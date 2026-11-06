package com.sharebite.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;

import androidx.lifecycle.ViewModelProvider;

import com.sharebite.app.R;
import com.sharebite.app.data.model.User;
import com.sharebite.app.databinding.ActivityLoginBinding;
import com.sharebite.app.domain.service.InputValidator;
import com.sharebite.app.ui.BaseActivity;
import com.sharebite.app.ui.home.MainActivity;
import com.sharebite.app.utils.ViewUtils;

public class LoginActivity extends BaseActivity {

    private ActivityLoginBinding binding;
    private AuthViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot(), true, true);
        setupToolbar(binding.toolbar, true);
        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        binding.passwordInput.getEditText().setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin();
                return true;
            }
            return false;
        });
        binding.loginButton.setOnClickListener(v -> attemptLogin());
        binding.forgotPassword.setOnClickListener(v -> {
            Intent i = new Intent(this, ForgotPasswordActivity.class);
            i.putExtra(ForgotPasswordActivity.EXTRA_EMAIL, ViewUtils.text(binding.emailInput).trim());
            startActivity(i);
        });
        binding.registerLink.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
            finish();
        });

        viewModel.loading().observe(this, loading -> {
            binding.loginButton.setEnabled(!loading);
            binding.loginButton.setText(loading ? R.string.signing_in : R.string.log_in);
            ViewUtils.visible(binding.progress, loading);
        });
        viewModel.error().observe(this, e -> {
            String msg = e.consume();
            if (msg != null) showMessage(msg);
        });
        viewModel.signedIn().observe(this, e -> {
            User user = e.consume();
            if (user != null) {
                Intent i = new Intent(this, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
                finish();
            }
        });
    }

    private void attemptLogin() {
        String email = ViewUtils.text(binding.emailInput);
        String password = ViewUtils.text(binding.passwordInput);
        boolean invalid = ViewUtils.setError(binding.emailInput, InputValidator.validateEmail(email));
        invalid |= ViewUtils.setError(binding.passwordInput, InputValidator.validateLoginPassword(password));
        if (invalid) return;
        ViewUtils.hideKeyboard(this);
        viewModel.login(email, password);
    }
}
