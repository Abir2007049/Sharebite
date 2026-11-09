package com.sharebite.app.ui.profile;

import android.content.ContentResolver;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.sharebite.app.data.model.CharityVerification;
import com.sharebite.app.domain.interfaces.ResultCallback;
import com.sharebite.app.utils.ErrorMessages;
import com.sharebite.app.utils.Event;
import com.sharebite.app.utils.ServiceLocator;
import com.sharebite.app.utils.UiState;
import com.sharebite.app.workers.BackgroundTaskRunner;
import com.sharebite.app.workers.ImageCompressionTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Loads an existing submission and submits a new one. An optional supporting document photo is
 * compressed in the background and uploaded to Storage before the Firestore write.
 */
public class CharityVerificationViewModel extends ViewModel {

    private final ServiceLocator services = ServiceLocator.get();
    private final BackgroundTaskRunner runner = new BackgroundTaskRunner("verification", 1);

    private final MutableLiveData<UiState<CharityVerification>> existing = new MutableLiveData<>(UiState.loading(null));
    private final MutableLiveData<Uri> document = new MutableLiveData<>();
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    private final MutableLiveData<Event<String>> error = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> submitted = new MutableLiveData<>();
    private boolean loaded;

    public LiveData<UiState<CharityVerification>> existing() { return existing; }
    public LiveData<Uri> document() { return document; }
    public LiveData<Boolean> submitting() { return submitting; }
    public LiveData<Event<String>> error() { return error; }
    public LiveData<Event<Boolean>> submitted() { return submitted; }

    public void load(String organizationId) {
        if (loaded) return;
        loaded = true;
        services.users().getCharityVerification(organizationId, new ResultCallback<CharityVerification>() {
            @Override public void onSuccess(CharityVerification result) { existing.setValue(UiState.success(result)); }
            @Override public void onError(@NonNull Exception e) { existing.setValue(UiState.error(ErrorMessages.from(e), null)); }
        });
    }

    public void setDocument(@Nullable Uri uri) {
        document.setValue(uri);
    }

    public void submit(@NonNull CharityVerification v, @NonNull ContentResolver resolver) {
        if (Boolean.TRUE.equals(submitting.getValue())) return;
        submitting.setValue(true);
        Uri doc = document.getValue();
        if (doc == null) {
            write(v);
            return;
        }
        runner.submit(new ImageCompressionTask(resolver, doc, com.sharebite.app.utils.ServiceLocator.get().images().maxDimensionPx(),
                com.sharebite.app.utils.ServiceLocator.get().images().maxBytes()), new ResultCallback<byte[]>() {
            @Override
            public void onSuccess(byte[] bytes) {
                services.images().uploadVerificationDocument(v.getOrganizationId(), bytes, new ResultCallback<String>() {
                    @Override
                    public void onSuccess(String url) {
                        List<String> docs = new ArrayList<>(v.getSubmittedDocuments());
                        docs.add(url);
                        v.setSubmittedDocuments(docs);
                        write(v);
                    }

                    @Override
                    public void onError(@NonNull Exception e) {
                        fail(e);
                    }
                });
            }

            @Override
            public void onError(@NonNull Exception e) {
                fail(e);
            }
        });
    }

    private void write(CharityVerification v) {
        services.users().submitCharityVerification(v, new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                submitting.setValue(false);
                submitted.setValue(new Event<>(true));
            }

            @Override
            public void onError(@NonNull Exception e) {
                fail(e);
            }
        });
    }

    private void fail(Exception e) {
        submitting.setValue(false);
        error.setValue(new Event<>(ErrorMessages.from(e)));
    }

    @Override
    protected void onCleared() {
        runner.shutdown();
    }
}
