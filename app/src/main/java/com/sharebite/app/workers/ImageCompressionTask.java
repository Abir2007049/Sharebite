package com.sharebite.app.workers;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;

/**
 * Decodes a picked image, downsamples it to at most {@link #MAX_DIMENSION_PX}, applies EXIF
 * rotation and re-encodes as JPEG. Camera photos are often 5–12 MB; this keeps uploads ~200 KB
 * and avoids decoding a full-size bitmap (which can run out of memory on low-end phones).
 */
public class ImageCompressionTask implements Callable<byte[]> {

    public static final int MAX_DIMENSION_PX = 1280;
    private static final int JPEG_QUALITY = 82;
    /** Mirrors the limit in storage.rules. */
    public static final int MAX_UPLOAD_BYTES = 5 * 1024 * 1024;

    private final ContentResolver resolver;
    private final Uri uri;

    public ImageCompressionTask(@NonNull ContentResolver resolver, @NonNull Uri uri) {
        this.resolver = resolver;
        this.uri = uri;
    }

    @WorkerThread
    @Override
    public byte[] call() throws IOException, InterruptedException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = open()) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("The selected file is not a supported image.");
        }
        checkCancelled();

        BitmapFactory.Options decode = new BitmapFactory.Options();
        decode.inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION_PX);
        Bitmap sampled;
        try (InputStream in = open()) {
            sampled = BitmapFactory.decodeStream(in, null, decode);
        }
        if (sampled == null) throw new IOException("Could not decode the selected image.");
        checkCancelled();

        Bitmap scaled = scaleAndRotate(sampled, readRotation());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
        if (scaled != sampled) scaled.recycle();
        sampled.recycle();

        byte[] bytes = out.toByteArray();
        if (bytes.length > MAX_UPLOAD_BYTES) throw new IOException("Image is too large even after compression.");
        return bytes;
    }

    /** Largest power of two that keeps both sides ≥ target. Public for unit testing. */
    public static int calculateInSampleSize(int width, int height, int target) {
        int sample = 1;
        while ((width / (sample * 2)) >= target && (height / (sample * 2)) >= target) {
            sample *= 2;
        }
        return sample;
    }

    private Bitmap scaleAndRotate(Bitmap src, int rotationDegrees) {
        int w = src.getWidth();
        int h = src.getHeight();
        float scale = Math.min(1f, (float) MAX_DIMENSION_PX / Math.max(w, h));
        if (scale >= 1f && rotationDegrees == 0) return src;
        Matrix m = new Matrix();
        m.postScale(scale, scale);
        if (rotationDegrees != 0) m.postRotate(rotationDegrees);
        return Bitmap.createBitmap(src, 0, 0, w, h, m, true);
    }

    private int readRotation() {
        try (InputStream in = open()) {
            ExifInterface exif = new ExifInterface(in);
            int o = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (o) {
                case ExifInterface.ORIENTATION_ROTATE_90: return 90;
                case ExifInterface.ORIENTATION_ROTATE_180: return 180;
                case ExifInterface.ORIENTATION_ROTATE_270: return 270;
                default: return 0;
            }
        } catch (IOException e) {
            return 0; // Missing EXIF data is normal (e.g. screenshots).
        }
    }

    private InputStream open() throws IOException {
        InputStream in = resolver.openInputStream(uri);
        if (in == null) throw new IOException("Unable to open the selected image.");
        return in;
    }

    private static void checkCancelled() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
    }
}
