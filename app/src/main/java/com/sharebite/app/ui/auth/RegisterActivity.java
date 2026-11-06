package com.sharebite.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.card.MaterialCardView;
import com.sharebite.app.R;
import com.sharebite.app.data.model.ProviderType;
import com.sharebite.app.data.model.RegistrationRequest;
import com.sharebite.app.data.model.User;
import com.sharebite.app.data.model.UserRole;
import com.sharebite.app.databinding.ActivityRegisterBinding;
import com.sharebite.app.domain.service.InputValidator;
import com.sharebite.app.ui.BaseActivity;
import com.sharebite.app.ui.home.MainActivity;
import com.sharebite.app.utils.ViewUtils;

/** Registration with role selection. Organisation details appear only for roles that need them. */
public class RegisterActivity extends BaseActivity {

    private static final String STATE_ROLE = "role";
    private static final String STATE_PROVIDER_TYPE = "providerType";

    private ActivityRegisterBinding binding;
    private AuthViewModel viewModel;
    private UserRole selectedRole;
    private ProviderType selectedProviderType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot(), true, true);
        setupToolbar(binding.toolbar, true);
        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        bindRoleCard(binding.roleProvider, UserRole.PROVIDER);
        bindRoleCard(binding.roleReceiver, UserRole.RECEIVER);
        bindRoleCard(binding.roleCharity, UserRole.CHARITY);
        setupProviderTypes();

        if (savedInstanceState != null) {
            String role = savedInstanceState.getString(STATE_ROLE);
            String type = savedInstanceState.getString(STATE_PROVIDER_TYPE);
            if (type != null) selectedProviderType = ProviderType.valueOf(type);
            if (role != null) selectRole(UserRole.valueOf(role));
        }
        if (selectedRole == null) selectRole(UserRole.RECEIVER);

        binding.registerButton.setOnClickListener(v -> attemptRegister());
        binding.loginLink.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        viewModel.loading().observe(this, loading -> {
            binding.registerButton.setEnabled(!loading);
            binding.registerButton.setText(loading ? R.string.creating_account : R.string.create_account);
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

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (selectedRole != null) outState.putString(STATE_ROLE, selectedRole.name());
        if (selectedProviderType != null) outState.putString(STATE_PROVIDER_TYPE, selectedProviderType.name());
    }

    private void bindRoleCard(MaterialCardView card, UserRole role) {
        card.setOnClickListener(v -> selectRole(role));
    }

    private void selectRole(UserRole role) {
        selectedRole = role;
        binding.roleProvider.setChecked(role == UserRole.PROVIDER);
        binding.roleReceiver.setChecked(role == UserRole.RECEIVER);
        binding.roleCharity.setChecked(role == UserRole.CHARITY);

        ViewUtils.visible(binding.providerTypeInput, role == UserRole.PROVIDER);
        boolean showOrg = role == UserRole.CHARITY || (role == UserRole.PROVIDER
                && (selectedProviderType == null || selectedProviderType.isBusiness()));
        ViewUtils.visible(binding.organizationInput, showOrg);
        binding.organizationInput.setHint(role == UserRole.CHARITY ? R.string.organization_name : R.string.business_name);
        ViewUtils.visible(binding.charityNote, role == UserRole.CHARITY);
    }

    private void setupProviderTypes() {
        ProviderType[] types = ProviderType.values();
        String[] labels = new String[types.length];
        for (int i = 0; i < types.length; i++) labels[i] = getString(types[i].labelRes);
        AutoCompleteTextView field = (AutoCompleteTextView) binding.providerTypeInput.getEditText();
        if (field == null) return;
        field.setAdapter(new ArrayAdapter<>(this, R.layout.item_dropdown, labels));
        field.setOnItemClickListener((parent, view, position, id) -> {
            selectedProviderType = types[position];
            binding.providerTypeInput.setError(null);
            selectRole(UserRole.PROVIDER);
        });
    }

    private void attemptRegister() {
        String name = ViewUtils.text(binding.nameInput);
        String email = ViewUtils.text(binding.emailInput);
        String phone = ViewUtils.text(binding.phoneInput);
        String org = ViewUtils.text(binding.organizationInput);
        String password = ViewUtils.text(binding.passwordInput);
        String confirm = ViewUtils.text(binding.confirmInput);

        boolean invalid = ViewUtils.setError(binding.nameInput, InputValidator.validateFullName(name));
        invalid |= ViewUtils.setError(binding.emailInput, InputValidator.validateEmail(email));
        invalid |= ViewUtils.setError(binding.phoneInput, InputValidator.validatePhone(phone));
        if (selectedRole == UserRole.PROVIDER) {
            invalid |= ViewUtils.setError(binding.providerTypeInput,
                    selectedProviderType == null ? getString(R.string.error_provider_type) : null);
        }
        boolean orgVisible = binding.organizationInput.getVisibility() == android.view.View.VISIBLE;
        if (orgVisible) {
            invalid |= ViewUtils.setError(binding.organizationInput,
                    InputValidator.validateOrganizationName(org, selectedRole == UserRole.CHARITY || selectedRole == UserRole.PROVIDER));
        }
        invalid |= ViewUtils.setError(binding.passwordInput, InputValidator.validatePassword(password));
        invalid |= ViewUtils.setError(binding.confirmInput, InputValidator.validateConfirmation(password, confirm));
        if (invalid) {
            showMessage(getString(R.string.error_fix_fields));
            return;
        }
        ViewUtils.hideKeyboard(this);
        viewModel.register(new RegistrationRequest(name, email, password, phone, selectedRole,
                orgVisible ? org : null, selectedRole == UserRole.PROVIDER ? selectedProviderType : null));
    }
}
