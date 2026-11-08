package com.sharebite.app.ui.profile;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.sharebite.app.R;
import com.sharebite.app.data.model.Allergen;
import com.sharebite.app.utils.AllergenPreferences;
import com.sharebite.app.utils.ServiceLocator;

import java.util.EnumSet;
import java.util.Set;

/** Multi-select dialog for the allergens the user avoids (stored on the device only). */
public final class AllergenProfileDialog {

    private AllergenProfileDialog() { }

    public static void show(@NonNull Context context, @Nullable Runnable onSaved) {
        AllergenPreferences prefs = ServiceLocator.get().allergens();
        Allergen[] all = Allergen.values();
        String[] labels = new String[all.length];
        boolean[] checked = new boolean[all.length];
        Set<Allergen> current = prefs.avoided();
        for (int i = 0; i < all.length; i++) {
            labels[i] = context.getString(all[i].labelRes);
            checked[i] = current.contains(all[i]);
        }
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.my_allergens_title)
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (d, w) -> {
                    EnumSet<Allergen> selection = EnumSet.noneOf(Allergen.class);
                    for (int i = 0; i < all.length; i++) if (checked[i]) selection.add(all[i]);
                    prefs.setAvoided(selection);
                    if (onSaved != null) onSaved.run();
                })
                .show();
    }
}
