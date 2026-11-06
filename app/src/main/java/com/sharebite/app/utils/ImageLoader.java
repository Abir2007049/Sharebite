package com.sharebite.app.utils;

import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.sharebite.app.R;

/** Glide wrapper so every food image uses the same placeholder, caching and fade. */
public final class ImageLoader {

    private ImageLoader() {
    }

    public static void loadFood(@NonNull ImageView view, @Nullable String url) {
        if (url == null || url.isEmpty()) {
            Glide.with(view).clear(view);
            view.setImageResource(R.drawable.placeholder_food);
            return;
        }
        Glide.with(view)
                .load(url)
                .placeholder(R.drawable.placeholder_food)
                .error(R.drawable.placeholder_food)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .transition(DrawableTransitionOptions.withCrossFade(180))
                .centerCrop()
                .into(view);
    }

    public static void loadLocal(@NonNull ImageView view, @NonNull android.net.Uri uri) {
        Glide.with(view).load(uri).centerCrop().into(view);
    }
}
