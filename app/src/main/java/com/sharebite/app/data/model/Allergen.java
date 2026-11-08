package com.sharebite.app.data.model;

import androidx.annotation.StringRes;

import com.sharebite.app.R;

/**
 * Common food allergens (based on the 14 major allergens used in EU/UK food labelling; crustaceans
 * and molluscs are grouped as shellfish). Each has the words a provider might write for it in a
 * listing's free-text allergen declaration.
 */
public enum Allergen {
    GLUTEN(R.string.allergen_gluten, R.string.allergen_short_gluten, "gluten", "wheat", "barley", "rye", "spelt", "semolina", "flour"),
    MILK(R.string.allergen_milk, R.string.allergen_short_milk, "milk", "dairy", "lactose", "cheese", "butter", "ghee", "cream", "yoghurt",
            "yogurt", "paneer", "curd", "whey"),
    EGGS(R.string.allergen_eggs, R.string.allergen_short_eggs, "egg"),
    PEANUTS(R.string.allergen_peanuts, R.string.allergen_short_peanuts, "peanut", "groundnut"),
    TREE_NUTS(R.string.allergen_tree_nuts, R.string.allergen_short_tree_nuts, "nut", "almond", "cashew", "walnut", "hazelnut", "pistachio", "pecan"),
    SOY(R.string.allergen_soy, R.string.allergen_short_soy, "soy", "soya", "tofu"),
    FISH(R.string.allergen_fish, R.string.allergen_short_fish, "fish", "anchov", "tuna", "salmon", "cod", "sardine"),
    SHELLFISH(R.string.allergen_shellfish, R.string.allergen_short_shellfish, "shellfish", "shrimp", "prawn", "crab", "lobster", "crustacean",
            "mollusc", "mollusk", "squid", "oyster", "mussel", "clam"),
    SESAME(R.string.allergen_sesame, R.string.allergen_short_sesame, "sesame", "tahini"),
    MUSTARD(R.string.allergen_mustard, R.string.allergen_short_mustard, "mustard"),
    CELERY(R.string.allergen_celery, R.string.allergen_short_celery, "celery", "celeriac"),
    SULPHITES(R.string.allergen_sulphites, R.string.allergen_short_sulphites, "sulphite", "sulfite", "sulphur", "sulfur"),
    LUPIN(R.string.allergen_lupin, R.string.allergen_short_lupin, "lupin");

    @StringRes public final int labelRes;
    /** Lower-case name for use inside sentences ("contains milk"). */
    @StringRes public final int shortRes;
    /** Lower-case word prefixes: "egg" matches "eggs", "nut" matches "nuts" but not "peanut". */
    final String[] keywords;

    Allergen(@StringRes int labelRes, @StringRes int shortRes, String... keywords) {
        this.labelRes = labelRes;
        this.shortRes = shortRes;
        this.keywords = keywords;
    }

    public String[] keywords() {
        return keywords.clone();
    }
}
