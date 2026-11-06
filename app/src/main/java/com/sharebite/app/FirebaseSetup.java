package com.sharebite.app;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.MemoryCacheSettings;
import com.google.firebase.firestore.PersistentCacheSettings;
import com.google.firebase.storage.FirebaseStorage;

/**
 * Initialises Firebase either from google-services.json (normal builds) or, for local
 * development, against the Firebase Emulator Suite using a "demo-" project, which the emulators
 * accept without any cloud project or real API key.
 */
public final class FirebaseSetup {

    private static final String TAG = "FirebaseSetup";
    public static final String DEMO_PROJECT_ID = "demo-sharebite";

    private FirebaseSetup() {
    }

    /** @return true when Firebase is ready to use */
    public static boolean initialise(@NonNull Context context) {
        if (BuildConfig.USE_FIREBASE_EMULATORS) {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context, new FirebaseOptions.Builder()
                        .setProjectId(DEMO_PROJECT_ID)
                        .setApplicationId("1:000000000000:android:0000000000000000")
                        .setApiKey("demo-api-key-for-emulators")
                        .setStorageBucket(DEMO_PROJECT_ID + ".appspot.com")
                        .build());
            }
            String host = BuildConfig.EMULATOR_HOST;
            FirebaseAuth.getInstance().useEmulator(host, 9099);
            FirebaseFirestore firestore = FirebaseFirestore.getInstance();
            firestore.useEmulator(host, 8080);
            firestore.setFirestoreSettings(new FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build());
            FirebaseStorage.getInstance().useEmulator(host, 9199);
            Log.i(TAG, "Using Firebase emulators at " + host);
            return true;
        }
        if (!BuildConfig.FIREBASE_CONFIGURED || FirebaseApp.getApps(context).isEmpty()) {
            Log.w(TAG, "Firebase is not configured — add app/google-services.json (see README).");
            return false;
        }
        // Offline cache: listings and reservations remain viewable without connectivity.
        FirebaseFirestore.getInstance().setFirestoreSettings(new FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                .build());
        return true;
    }

    public static boolean isReady(@NonNull Context context) {
        return (BuildConfig.FIREBASE_CONFIGURED || BuildConfig.USE_FIREBASE_EMULATORS)
                && !FirebaseApp.getApps(context).isEmpty();
    }
}
