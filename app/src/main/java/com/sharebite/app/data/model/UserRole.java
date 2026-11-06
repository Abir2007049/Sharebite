package com.sharebite.app.data.model;

import androidx.annotation.StringRes;

import com.sharebite.app.R;

/** Account type chosen during registration. Stored in Firestore as the enum name. */
public enum UserRole {
    PROVIDER(R.string.role_provider, R.string.role_provider_desc),
    RECEIVER(R.string.role_receiver, R.string.role_receiver_desc),
    CHARITY(R.string.role_charity, R.string.role_charity_desc);

    @StringRes public final int labelRes;
    @StringRes public final int descriptionRes;

    UserRole(@StringRes int labelRes, @StringRes int descriptionRes) {
        this.labelRes = labelRes;
        this.descriptionRes = descriptionRes;
    }

    /** Lenient parse used when reading untyped data (e.g. the role field of a snapshot). */
    public static UserRole fromName(String name) {
        if (name == null) return null;
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
