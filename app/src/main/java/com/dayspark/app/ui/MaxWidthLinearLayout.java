package com.dayspark.app.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import com.dayspark.app.R;

/** Vertical column that stops growing at {@code content_max_width}, keeping tablets readable. */
public class MaxWidthLinearLayout extends LinearLayout {

    private final int maxWidth;

    public MaxWidthLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        maxWidth = getResources().getDimensionPixelSize(R.dimen.content_max_width);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width > maxWidth) {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.EXACTLY);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
