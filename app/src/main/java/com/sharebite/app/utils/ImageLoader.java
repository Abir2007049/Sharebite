package com.sharebite.app.utils;

import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.sharebite.app.R;

/**
 * Glide wrapper so every picture uses the same placeholder, caching and fade. Accepts https URLs
 * (Firebase Storage) and {@code firestore-image://} references (see FirestoreImageLoader).
 */
public final class ImageLoader {

    private ImageLoader() {
    }

    /** What Glide should load for a stored URL. */
    @Nullable
    static Object model(@Nullable String url) {
        if (url == null || url.isEmpty()) return null;
        FirestoreImageLoader.Ref ref = FirestoreImageLoader.Ref.parse(url);
        return ref != null ? ref : url;
    }

    public static void loadFood(@NonNull ImageView view, @Nullable String url) {
        load(view, url, R.drawable.placeholder_food);
    }

    /** Round provider logo; falls back to {@code fallback} (e.g. a monogram) when there is none. */
    public static void loadLogo(@NonNull ImageView view, @Nullable String url, @NonNull android.graphics.drawable.Drawable fallback) {
        Object model = model(url);
        if (model == null) {
            Glide.with(view).clear(view);
            view.setImageDrawable(fallback);
            return;
        }
        Glide.with(view).load(model).placeholder(fallback).error(fallback)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC).transform(new CircleCrop()).into(view);
    }

    private static void load(ImageView view, @Nullable String url, @DrawableRes int placeholder) {
        Object model = model(url);
        if (model == null) {
            Glide.with(view).clear(view);
            view.setImageResource(placeholder);
            return;
        }
        Glide.with(view)
                .load(model)
                .placeholder(placeholder)
                .error(placeholder)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .transition(DrawableTransitionOptions.withCrossFade(180))
                .centerCrop()
                .into(view);
    }

    public static void loadLocal(@NonNull ImageView view, @NonNull android.net.Uri uri) {
        Glide.with(view).load(uri).centerCrop().into(view);
    }
}
