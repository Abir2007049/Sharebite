package com.sharebite.app.domain.service;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.sharebite.app.data.model.Allergen;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Finds allergens mentioned in a provider's free-text allergen declaration, so a user's personal
 * allergen profile can be checked against it.
 *
 * <p>This only reads what the provider declared. It cannot detect undeclared allergens, which is
 * why the app always says "check with the provider" rather than "safe".
 */
public final class AllergenMatcher {

    private AllergenMatcher() { }

    /** Words that cancel the allergen word right after them ("no nuts", "without milk"). */
    private static final Set<String> NEGATIONS_BEFORE = new HashSet<>(Arrays.asList("no", "without", "non", "zero"));
    /** Words that cancel the allergen word right before them ("nut free", "gluten-free"). */
    private static final Set<String> NEGATIONS_AFTER = Collections.singleton("free");

    /** Every allergen the declaration mentions (ignoring negated mentions like "nut-free"). */
    @NonNull
    public static Set<Allergen> find(@Nullable String declaration) {
        if (declaration == null || declaration.trim().isEmpty()) return Collections.emptySet();
        String[] words = declaration.toLowerCase(Locale.ROOT).split("[^\\p{L}]+");
        EnumSet<Allergen> found = EnumSet.noneOf(Allergen.class);
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            if (w.isEmpty()) continue;
            boolean negated = (i > 0 && NEGATIONS_BEFORE.contains(words[i - 1]))
                    || (i + 1 < words.length && NEGATIONS_AFTER.contains(words[i + 1]));
            if (negated) continue;
            for (Allergen a : Allergen.values()) {
                for (String k : a.keywords()) {
                    if (w.startsWith(k)) {
                        found.add(a);
                        break;
                    }
                }
            }
        }
        return found;
    }

    /** Allergens in {@code declaration} that the user avoids; empty when there is no conflict. */
    @NonNull
    public static Set<Allergen> conflicts(@Nullable String declaration, @NonNull Set<Allergen> avoided) {
        if (avoided.isEmpty()) return Collections.emptySet();
        Set<Allergen> found = find(declaration);
        if (found.isEmpty()) return Collections.emptySet();
        EnumSet<Allergen> result = EnumSet.noneOf(Allergen.class);
        for (Allergen a : found) if (avoided.contains(a)) result.add(a);
        return result;
    }
}
