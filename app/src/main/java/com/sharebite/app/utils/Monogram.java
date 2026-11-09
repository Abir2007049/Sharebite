package com.sharebite.app.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Round initials badge used as a provider's logo until they upload one. The colour is picked from a
 * small palette by the name, so each provider keeps a stable, distinct colour.
 */
public final class Monogram extends Drawable {

    private static final int[] COLORS = {
            0xFF0B6B4F, 0xFF175CD3, 0xFFB54708, 0xFF6941C6, 0xFFC11574, 0xFF0E7090, 0xFF4E5BA6, 0xFF9E5A00,
    };

    private final Paint circle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String initials;

    public Monogram(@NonNull Context context, @Nullable String name) {
        initials = initials(name);
        circle.setColor(colorFor(name));
        text.setColor(0xFFFFFFFF);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        text.setTextAlign(Paint.Align.CENTER);
    }

    public static int colorFor(@Nullable String name) {
        int h = name == null ? 0 : name.trim().toLowerCase(Locale.ROOT).hashCode();
        return COLORS[Math.abs(h % COLORS.length)];
    }

    /** Up to two initials from the first two words ("Golden Crust Bakery" -> "GC"). */
    @NonNull
    public static String initials(@Nullable String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        String[] words = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (sb.length() == 2) break;
            int cp = w.codePointAt(0);
            if (Character.isLetterOrDigit(cp)) sb.appendCodePoint(Character.toUpperCase(cp));
        }
        return sb.length() == 0 ? "?" : sb.toString();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        float r = Math.min(b.width(), b.height()) / 2f;
        canvas.drawCircle(b.exactCenterX(), b.exactCenterY(), r, circle);
        text.setTextSize(r * (initials.length() > 1 ? 0.78f : 0.95f));
        float y = b.exactCenterY() - (text.descent() + text.ascent()) / 2f;
        canvas.drawText(initials, b.exactCenterX(), y, text);
    }

    @Override public void setAlpha(int alpha) { circle.setAlpha(alpha); text.setAlpha(alpha); }
    @Override public void setColorFilter(@Nullable ColorFilter cf) { circle.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
