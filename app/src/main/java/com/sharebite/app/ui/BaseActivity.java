package com.sharebite.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;
import com.sharebite.app.data.model.User;
import com.sharebite.app.ui.auth.SplashActivity;
import com.sharebite.app.utils.ServiceLocator;

/**
 * Shared activity behaviour: edge-to-edge insets (enforced from Android 15), toolbar setup,
 * snackbars, and a guard that sends signed-out users back to the start of the app.
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
    }

    /** Pads {@code root} so content is not drawn under the status/navigation bars or keyboard. */
    protected void applySystemBarInsets(@NonNull View root, boolean top, boolean bottom) {
        final int l = root.getPaddingLeft(), t = root.getPaddingTop(), r = root.getPaddingRight(), b = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(l + bars.left, t + (top ? bars.top : 0), r + bars.right,
                    b + (bottom ? Math.max(bars.bottom, ime.bottom) : 0));
            return windowInsets;
        });
    }

    protected void setupToolbar(@NonNull MaterialToolbar toolbar, boolean showBack) {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(showBack);
        }
        if (showBack) toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
    }

    protected void showMessage(@NonNull String message) {
        View root = findViewById(android.R.id.content);
        Snackbar.make(root, message, Snackbar.LENGTH_LONG).show();
    }

    /**
     * Returns the signed-in user's profile, or redirects to the splash screen (which routes to
     * login) and returns null. Activities call this in onCreate before showing user data.
     */
    @Nullable
    protected User requireUser() {
        if (!ServiceLocator.isFirebaseConfigured(this)) {
            restartApp();
            return null;
        }
        User user = ServiceLocator.get().session().user();
        if (user == null) restartApp();
        return user;
    }

    protected void restartApp() {
        Intent intent = new Intent(this, SplashActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
