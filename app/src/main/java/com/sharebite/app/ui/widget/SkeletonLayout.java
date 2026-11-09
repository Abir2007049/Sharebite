package com.sharebite.app.ui.widget;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Placeholder shapes shown while content loads (instead of a spinner), so the screen keeps its
 * layout and feels faster. Pulses gently only while visible on screen; the system animation scale
 * applies, so with animations off it simply stays still.
 */
public class SkeletonLayout extends LinearLayout {

    private ObjectAnimator pulse;

    public SkeletonLayout(Context context) {
        super(context);
    }

    public SkeletonLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public SkeletonLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (isVisible) start();
        else stop();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    private void start() {
        if (pulse != null || com.sharebite.app.utils.SystemStatusMonitor.isLowPower()) return;
        pulse = ObjectAnimator.ofFloat(this, View.ALPHA, 1f, 0.45f);
        pulse.setDuration(750);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.start();
    }

    private void stop() {
        if (pulse == null) return;
        pulse.cancel();
        pulse = null;
        setAlpha(1f);
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (visibility != VISIBLE) stop();
    }

    @NonNull
    @Override
    public CharSequence getAccessibilityClassName() {
        return LinearLayout.class.getName();
    }
}
