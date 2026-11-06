package com.sharebite.app.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.sharebite.app.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal vertical bar chart (one series) for the monthly activity card. Draws value labels
 * above bars and category labels below; the latest bar is highlighted.
 */
public class BarChartView extends View {

    public static final class Bar {
        final String label;
        final int value;

        public Bar(@NonNull String label, int value) {
            this.label = label;
            this.value = value;
        }
    }

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final List<Bar> bars = new ArrayList<>();
    private final float radius, labelGap, textSize;

    public BarChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        barPaint.setColor(ContextCompat.getColor(context, R.color.soft_green));
        highlightPaint.setColor(ContextCompat.getColor(context, R.color.leaf_green));
        trackPaint.setColor(ContextCompat.getColor(context, R.color.cream_dark));
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12, getResources().getDisplayMetrics());
        labelPaint.setColor(ContextCompat.getColor(context, R.color.charcoal_secondary));
        labelPaint.setTextSize(textSize);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setColor(ContextCompat.getColor(context, R.color.charcoal));
        valuePaint.setTextSize(textSize);
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setFakeBoldText(true);
        radius = dp(6);
        labelGap = dp(8);
    }

    public void setBars(@NonNull List<Bar> data) {
        bars.clear();
        bars.addAll(data);
        StringBuilder description = new StringBuilder();
        for (Bar b : data) description.append(b.label).append(": ").append(b.value).append(". ");
        setContentDescription(description.toString());
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (bars.isEmpty()) return;
        int max = 1;
        for (Bar b : bars) max = Math.max(max, b.value);

        float left = getPaddingLeft(), right = getWidth() - getPaddingRight();
        float top = getPaddingTop() + textSize + labelGap;
        float bottom = getHeight() - getPaddingBottom() - textSize - labelGap;
        float slot = (right - left) / bars.size();
        float barWidth = Math.min(dp(28), slot * 0.55f);

        for (int i = 0; i < bars.size(); i++) {
            Bar b = bars.get(i);
            float cx = left + slot * i + slot / 2f;
            rect.set(cx - barWidth / 2f, top, cx + barWidth / 2f, bottom);
            canvas.drawRoundRect(rect, radius, radius, trackPaint);

            float h = (bottom - top) * b.value / max;
            if (b.value > 0) {
                rect.set(cx - barWidth / 2f, bottom - Math.max(h, radius * 2), cx + barWidth / 2f, bottom);
                canvas.drawRoundRect(rect, radius, radius, i == bars.size() - 1 ? highlightPaint : barPaint);
            }
            canvas.drawText(String.valueOf(b.value), cx, bottom - h - labelGap / 2f, valuePaint);
            canvas.drawText(b.label, cx, getHeight() - getPaddingBottom() - labelGap / 4f, labelPaint);
        }
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }
}
