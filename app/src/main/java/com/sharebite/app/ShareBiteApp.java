package com.sharebite.app;

import android.app.Application;

import java.io.File;

import com.sharebite.app.utils.FirestoreImageLoader;
import com.sharebite.app.utils.ServiceLocator;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;

/** Application entry point: wires Firebase-backed services when configuration is present. */
public class ShareBiteApp extends Application {

    /**
     * Shows the signed-in user as "online" (Realtime Database) while any ShareBite screen is visible,
     * and "offline" once the last one is hidden. Guests have no presence.
     */
    private static final class PresenceTracker implements ActivityLifecycleCallbacks {
        private int started;
        private String onlineUid;

        @Override
        public void onActivityStarted(@androidx.annotation.NonNull android.app.Activity a) {
            if (started++ > 0) return;
            com.sharebite.app.utils.SessionManager s = ServiceLocator.get().session();
            if (s.isGuest() || s.uid() == null) return;
            onlineUid = s.uid();
            ServiceLocator.get().presence().goOnline(onlineUid);
        }

        @Override
        public void onActivityStopped(@androidx.annotation.NonNull android.app.Activity a) {
            if (--started > 0 || onlineUid == null) return;
            ServiceLocator.get().presence().goOffline(onlineUid);
            onlineUid = null;
        }

        @Override public void onActivityCreated(@androidx.annotation.NonNull android.app.Activity a, android.os.Bundle b) { }
        @Override public void onActivityResumed(@androidx.annotation.NonNull android.app.Activity a) { }
        @Override public void onActivityPaused(@androidx.annotation.NonNull android.app.Activity a) { }
        @Override public void onActivitySaveInstanceState(@androidx.annotation.NonNull android.app.Activity a, @androidx.annotation.NonNull android.os.Bundle b) { }
        @Override public void onActivityDestroyed(@androidx.annotation.NonNull android.app.Activity a) { }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // OpenStreetMap tile servers require an identifying user agent; tiles are cached in
        // app-private storage (no storage permission needed under scoped storage).
        IConfigurationProvider osm = Configuration.getInstance();
        osm.setUserAgentValue(BuildConfig.APPLICATION_ID + "/" + BuildConfig.VERSION_NAME);
        File osmBase = new File(getCacheDir(), "osmdroid");
        osm.setOsmdroidBasePath(osmBase);
        osm.setOsmdroidTileCache(new File(osmBase, "tiles"));

        if (FirebaseSetup.initialise(this)) {
            ServiceLocator.init(this);
            // Teach Glide to load firestore-image:// pictures.
            com.bumptech.glide.Glide.get(this).getRegistry().prepend(FirestoreImageLoader.Ref.class,
                    java.io.InputStream.class, new FirestoreImageLoader.Factory());
            registerActivityLifecycleCallbacks(new PresenceTracker());
        }
        // Otherwise SplashActivity shows the setup instructions.
    }
}
