package com.sharebite.app.ui.auth;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.sharebite.app.data.model.RegistrationRequest;
import com.sharebite.app.data.model.User;
import com.sharebite.app.domain.interfaces.AuthRepository;
import com.sharebite.app.domain.interfaces.ResultCallback;
import com.sharebite.app.utils.ErrorMessages;
import com.sharebite.app.utils.Event;
import com.sharebite.app.utils.ServiceLocator;
import com.sharebite.app.utils.SessionManager;

/** Login, registration and password reset. Survives rotation so requests are not repeated. */
public class AuthViewModel extends ViewModel {

    private final AuthRepository auth;
    private final SessionManager session;

    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Event<String>> error = new MutableLiveData<>();
    private final MutableLiveData<Event<User>> signedIn = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> resetSent = new MutableLiveData<>();

    public AuthViewModel() {
        ServiceLocator locator = ServiceLocator.get();
        this.auth = locator.auth();
        this.session = locator.session();
    }

    public LiveData<Boolean> loading() { return loading; }
    public LiveData<Event<String>> error() { return error; }
    public LiveData<Event<User>> signedIn() { return signedIn; }
    public LiveData<Event<String>> resetSent() { return resetSent; }

    public void login(String email, String password) {
        if (Boolean.TRUE.equals(loading.getValue())) return;
        loading.setValue(true);
        auth.login(email, password, userCallback());
    }

    public void register(RegistrationRequest request) {
        if (Boolean.TRUE.equals(loading.getValue())) return;
        loading.setValue(true);
        auth.register(request, userCallback());
    }

    public void sendPasswordReset(String email) {
        if (Boolean.TRUE.equals(loading.getValue())) return;
        loading.setValue(true);
        auth.sendPasswordReset(email, new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                loading.setValue(false);
                resetSent.setValue(new Event<>(email.trim()));
            }

            @Override
            public void onError(@NonNull Exception e) {
                loading.setValue(false);
                error.setValue(new Event<>(ErrorMessages.from(e)));
            }
        });
    }

    private ResultCallback<User> userCallback() {
        return new ResultCallback<User>() {
            @Override
            public void onSuccess(User user) {
                session.start(user);
                loading.setValue(false);
                signedIn.setValue(new Event<>(user));
            }

            @Override
            public void onError(@NonNull Exception e) {
                loading.setValue(false);
                error.setValue(new Event<>(ErrorMessages.from(e)));
            }
        };
    }
}
