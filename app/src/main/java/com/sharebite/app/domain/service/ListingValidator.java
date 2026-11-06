package com.sharebite.app.domain.service;

import androidx.annotation.NonNull;

import com.sharebite.app.data.model.DistributionType;

import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Validates listing input, including the food-safety rules from the product brief:
 * preparation time and pickup deadline are mandatory, storage and allergen information are
 * required, and the provider must declare the food fit for human consumption.
 */
public final class ListingValidator {

    public enum Field {
        TITLE, DESCRIPTION, CATEGORY, QUANTITY, UNIT, DISTRIBUTION, ORIGINAL_PRICE, DISCOUNTED_PRICE,
        PREPARATION_TIME, PICKUP_START, PICKUP_DEADLINE, ADDRESS, STORAGE, ALLERGENS, SAFETY_DECLARATION
    }

    public static final int MAX_QUANTITY = 1000;
    public static final double MAX_PRICE = 1_000_000d;
    /** Listings are for surplus food; a pickup window longer than this is almost certainly a mistake. */
    public static final long MAX_LISTING_WINDOW_MS = TimeUnit.DAYS.toMillis(7);
    /** Small allowance for clock drift between "now" on the form and the device clock. */
    private static final long CLOCK_TOLERANCE_MS = TimeUnit.MINUTES.toMillis(5);

    /** Immutable map of field → user-facing error. Empty when the draft is valid. */
    public static final class Result {
        private final Map<Field, String> errors;

        Result(Map<Field, String> errors) {
            this.errors = Collections.unmodifiableMap(errors);
        }

        public boolean isValid() { return errors.isEmpty(); }
        public Map<Field, String> errors() { return errors; }
        public String errorFor(Field f) { return errors.get(f); }
    }

    private ListingValidator() {
    }

    @NonNull
    public static Result validate(@NonNull ListingDraft d, @NonNull Date now) {
        Map<Field, String> e = new EnumMap<>(Field.class);

        String title = InputValidator.trim(d.title);
        if (title.length() < 3) e.put(Field.TITLE, "Give the food a short descriptive title (at least 3 characters).");
        else if (title.length() > 80) e.put(Field.TITLE, "Title must be 80 characters or fewer.");

        String desc = InputValidator.trim(d.description);
        if (desc.length() < 10) e.put(Field.DESCRIPTION, "Describe the food in at least 10 characters.");
        else if (desc.length() > 1000) e.put(Field.DESCRIPTION, "Description must be 1000 characters or fewer.");

        if (d.category == null) e.put(Field.CATEGORY, "Choose a category.");
        if (d.unit == null) e.put(Field.UNIT, "Choose a unit.");
        if (d.distributionType == null) e.put(Field.DISTRIBUTION, "Choose how the food will be shared.");

        Integer quantity = parseInt(d.quantityText);
        if (quantity == null || quantity < 1) {
            e.put(Field.QUANTITY, "Enter a whole number of at least 1.");
        } else if (quantity > MAX_QUANTITY) {
            e.put(Field.QUANTITY, "Quantity cannot exceed " + MAX_QUANTITY + ".");
        } else if (quantity < d.reservedQuantity) {
            e.put(Field.QUANTITY, d.reservedQuantity + " units are already reserved — quantity cannot be lower.");
        }

        if (d.distributionType == DistributionType.DISCOUNTED) {
            Double original = parseDouble(d.originalPriceText);
            Double discounted = parseDouble(d.discountedPriceText);
            if (original == null || original <= 0 || original > MAX_PRICE) {
                e.put(Field.ORIGINAL_PRICE, "Enter the original price.");
            }
            if (discounted == null || discounted <= 0) {
                e.put(Field.DISCOUNTED_PRICE, "Enter the discounted price.");
            } else if (original != null && discounted >= original) {
                e.put(Field.DISCOUNTED_PRICE, "Discounted price must be lower than the original price.");
            }
        }

        // Time rules.
        if (d.preparationTime == null) {
            e.put(Field.PREPARATION_TIME, "Preparation date and time are required for food safety.");
        } else if (d.preparationTime.getTime() > now.getTime() + CLOCK_TOLERANCE_MS) {
            e.put(Field.PREPARATION_TIME, "Preparation time cannot be in the future.");
        }
        if (d.pickupStartTime == null) e.put(Field.PICKUP_START, "Choose when pickup can start.");
        if (d.pickupDeadline == null) {
            e.put(Field.PICKUP_DEADLINE, "A pickup deadline is required.");
        } else {
            if (!d.pickupDeadline.after(now)) {
                e.put(Field.PICKUP_DEADLINE, "The pickup deadline must be in the future.");
            } else if (d.pickupStartTime != null && !d.pickupDeadline.after(d.pickupStartTime)) {
                e.put(Field.PICKUP_DEADLINE, "The deadline must be after the pickup start time.");
            } else if (d.pickupDeadline.getTime() - now.getTime() > MAX_LISTING_WINDOW_MS) {
                e.put(Field.PICKUP_DEADLINE, "Surplus food listings can run for at most 7 days.");
            }
            if (d.preparationTime != null && !d.pickupDeadline.after(d.preparationTime)) {
                e.put(Field.PICKUP_DEADLINE, "The deadline must be after the preparation time.");
            }
        }

        String address = InputValidator.trim(d.pickupAddress);
        if (address.length() < 5) e.put(Field.ADDRESS, "Enter a pickup address receivers can find.");
        else if (address.length() > 200) e.put(Field.ADDRESS, "Address must be 200 characters or fewer.");

        String storage = InputValidator.trim(d.storageInformation);
        if (storage.length() < 3) e.put(Field.STORAGE, "Describe how the food has been stored (e.g. refrigerated at 4°C).");
        else if (storage.length() > 300) e.put(Field.STORAGE, "Storage information must be 300 characters or fewer.");

        String allergens = InputValidator.trim(d.allergens);
        if (allergens.isEmpty()) e.put(Field.ALLERGENS, "List allergens, or enter \"None\" if there are none.");
        else if (allergens.length() > 300) e.put(Field.ALLERGENS, "Allergen information must be 300 characters or fewer.");

        if (!d.safetyDeclared) {
            e.put(Field.SAFETY_DECLARATION, "You must confirm the food is safe and fit for human consumption.");
        }
        return new Result(e);
    }

    static Integer parseInt(String s) {
        try {
            return Integer.parseInt(InputValidator.trim(s));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static Double parseDouble(String s) {
        try {
            double v = Double.parseDouble(InputValidator.trim(s).replace(',', '.'));
            return Double.isNaN(v) || Double.isInfinite(v) ? null : v;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Rounds money to 2 decimal places (rules compare totals exactly, so store rounded values). */
    public static double roundMoney(double value) {
        return Math.round(value * 100d) / 100d;
    }
}
