package com.dayspark.app.billing;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Production verifier: asks the DaySpark verification service (see /server) to check the
 * token with the Google Play Developer API. Service-account credentials stay on the server.
 */
public final class BackendPurchaseVerifier implements PurchaseVerifier {

    private static final int TIMEOUT_MS = 10_000;

    private final String baseUrl;
    private final String installId;

    public BackendPurchaseVerifier(String baseUrl, String installId) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.installId = installId;
    }

    @Override
    public VerificationResult verify(PurchaseRecord purchase) {
        if (baseUrl.isEmpty()) {
            return VerificationResult.unavailable("Verification service URL not configured");
        }
        HttpURLConnection conn = null;
        try {
            JSONObject body = new JSONObject()
                    .put("packageName", purchase.packageName)
                    .put("productId", purchase.productIds.isEmpty() ? "" : purchase.productIds.get(0))
                    .put("purchaseToken", purchase.purchaseToken)
                    .put("orderId", purchase.orderId)
                    .put("installId", installId);
            conn = (HttpURLConnection) new URL(baseUrl + "/v1/purchases/verify").openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream out = conn.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int http = conn.getResponseCode();
            if (http >= 500) return VerificationResult.unavailable("HTTP " + http);
            InputStream in = http >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String text = in == null ? "" : readAll(in);
            return parse(http, text);
        } catch (IOException | JSONException e) {
            return VerificationResult.unavailable(e.getClass().getSimpleName());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    static VerificationResult parse(int http, String text) throws JSONException {
        if (text.isEmpty()) {
            return http >= 400
                    ? new VerificationResult(VerificationResult.Status.INVALID, false, "HTTP " + http)
                    : VerificationResult.unavailable("Empty response");
        }
        JSONObject json = new JSONObject(text);
        String status = json.optString("status", "");
        boolean acked = json.optBoolean("acknowledged", false);
        switch (status) {
            case "verified":
                return new VerificationResult(VerificationResult.Status.VERIFIED, acked, "");
            case "pending":
                return new VerificationResult(VerificationResult.Status.PENDING, false, "");
            case "revoked":
                return new VerificationResult(VerificationResult.Status.REVOKED, false, json.optString("reason"));
            case "invalid":
                return new VerificationResult(VerificationResult.Status.INVALID, false, json.optString("reason"));
            default:
                return VerificationResult.unavailable("Unexpected status: " + status);
        }
    }

    private static String readAll(InputStream in) throws IOException {
        try (InputStream s = in; ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[4096];
            int n;
            while ((n = s.read(chunk)) != -1) buf.write(chunk, 0, n);
            return buf.toString(StandardCharsets.UTF_8.name());
        }
    }
}
