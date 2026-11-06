package com.sharebite.app.data.model;

import androidx.annotation.PluralsRes;
import androidx.annotation.StringRes;

import com.sharebite.app.R;

/** Unit the available quantity is counted in. Quantities are whole numbers of this unit. */
public enum QuantityUnit {
    PORTIONS(R.string.unit_portions, R.plurals.unit_portions),
    PIECES(R.string.unit_pieces, R.plurals.unit_pieces),
    BOXES(R.string.unit_boxes, R.plurals.unit_boxes),
    PACKS(R.string.unit_packs, R.plurals.unit_packs),
    KILOGRAMS(R.string.unit_kg, R.plurals.unit_kg),
    LITRES(R.string.unit_litres, R.plurals.unit_litres);

    @StringRes public final int labelRes;
    @PluralsRes public final int pluralRes;

    QuantityUnit(@StringRes int labelRes, @PluralsRes int pluralRes) {
        this.labelRes = labelRes;
        this.pluralRes = pluralRes;
    }
}
