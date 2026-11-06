package com.sharebite.app.utils;

import android.content.Context;
import android.text.format.DateFormat;
import android.text.format.DateUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.sharebite.app.R;
import com.sharebite.app.data.model.QuantityUnit;

import java.text.NumberFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Display formatting for money, quantities, distances and times. */
public final class Formatters {

    private Formatters() {
    }

    @NonNull
    public static String money(double amount) {
        NumberFormat f = NumberFormat.getCurrencyInstance(Locale.getDefault());
        return f.format(amount);
    }

    @NonNull
    public static String quantity(@NonNull Context c, int amount, @Nullable QuantityUnit unit) {
        QuantityUnit u = unit == null ? QuantityUnit.PORTIONS : unit;
        return c.getResources().getQuantityString(u.pluralRes, amount, amount);
    }

    @NonNull
    public static String distance(@NonNull Context c, double km) {
        if (km < 1) return c.getString(R.string.distance_m, Math.max(10, Math.round(km * 1000 / 10) * 10));
        if (km < 10) return c.getString(R.string.distance_km_decimal, km);
        return c.getString(R.string.distance_km, Math.round(km));
    }

    /** "Today, 8:30 PM" / "Tomorrow, 9:00 AM" / "Mon 12 Nov, 6:00 PM". */
    @NonNull
    public static String dateTime(@NonNull Context c, @Nullable Timestamp ts) {
        return ts == null ? "—" : dateTime(c, ts.toDate());
    }

    @NonNull
    public static String dateTime(@NonNull Context c, @NonNull Date date) {
        String time = DateFormat.getTimeFormat(c).format(date);
        Calendar target = Calendar.getInstance();
        target.setTime(date);
        Calendar today = Calendar.getInstance();
        if (sameDay(target, today)) return c.getString(R.string.today_at, time);
        today.add(Calendar.DAY_OF_YEAR, 1);
        if (sameDay(target, today)) return c.getString(R.string.tomorrow_at, time);
        today.add(Calendar.DAY_OF_YEAR, -2);
        if (sameDay(target, today)) return c.getString(R.string.yesterday_at, time);
        String day = DateUtils.formatDateTime(c, date.getTime(),
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_ALL);
        return day + ", " + time;
    }

    /** "Ends in 2h 15m", "Ends in 25 min", "Ended". */
    @NonNull
    public static String timeLeft(@NonNull Context c, @Nullable Timestamp deadline, @NonNull Date now) {
        if (deadline == null) return "";
        long ms = deadline.toDate().getTime() - now.getTime();
        if (ms <= 0) return c.getString(R.string.time_left_ended);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(ms);
        if (minutes < 60) return c.getString(R.string.time_left_minutes, Math.max(1, minutes));
        long hours = minutes / 60;
        if (hours < 24) return c.getString(R.string.time_left_hours, hours, minutes % 60);
        return c.getString(R.string.time_left_days, hours / 24);
    }

    /** True when fewer than {@code minutes} remain before the deadline. */
    public static boolean endingWithin(@Nullable Timestamp deadline, @NonNull Date now, long minutes) {
        if (deadline == null) return false;
        long ms = deadline.toDate().getTime() - now.getTime();
        return ms > 0 && ms < TimeUnit.MINUTES.toMillis(minutes);
    }

    @NonNull
    public static String relative(@Nullable Timestamp ts) {
        if (ts == null) return "Just now";
        return DateUtils.getRelativeTimeSpanString(ts.toDate().getTime(), System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString();
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }
}
