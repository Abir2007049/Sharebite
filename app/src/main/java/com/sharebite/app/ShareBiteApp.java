package com.sharebite.app;

import android.app.Application;
import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.PersistentCacheSettings;
import com.sharebite.app.utils.ServiceLocator;

import org.osmdroid.config.Configuration;

/** Application entry point: wires Firebase-backed services when configuration is present. */
public class ShareBiteApp extends Application {

    private static final String TAG = "ShareBiteApp";

    @Override
    public void onCreate() {
        super.onCreate();

        // OpenStreetMap tile servers require an identifying user agent.
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID + "/" + BuildConfig.VERSION_NAME);

        if (!ServiceLocator.isFirebaseConfigured(this)) {
            Log.w(TAG, "Firebase is not configured — add app/google-services.json (see README).");
            return;
        }
        // Offline cache: listings and reservations remain viewable without connectivity.
        FirebaseFirestore.getInstance().setFirestoreSettings(new FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                .build());
        ServiceLocator.init(this);
    }
}
