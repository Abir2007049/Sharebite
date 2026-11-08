package com.sharebite.app.utils;

import android.content.Context;

import androidx.annotation.NonNull;

import com.sharebite.app.data.model.Allergen;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Human-readable allergen lists ("milk and eggs", "gluten, milk and eggs"). */
public final class AllergenText {

    private AllergenText() { }

    @NonNull
    public static String join(@NonNull Context c, @NonNull Set<Allergen> allergens) {
        List<String> names = new ArrayList<>();
        for (Allergen a : allergens) names.add(c.getString(a.shortRes));
        if (names.isEmpty()) return "";
        if (names.size() == 1) return names.get(0);
        String last = names.remove(names.size() - 1);
        return c.getString(com.sharebite.app.R.string.list_and_format, String.join(", ", names), last);
    }
}
