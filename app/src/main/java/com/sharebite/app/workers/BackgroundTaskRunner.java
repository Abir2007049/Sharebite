package com.sharebite.app.workers;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.sharebite.app.domain.interfaces.ResultCallback;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lifecycle-scoped wrapper around an {@link ExecutorService}.
 *
 * <ul>
 *     <li>Runs CPU or blocking work (image compression, HTTP calls, report aggregation) off the
 *     main thread.</li>
 *     <li>Delivers results on the main thread via {@link ResultCallback}.</li>
 *     <li>Tracks in-flight tasks so they can be cancelled, and drops results that arrive after
 *     {@link #shutdown()} so callbacks never touch a destroyed screen.</li>
 * </ul>
 * Owners (ViewModels) create one instance and call {@link #shutdown()} in {@code onCleared()}.
 */
public class BackgroundTaskRunner {

    private static final String TAG = "BackgroundTaskRunner";

    private final ExecutorService executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<Future<?>> inFlight = Collections.newSetFromMap(new WeakHashMap<>());
    private volatile boolean shutDown;

    public BackgroundTaskRunner(@NonNull String name, int threads) {
        this.executor = Executors.newFixedThreadPool(Math.max(1, threads), new NamedThreadFactory(name));
    }

    /** Runs {@code task} in the background and reports the outcome on the main thread. */
    public <T> Future<?> submit(@NonNull Callable<T> task, @NonNull ResultCallback<T> callback) {
        if (shutDown) {
            callback.onError(new CancellationException("Task runner already shut down"));
            return null;
        }
        try {
            Future<?> future = executor.submit(() -> {
                try {
                    T result = task.call();
                    postIfActive(() -> callback.onSuccess(result));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Cancelled: nobody is waiting for the result.
                } catch (Exception e) {
                    if (!Thread.currentThread().isInterrupted()) {
                        Log.w(TAG, "Background task failed", e);
                        postIfActive(() -> callback.onError(e));
                    }
                }
            });
            synchronized (inFlight) {
                inFlight.add(future);
            }
            return future;
        } catch (RejectedExecutionException e) {
            callback.onError(e);
            return null;
        }
    }

    /** Cancels (interrupts) every task that has not finished yet. */
    public void cancelAll() {
        synchronized (inFlight) {
            for (Future<?> f : inFlight) {
                if (f != null) f.cancel(true);
            }
            inFlight.clear();
        }
    }

    /** Cancels running work and releases the threads. Results arriving later are discarded. */
    public void shutdown() {
        shutDown = true;
        cancelAll();
        executor.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
    }

    public boolean isShutDown() {
        return shutDown;
    }

    private void postIfActive(Runnable r) {
        mainHandler.post(() -> {
            if (!shutDown) r.run();
        });
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger count = new AtomicInteger(1);

        NamedThreadFactory(String prefix) {
            this.prefix = "sharebite-" + prefix + "-";
        }

        @Override
        public Thread newThread(@NonNull Runnable r) {
            Thread t = new Thread(r, prefix + count.getAndIncrement());
            t.setPriority(Thread.NORM_PRIORITY - 1); // Keep the UI thread responsive.
            return t;
        }
    }
}
