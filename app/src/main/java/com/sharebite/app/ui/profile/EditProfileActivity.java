package com.sharebite.app.ui.profile;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import androidx.annotation.NonNull;

import com.sharebite.app.R;
import com.sharebite.app.data.model.Provider;
import com.sharebite.app.data.model.ProviderType;
import com.sharebite.app.data.model.User;
import com.sharebite.app.data.model.UserRole;
import com.sharebite.app.databinding.ActivityEditProfileBinding;
import com.sharebite.app.domain.interfaces.ResultCallback;
import com.sharebite.app.domain.service.InputValidator;
import com.sharebite.app.ui.BaseActivity;
import com.sharebite.app.utils.ErrorMessages;
import com.sharebite.app.utils.ServiceLocator;
import com.sharebite.app.utils.ViewUtils;

/**
 * Edits name, phone and organisation details. Email, role and verification status are not
 * editable here (security rules reject changes to them too).
 */
public class EditProfileActivity extends BaseActivity {

    private static final String STATE_TYPE = "providerType";

    private ActivityEditProfileBinding binding;
    private User user;
    private ProviderType providerType;
    private boolean saving;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        user = requireUser();
        if (user == null) return;
        binding = ActivityEditProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot(), true, true);
        setupToolbar(binding.toolbar, true);

        boolean isProvider = user.getRole() == UserRole.PROVIDER;
        boolean isCharity = user.getRole() == UserRole.CHARITY;
        if (user instanceof Provider) providerType = ((Provider) user).getProviderType();
        if (savedInstanceState != null && savedInstanceState.getString(STATE_TYPE) != null) {
            providerType = ProviderType.valueOf(savedInstanceState.getString(STATE_TYPE));
        }

        ViewUtils.visible(binding.providerTypeInput, isProvider);
        ViewUtils.visible(binding.organizationInput, isProvider || isCharity);
        binding.organizationInput.setHint(isCharity ? R.string.organization_name : R.string.business_name_optional);
        binding.emailValue.setText(user.getEmail());

        if (savedInstanceState == null) {
            ViewUtils.setText(binding.nameInput, user.getFullName());
            ViewUtils.setText(binding.phoneInput, user.getPhone());
            ViewUtils.setText(binding.organizationInput, user.getOrganizationName());
        }
        if (isProvider) setupProviderTypes();
        binding.saveButton.setOnClickListener(v -> save());
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (providerType != null) outState.putString(STATE_TYPE, providerType.name());
    }

    private void setupProviderTypes() {
        ProviderType[] types = ProviderType.values();
        String[] labels = new String[types.length];
        for (int i = 0; i < types.length; i++) labels[i] = getString(types[i].labelRes);
        AutoCompleteTextView field = (AutoCompleteTextView) binding.providerTypeInput.getEditText();
        field.setAdapter(new ArrayAdapter<>(this, R.layout.item_dropdown, labels));
        if (providerType != null) field.setText(getString(providerType.labelRes), false);
        field.setOnItemClickListener((p, v, pos, id) -> providerType = types[pos]);
    }

    private void save() {
        if (saving) return;
        String name = ViewUtils.text(binding.nameInput).trim();
        String phone = ViewUtils.text(binding.phoneInput).trim();
        String org = ViewUtils.text(binding.organizationInput).trim();
        boolean charity = user.getRole() == UserRole.CHARITY;
        boolean invalid = ViewUtils.setError(binding.nameInput, InputValidator.validateFullName(name));
        invalid |= ViewUtils.setError(binding.phoneInput, InputValidator.validatePhone(phone));
        if (user.getRole() != UserRole.RECEIVER) {
            invalid |= ViewUtils.setError(binding.organizationInput, InputValidator.validateOrganizationName(org, charity));
        }
        if (invalid) return;

        saving = true;
        ViewUtils.visible(binding.progress, true);
        binding.saveButton.setEnabled(false);
        ServiceLocator.get().users().updateProfile(user.getUid(), name, phone,
                user.getRole() == UserRole.RECEIVER ? null : (org.isEmpty() ? null : org),
                user.getRole() == UserRole.PROVIDER ? providerType : null,
                new ResultCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        // SessionManager's realtime listener picks up the new profile.
                        finish();
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        saving = false;
                        ViewUtils.visible(binding.progress, false);
                        binding.saveButton.setEnabled(true);
                        showMessage(ErrorMessages.from(error));
                    }
                });
    }
}
