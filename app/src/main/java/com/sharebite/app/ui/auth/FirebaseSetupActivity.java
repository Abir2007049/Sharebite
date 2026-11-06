package com.sharebite.app.ui.auth;

import android.os.Bundle;

import com.sharebite.app.databinding.ActivityFirebaseSetupBinding;
import com.sharebite.app.ui.BaseActivity;

/**
 * Shown when the APK was built without app/google-services.json. ShareBite does not fake a
 * backend, so instead of crashing it explains how to connect a Firebase project.
 */
public class FirebaseSetupActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityFirebaseSetupBinding binding = ActivityFirebaseSetupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot(), true, true);
        binding.closeButton.setOnClickListener(v -> finishAffinity());
    }
}
