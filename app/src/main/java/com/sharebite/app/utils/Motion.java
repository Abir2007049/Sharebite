package com.sharebite.app.utils;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.sharebite.app.R;

/**
 * Small motion helpers. Animations go through ValueAnimator / ViewPropertyAnimator, so they follow
 * the system "animation scale" setting (and are instant when animations are turned off).
 */
public final class Motion {

    private Motion() { }

    /** Counts a number up from what is shown now to {@code target}. */
    public static void countUp(@NonNull TextView view, int target) {
        Object running = view.getTag(R.id.motion_animator);
        if (running instanceof ValueAnimator) ((ValueAnimator) running).cancel();
        int from;
        try {
            from = Integer.parseInt(view.getText().toString());
        } catch (NumberFormatException e) {
            from = 0;
        }
        if (from == target || SystemStatusMonitor.isLowPower()) {
            view.setText(String.valueOf(target));
            return;
        }
        ValueAnimator a = ValueAnimator.ofInt(from, target);
        a.setDuration(Math.min(900, 300 + Math.abs(target - from) * 20L));
        a.setInterpolator(new DecelerateInterpolator());
        a.addUpdateListener(an -> view.setText(String.valueOf(an.getAnimatedValue())));
        view.setTag(R.id.motion_animator, a);
        a.start();
    }

    /** Scales a view up from nothing with a slight overshoot (success badges). */
    public static void popIn(@NonNull View view) {
        view.setScaleX(0f);
        view.setScaleY(0f);
        view.animate().scaleX(1f).scaleY(1f).setStartDelay(120).setDuration(420)
                .setInterpolator(new android.view.animation.OvershootInterpolator(2.2f)).start();
    }

    /** Fades and slides a view in from slightly below. */
    public static void enter(@NonNull View view, long delayMs) {
        view.setAlpha(0f);
        view.setTranslationY(view.getResources().getDisplayMetrics().density * 16);
        view.animate().alpha(1f).translationY(0f).setStartDelay(delayMs).setDuration(260)
                .setInterpolator(new DecelerateInterpolator()).start();
    }
}
