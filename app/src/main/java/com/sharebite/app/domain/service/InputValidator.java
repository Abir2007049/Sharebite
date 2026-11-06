package com.sharebite.app.domain.service;

import androidx.annotation.Nullable;

import java.util.regex.Pattern;

/**
 * Field validation shared by the auth and profile screens. Pure Java (no android.util.Patterns)
 * so it can be unit tested on the JVM. Each method returns an error message or {@code null}.
 */
public final class InputValidator {

    public static final int MIN_PASSWORD_LENGTH = 8;

    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9._%+'-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)*\\.[A-Za-z]{2,}$");
    /** Optional leading +, then 7–15 digits; spaces, dashes and brackets are allowed as separators. */
    private static final Pattern PHONE_CHARS = Pattern.compile("^\\+?[0-9 ()\\-]+$");

    private InputValidator() {
    }

    @Nullable
    public static String validateFullName(@Nullable String name) {
        String n = trim(name);
        if (n.isEmpty()) return "Enter your full name.";
        if (n.length() < 2) return "Name is too short.";
        if (n.length() > 60) return "Name must be 60 characters or fewer.";
        return null;
    }

    @Nullable
    public static String validateEmail(@Nullable String email) {
        String e = trim(email);
        if (e.isEmpty()) return "Enter your email address.";
        if (e.length() > 254 || !EMAIL.matcher(e).matches()) return "Enter a valid email address.";
        return null;
    }

    @Nullable
    public static String validatePassword(@Nullable String password) {
        if (password == null || password.isEmpty()) return "Enter a password.";
        if (password.length() < MIN_PASSWORD_LENGTH) return "Use at least " + MIN_PASSWORD_LENGTH + " characters.";
        boolean letter = false, digit = false;
        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) letter = true;
            if (Character.isDigit(c)) digit = true;
        }
        if (!letter || !digit) return "Include at least one letter and one number.";
        return null;
    }

    /** Login only checks presence: the server decides whether the password is correct. */
    @Nullable
    public static String validateLoginPassword(@Nullable String password) {
        return password == null || password.isEmpty() ? "Enter your password." : null;
    }

    @Nullable
    public static String validateConfirmation(@Nullable String password, @Nullable String confirmation) {
        if (confirmation == null || confirmation.isEmpty()) return "Re-enter your password.";
        return confirmation.equals(password) ? null : "Passwords do not match.";
    }

    @Nullable
    public static String validatePhone(@Nullable String phone) {
        String p = trim(phone);
        if (p.isEmpty()) return "Enter a phone number so you can be contacted about pickups.";
        if (!PHONE_CHARS.matcher(p).matches()) return "Use digits only (an optional leading + is allowed).";
        int digits = p.replaceAll("[^0-9]", "").length();
        if (digits < 7 || digits > 15) return "Enter a valid phone number (7–15 digits).";
        return null;
    }

    @Nullable
    public static String validateOrganizationName(@Nullable String name, boolean required) {
        String n = trim(name);
        if (n.isEmpty()) return required ? "Enter the organisation name." : null;
        if (n.length() < 2) return "Organisation name is too short.";
        if (n.length() > 80) return "Organisation name must be 80 characters or fewer.";
        return null;
    }

    @Nullable
    public static String validateRequired(@Nullable String value, String fieldLabel, int maxLength) {
        String v = trim(value);
        if (v.isEmpty()) return fieldLabel + " is required.";
        if (v.length() > maxLength) return fieldLabel + " must be " + maxLength + " characters or fewer.";
        return null;
    }

    static String trim(@Nullable String s) {
        return s == null ? "" : s.trim();
    }
}
