package com.sharebite.app.domain.service;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The secret behind the pickup QR code.
 *
 * <p>When a reservation is made, the receiver's app creates a random 128-bit code and stores it in a
 * sub-document only the receiver can read. At pickup the receiver shows it as a QR code; the provider
 * scans it, and the Firestore rules accept a "collected (verified)" update only if the scanned code
 * equals the stored one. So the provider cannot claim a verified handover without the receiver present.
 */
public final class PickupCode {

    private static final String PREFIX = "sharebite:pickup:v1:";
    private static final Pattern PAYLOAD = Pattern.compile("^sharebite:pickup:v1:([A-Za-z0-9_-]{1,64}):([0-9a-f]{32})$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private PickupCode() { }

    /** A new random code: 16 bytes as 32 lower-case hex characters. */
    @NonNull
    public static String generate() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(32);
        for (byte b : bytes) sb.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return sb.toString();
    }

    /** Text encoded in the QR code. */
    @NonNull
    public static String payload(@NonNull String reservationId, @NonNull String code) {
        return PREFIX + reservationId + ":" + code;
    }

    /** A scanned QR code that looks like a ShareBite pickup code. */
    public static final class Scanned {
        public final String reservationId;
        public final String code;

        Scanned(String reservationId, String code) {
            this.reservationId = reservationId;
            this.code = code;
        }
    }

    /** Parses scanned text, or returns null if it is not a ShareBite pickup code. */
    @Nullable
    public static Scanned parse(@Nullable String text) {
        if (text == null) return null;
        Matcher m = PAYLOAD.matcher(text.trim());
        return m.matches() ? new Scanned(m.group(1), m.group(2)) : null;
    }
}
