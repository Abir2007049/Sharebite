package com.sharebite.app.utils;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.textfield.TextInputLayout;
import com.sharebite.app.R;
import com.sharebite.app.data.model.DistributionType;
import com.sharebite.app.data.model.ListingStatus;
import com.sharebite.app.data.model.ReservationStatus;
import com.sharebite.app.data.model.VerificationStatus;

/** Small view helpers: status/distribution badges, form errors, keyboard. */
public final class ViewUtils {

    private ViewUtils() {
    }

    /** Colours a pill TextView (background bg_pill) for a distribution type. */
    public static void styleDistributionBadge(@NonNull TextView badge, @NonNull DistributionType type) {
        badge.setText(type.labelRes);
        switch (type) {
            case FREE: tint(badge, R.color.free_green_bg, R.color.free_green); break;
            case DISCOUNTED: tint(badge, R.color.discount_amber_bg, R.color.discount_amber); break;
            case CHARITY: default: tint(badge, R.color.charity_berry_bg, R.color.charity_berry); break;
        }
    }

    public static void styleListingStatus(@NonNull TextView badge, @NonNull ListingStatus status) {
        badge.setText(status.labelRes);
        switch (status) {
            case AVAILABLE: tint(badge, R.color.free_green_bg, R.color.free_green); break;
            case FULLY_RESERVED: tint(badge, R.color.info_bg, R.color.info_blue); break;
            case EXPIRED: tint(badge, R.color.warning_bg, R.color.warning_amber); break;
            case CANCELLED: tint(badge, R.color.error_bg, R.color.error_red); break;
            case COMPLETED: default: tint(badge, R.color.neutral_status_bg, R.color.charcoal_secondary); break;
        }
    }

    public static void styleReservationStatus(@NonNull TextView badge, @NonNull ReservationStatus status) {
        badge.setText(status.labelRes);
        switch (status) {
            case PENDING: tint(badge, R.color.warning_bg, R.color.warning_amber); break;
            case CONFIRMED: tint(badge, R.color.info_bg, R.color.info_blue); break;
            case COLLECTED: tint(badge, R.color.free_green_bg, R.color.free_green); break;
            case REJECTED:
            case CANCELLED:
            default: tint(badge, R.color.neutral_status_bg, R.color.charcoal_secondary); break;
        }
    }

    public static void styleVerification(@NonNull TextView badge, @NonNull VerificationStatus status) {
        badge.setText(status.labelRes);
        switch (status) {
            case VERIFIED: tint(badge, R.color.free_green_bg, R.color.free_green); break;
            case PENDING: tint(badge, R.color.info_bg, R.color.info_blue); break;
            case REJECTED: tint(badge, R.color.error_bg, R.color.error_red); break;
            default: tint(badge, R.color.warning_bg, R.color.warning_amber); break;
        }
    }

    private static void tint(TextView badge, @ColorRes int bg, @ColorRes int fg) {
        Context c = badge.getContext();
        badge.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(c, bg)));
        badge.setTextColor(ContextCompat.getColor(c, fg));
    }

    /** Sets or clears a TextInputLayout error; returns true when there was an error. */
    public static boolean setError(@NonNull TextInputLayout layout, @Nullable String error) {
        layout.setError(error);
        layout.setErrorEnabled(error != null);
        return error != null;
    }

    @NonNull
    public static String text(@NonNull TextInputLayout layout) {
        return layout.getEditText() == null || layout.getEditText().getText() == null
                ? "" : layout.getEditText().getText().toString();
    }

    public static void setText(@NonNull TextInputLayout layout, @Nullable CharSequence value) {
        if (layout.getEditText() != null) layout.getEditText().setText(value);
    }

    public static void hideKeyboard(@NonNull Activity activity) {
        View focus = activity.getCurrentFocus();
        if (focus == null) return;
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        focus.clearFocus();
    }

    public static void visible(@NonNull View view, boolean visible) {
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}
