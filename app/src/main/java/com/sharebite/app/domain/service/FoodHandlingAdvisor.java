package com.sharebite.app.domain.service;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.sharebite.app.data.model.FoodCategory;
import com.sharebite.app.data.remote.dto.WeatherInfo;

import java.util.Locale;

/**
 * Turns current weather at the pickup point into practical handling guidance. It never states
 * that food is safe; it only reminds collectors of general good practice (e.g. the widely used
 * guidance that perishable food should not stay in the 5–60 °C "danger zone" for long).
 */
public final class FoodHandlingAdvisor {

    static final double HOT_THRESHOLD_C = 30;
    static final double WARM_THRESHOLD_C = 25;

    private FoodHandlingAdvisor() {
    }

    @Nullable
    public static String advise(@Nullable FoodCategory category, @NonNull WeatherInfo weather) {
        double t = weather.getTemperatureCelsius();
        boolean sensitive = category != null && category.temperatureSensitive;
        String temp = String.format(Locale.getDefault(), "%.0f°C", t);
        if (sensitive && t >= HOT_THRESHOLD_C) {
            return "It's " + temp + " at the pickup point. Bring an insulated bag or cool box and refrigerate this food as soon as you get home.";
        }
        if (sensitive && t >= WARM_THRESHOLD_C) {
            return "It's " + temp + " at the pickup point. Perishable food should go straight home — avoid leaving it in a warm vehicle.";
        }
        if (t >= HOT_THRESHOLD_C) {
            return "It's " + temp + " at the pickup point. Keep the food out of direct sunlight on the way home.";
        }
        if (weather.isWet()) {
            return weather.describe() + " expected near the pickup point. Bring a bag to keep the food dry.";
        }
        return null;
    }
}
