package com.sharebite.app.ui.auth;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.sharebite.app.data.model.User;
import com.sharebite.app.databinding.ActivitySplashBinding;
import com.sharebite.app.domain.interfaces.ResultCallback;
import com.sharebite.app.ui.home.MainActivity;
import com.sharebite.app.utils.ErrorMessages;
import com.sharebite.app.utils.ServiceLocator;
import com.sharebite.app.utils.SessionManager;
import com.sharebite.app.utils.ViewUtils;

/**
 * Entry point. Routes to setup instructions (no Firebase config), welcome (signed out) or the
 * main screen once the signed-in user's profile has loaded (Firebase Auth persists sessions).
 */
@SuppressLint("CustomSplashScreen") // Routing activity (auth/profile check), shown after the system splash.
public class SplashActivity extends AppCompatActivity {

    private ActivitySplashBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT));
        super.onCreate(savedInstanceState);
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.retryButton.setOnClickListener(v -> route());
        route();
    }

    private void route() {
        if (!ServiceLocator.isFirebaseConfigured(this)) {
            open(FirebaseSetupActivity.class);
            return;
        }
        SessionManager session = ServiceLocator.get().session();
        String uid = session.uid();
        if (uid == null) {
            open(WelcomeActivity.class);
            return;
        }
        showLoading(true);
        ServiceLocator.get().users().getById(uid, new ResultCallback<User>() {
            @Override
            public void onSuccess(User user) {
                if (isFinishing()) return;
                if (user == null || !user.isActive()) {
                    session.signOut();
                    open(WelcomeActivity.class);
                } else {
                    session.start(user);
                    open(MainActivity.class);
                }
            }

            @Override
            public void onError(@NonNull Exception error) {
                if (isFinishing()) return;
                showLoading(false);
                binding.errorText.setText(ErrorMessages.from(error));
            }
        });
    }

    private void showLoading(boolean loading) {
        ViewUtils.visible(binding.progress, loading);
        ViewUtils.visible(binding.errorGroup, !loading);
    }

    private void open(Class<?> destination) {
        startActivity(new Intent(this, destination));
        finish();
    }
}
