package com.sharebite.app.ui.auth;

import android.os.Bundle;

import androidx.lifecycle.ViewModelProvider;

import com.sharebite.app.R;
import com.sharebite.app.databinding.ActivityForgotPasswordBinding;
import com.sharebite.app.domain.service.InputValidator;
import com.sharebite.app.ui.BaseActivity;
import com.sharebite.app.utils.ViewUtils;

public class ForgotPasswordActivity extends BaseActivity {

    public static final String EXTRA_EMAIL = "email";

    private ActivityForgotPasswordBinding binding;
    private AuthViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityForgotPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot(), true, true);
        setupToolbar(binding.toolbar, true);
        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        if (savedInstanceState == null) {
            ViewUtils.setText(binding.emailInput, getIntent().getStringExtra(EXTRA_EMAIL));
        }
        binding.sendButton.setOnClickListener(v -> {
            String email = ViewUtils.text(binding.emailInput);
            if (ViewUtils.setError(binding.emailInput, InputValidator.validateEmail(email))) return;
            ViewUtils.hideKeyboard(this);
            viewModel.sendPasswordReset(email);
        });
        binding.backToLogin.setOnClickListener(v -> finish());

        viewModel.loading().observe(this, loading -> {
            binding.sendButton.setEnabled(!loading);
            ViewUtils.visible(binding.progress, loading);
        });
        viewModel.error().observe(this, e -> {
            String msg = e.consume();
            if (msg != null) showMessage(msg);
        });
        viewModel.resetSent().observe(this, e -> {
            String email = e.peek();
            ViewUtils.visible(binding.formGroup, false);
            ViewUtils.visible(binding.successGroup, true);
            binding.successText.setText(getString(R.string.reset_sent_message, email));
        });
    }
}
