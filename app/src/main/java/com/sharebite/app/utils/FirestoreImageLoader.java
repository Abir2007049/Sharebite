package com.sharebite.app.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;
import com.bumptech.glide.signature.ObjectKey;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.Blob;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.sharebite.app.data.repository.FirestoreImageStorage;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Lets Glide load {@code firestore-image://collection/id} pictures (see FirestoreImageStorage) with
 * its usual memory/disk caching, placeholders and view recycling. Registered in ShareBiteApp.
 */
public final class FirestoreImageLoader implements ModelLoader<FirestoreImageLoader.Ref, InputStream> {

    /** A picture stored in Firestore; equality by path so Glide can cache it. */
    public static final class Ref {
        final String path;

        public Ref(@NonNull String path) {
            this.path = path;
        }

        @Nullable
        public static Ref parse(@Nullable String url) {
            if (url == null || !url.startsWith(FirestoreImageStorage.SCHEME)) return null;
            String path = url.substring(FirestoreImageStorage.SCHEME.length());
            String[] parts = path.split("/");
            if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) return null;
            return new Ref(path);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Ref && ((Ref) o).path.equals(path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path);
        }
    }

    @Override
    public LoadData<InputStream> buildLoadData(@NonNull Ref ref, int width, int height, @NonNull Options options) {
        return new LoadData<>(new ObjectKey(FirestoreImageStorage.SCHEME + ref.path), new Fetcher(ref));
    }

    @Override
    public boolean handles(@NonNull Ref ref) {
        return true;
    }

    public static final class Factory implements ModelLoaderFactory<Ref, InputStream> {
        @NonNull
        @Override
        public ModelLoader<Ref, InputStream> build(@NonNull MultiModelLoaderFactory multiFactory) {
            return new FirestoreImageLoader();
        }

        @Override
        public void teardown() { }
    }

    private static final class Fetcher implements DataFetcher<InputStream> {
        private final Ref ref;

        Fetcher(Ref ref) {
            this.ref = ref;
        }

        /** Called by Glide on a background thread, so blocking on the Firestore read is fine here. */
        @Override
        public void loadData(@NonNull Priority priority, @NonNull DataCallback<? super InputStream> callback) {
            try {
                DocumentSnapshot snap = Tasks.await(FirebaseFirestore.getInstance().document(ref.path).get(), 20, TimeUnit.SECONDS);
                Blob blob = snap.getBlob("data");
                if (blob == null) {
                    callback.onLoadFailed(new FileNotFoundException(ref.path));
                    return;
                }
                callback.onDataReady(new ByteArrayInputStream(blob.toBytes()));
            } catch (Exception e) {
                callback.onLoadFailed(e);
            }
        }

        @Override public void cleanup() { }
        @Override public void cancel() { }

        @NonNull
        @Override
        public Class<InputStream> getDataClass() {
            return InputStream.class;
        }

        @NonNull
        @Override
        public DataSource getDataSource() {
            return DataSource.REMOTE;
        }
    }
}
