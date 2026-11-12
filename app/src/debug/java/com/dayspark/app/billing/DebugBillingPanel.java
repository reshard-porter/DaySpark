package com.dayspark.app.billing;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.dayspark.app.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Debug-only tools reached from Settings. */
final class DebugBillingPanel {

    private DebugBillingPanel() {}

    static void show(Activity activity) {
        Context c = activity;
        boolean sim = DebugBillingSettings.useSimulator(c);
        String[] items = {
                c.getString(sim ? R.string.debug_source_sim : R.string.debug_source_play),
                c.getString(DebugBillingSettings.trustLocally(c)
                        ? R.string.debug_verify_local : R.string.debug_verify_server),
                c.getString(R.string.debug_scenario, DebugBillingSettings.scenario(c).name()),
                c.getString(R.string.debug_complete_pending),
                c.getString(R.string.debug_refund),
                c.getString(R.string.debug_reset),
        };
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.debug_title)
                .setItems(items, (d, which) -> {
                    PremiumManager pm = PremiumManager.get(activity);
                    SimulatedBillingGateway gw = pm.gateway() instanceof SimulatedBillingGateway
                            ? (SimulatedBillingGateway) pm.gateway() : null;
                    switch (which) {
                        case 0:
                            DebugBillingSettings.setUseSimulator(c, !sim);
                            restartApp(activity);
                            break;
                        case 1:
                            DebugBillingSettings.setTrustLocally(c, !DebugBillingSettings.trustLocally(c));
                            restartApp(activity);
                            break;
                        case 2:
                            pickScenario(activity);
                            break;
                        case 3:
                            if (gw != null) gw.completePendingPayment();
                            break;
                        case 4:
                            if (gw != null) {
                                gw.refund();
                                pm.refresh();
                            }
                            break;
                        case 5:
                            if (gw != null) {
                                gw.reset();
                                pm.refresh();
                            }
                            break;
                        default:
                            break;
                    }
                })
                .setNegativeButton(R.string.action_close, null)
                .show();
    }

    private static void pickScenario(Activity activity) {
        DebugBillingSettings.Scenario[] values = DebugBillingSettings.Scenario.values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) names[i] = values[i].name();
        int checked = DebugBillingSettings.scenario(activity).ordinal();
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.debug_pick_scenario)
                .setSingleChoiceItems(names, checked, (d, which) -> {
                    DebugBillingSettings.setScenario(activity, values[which]);
                    d.dismiss();
                })
                .show();
    }

    /** Billing source is fixed at process start, so switching restarts the app. */
    private static void restartApp(Activity activity) {
        Intent intent = activity.getPackageManager().getLaunchIntentForPackage(activity.getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity.startActivity(intent);
        }
        Runtime.getRuntime().exit(0);
    }
}
