package com.sharebite.app;

import android.app.Application;

import java.io.File;

import com.sharebite.app.utils.FirestoreImageLoader;
import com.sharebite.app.utils.ServiceLocator;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;

/** Application entry point: wires Firebase-backed services when configuration is present. */
public class ShareBiteApp extends Application {

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
        }
        // Otherwise SplashActivity shows the setup instructions.
    }
}
