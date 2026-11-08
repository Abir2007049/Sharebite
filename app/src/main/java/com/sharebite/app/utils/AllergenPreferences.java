package com.sharebite.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.sharebite.app.data.model.Allergen;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * The allergens this person avoids. Kept on the device only: allergy information is health data and
 * user profiles in Firestore are readable by other signed-in users, so it is deliberately not synced.
 */
public final class AllergenPreferences {

    private static final String FILE = "allergen_profile";
    private static final String KEY = "avoided";

    private final SharedPreferences prefs;
    private final MutableLiveData<Set<Allergen>> avoided = new MutableLiveData<>();

    AllergenPreferences(@NonNull Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
        avoided.setValue(read());
    }

    /** Current selection; never null. */
    @NonNull
    public Set<Allergen> avoided() {
        Set<Allergen> v = avoided.getValue();
        return v == null ? Collections.emptySet() : v;
    }

    /** Emits whenever the selection changes, so lists can re-check their cards. */
    public LiveData<Set<Allergen>> changes() {
        return avoided;
    }

    public void setAvoided(@NonNull Set<Allergen> selection) {
        Set<String> names = new HashSet<>();
        for (Allergen a : selection) names.add(a.name());
        prefs.edit().putStringSet(KEY, names).apply();
        avoided.setValue(Collections.unmodifiableSet(selection.isEmpty()
                ? EnumSet.noneOf(Allergen.class) : EnumSet.copyOf(selection)));
    }

    private Set<Allergen> read() {
        EnumSet<Allergen> result = EnumSet.noneOf(Allergen.class);
        for (String name : prefs.getStringSet(KEY, Collections.emptySet())) {
            try {
                result.add(Allergen.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                // An allergen removed in a later version: drop it.
            }
        }
        return Collections.unmodifiableSet(result);
    }
}
