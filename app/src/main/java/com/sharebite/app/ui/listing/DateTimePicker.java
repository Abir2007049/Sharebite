package com.sharebite.app.ui.listing;

import android.text.format.DateFormat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * Two-step date then time picker built on Material pickers. Pickers are DialogFragments that
 * survive rotation, but their listeners do not, so {@link #reattach} reconnects them by tag.
 */
public final class DateTimePicker {

    public interface Listener {
        void onPicked(@NonNull String key, @NonNull Date value);
    }

    public enum Direction { PAST, FUTURE }

    private static final String DATE_TAG = "dtp_date_";
    private static final String TIME_TAG = "dtp_time_";

    private final FragmentActivity activity;
    private final Listener listener;
    /** Selected date (UTC midnight millis) carried from the date step to the time step. */
    private long pendingDateUtc;

    public DateTimePicker(@NonNull FragmentActivity activity, @NonNull Listener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    public void pick(@NonNull String key, @Nullable Date initial, @NonNull Direction direction) {
        Calendar local = Calendar.getInstance();
        if (initial != null) local.setTime(initial);
        // MaterialDatePicker works in UTC: convert the local calendar date to UTC midnight.
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH));

        long todayUtc = MaterialDatePicker.todayInUtcMilliseconds();
        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setValidator(direction == Direction.PAST
                        ? DateValidatorPointBackward.before(todayUtc + 1)
                        : DateValidatorPointForward.from(todayUtc))
                .build();
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setSelection(utc.getTimeInMillis())
                .setCalendarConstraints(constraints)
                .build();
        attachDate(key, datePicker, initial);
        datePicker.show(activity.getSupportFragmentManager(), DATE_TAG + key);
    }

    /** Call from onCreate after rotation for every key that may have an open picker. */
    @SuppressWarnings("unchecked")
    public void reattach(@NonNull String... keys) {
        FragmentManager fm = activity.getSupportFragmentManager();
        for (String key : keys) {
            Object date = fm.findFragmentByTag(DATE_TAG + key);
            if (date instanceof MaterialDatePicker) attachDate(key, (MaterialDatePicker<Long>) date, null);
            Object time = fm.findFragmentByTag(TIME_TAG + key);
            if (time instanceof MaterialTimePicker) attachTime(key, (MaterialTimePicker) time);
        }
    }

    public void restorePendingDate(long utcMillis) {
        pendingDateUtc = utcMillis;
    }

    public long pendingDate() {
        return pendingDateUtc;
    }

    private void attachDate(String key, MaterialDatePicker<Long> picker, @Nullable Date initial) {
        picker.addOnPositiveButtonClickListener(selection -> {
            pendingDateUtc = selection;
            Calendar local = Calendar.getInstance();
            if (initial != null) local.setTime(initial);
            MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                    .setTimeFormat(DateFormat.is24HourFormat(activity) ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                    .setHour(local.get(Calendar.HOUR_OF_DAY))
                    .setMinute(local.get(Calendar.MINUTE))
                    .build();
            attachTime(key, timePicker);
            timePicker.show(activity.getSupportFragmentManager(), TIME_TAG + key);
        });
    }

    private void attachTime(String key, MaterialTimePicker picker) {
        picker.addOnPositiveButtonClickListener(v -> {
            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            utc.setTimeInMillis(pendingDateUtc);
            Calendar local = Calendar.getInstance();
            local.clear();
            local.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH),
                    picker.getHour(), picker.getMinute());
            listener.onPicked(key, local.getTime());
        });
    }
}
