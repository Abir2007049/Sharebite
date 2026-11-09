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
    private android.net.Uri pickedLogo;
    private android.net.Uri pickedCover;
    private com.sharebite.app.workers.BackgroundTaskRunner runner;

    private final androidx.activity.result.ActivityResultLauncher<androidx.activity.result.PickVisualMediaRequest> logoPicker =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri == null) return;
                pickedLogo = uri;
                com.bumptech.glide.Glide.with(this).load(uri).circleCrop().into(binding.logoImage);
            });
    private final androidx.activity.result.ActivityResultLauncher<androidx.activity.result.PickVisualMediaRequest> coverPicker =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri == null) return;
                pickedCover = uri;
                com.sharebite.app.utils.ImageLoader.loadLocal(binding.coverImage, uri);
            });

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
        if (isProvider) setupStorefront((Provider) user, savedInstanceState == null);
        binding.saveButton.setOnClickListener(v -> save());
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (providerType != null) outState.putString(STATE_TYPE, providerType.name());
    }

    private void setupStorefront(Provider p, boolean firstTime) {
        runner = new com.sharebite.app.workers.BackgroundTaskRunner("storefront-photos", 1);
        ViewUtils.visible(binding.storefrontSection, true);
        if (firstTime) ViewUtils.setText(binding.bioInput, p.getBio());
        com.sharebite.app.utils.ImageLoader.loadLogo(binding.logoImage, p.getProfileImageUrl(),
                new com.sharebite.app.utils.Monogram(this, p.displayName()));
        if (p.getCoverImageUrl() != null) com.sharebite.app.utils.ImageLoader.loadFood(binding.coverImage, p.getCoverImageUrl());
        androidx.activity.result.PickVisualMediaRequest imagesOnly = new androidx.activity.result.PickVisualMediaRequest.Builder()
                .setMediaType(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build();
        binding.logoButton.setOnClickListener(v -> logoPicker.launch(imagesOnly));
        binding.logoImage.setOnClickListener(v -> logoPicker.launch(imagesOnly));
        binding.coverButton.setOnClickListener(v -> coverPicker.launch(imagesOnly));
        binding.coverImage.setOnClickListener(v -> coverPicker.launch(imagesOnly));
    }

    @Override
    protected void onDestroy() {
        if (runner != null) runner.shutdown();
        super.onDestroy();
    }

    /** Compresses and uploads a picked picture, or passes null straight on when nothing was picked. */
    private void upload(@androidx.annotation.Nullable android.net.Uri uri,
                        com.sharebite.app.domain.interfaces.ImageStorage.Purpose purpose, ResultCallback<String> next) {
        if (uri == null) {
            next.onSuccess(null);
            return;
        }
        com.sharebite.app.domain.interfaces.ImageStorage images = ServiceLocator.get().images();
        runner.submit(new com.sharebite.app.workers.ImageCompressionTask(getContentResolver(), uri,
                purpose == com.sharebite.app.domain.interfaces.ImageStorage.Purpose.LOGO ? 512 : images.maxDimensionPx(),
                images.maxBytes()), new ResultCallback<byte[]>() {
            @Override
            public void onSuccess(byte[] bytes) {
                images.uploadProfileImage(user.getUid(), purpose, bytes, next);
            }

            @Override
            public void onError(@NonNull Exception error) {
                next.onError(error);
            }
        });
    }

    /** Providers: photos and about text first, then the basic profile, then refresh listing branding. */
    private void saveStorefrontThen(Runnable then) {
        if (!(user instanceof Provider)) {
            then.run();
            return;
        }
        String bio = ViewUtils.text(binding.bioInput).trim();
        upload(pickedLogo, com.sharebite.app.domain.interfaces.ImageStorage.Purpose.LOGO, new ResultCallback<String>() {
            @Override
            public void onSuccess(String logoUrl) {
                upload(pickedCover, com.sharebite.app.domain.interfaces.ImageStorage.Purpose.COVER, new ResultCallback<String>() {
                    @Override
                    public void onSuccess(String coverUrl) {
                        ServiceLocator.get().users().updateStorefront(user.getUid(), bio, logoUrl, coverUrl, new ResultCallback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                if (logoUrl != null) pendingLogoUrl = logoUrl;
                                then.run();
                            }

                            @Override public void onError(@NonNull Exception error) { failSave(error); }
                        });
                    }

                    @Override public void onError(@NonNull Exception error) { failSave(error); }
                });
            }

            @Override public void onError(@NonNull Exception error) { failSave(error); }
        });
    }

    private String pendingLogoUrl;

    private void failSave(@NonNull Exception error) {
        saving = false;
        ViewUtils.visible(binding.progress, false);
        binding.saveButton.setEnabled(true);
        showMessage(ErrorMessages.from(error));
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
        saveStorefrontThen(() -> saveBasics(name, phone, org));
    }

    private void saveBasics(String name, String phone, String org) {
        ServiceLocator.get().users().updateProfile(user.getUid(), name, phone,
                user.getRole() == UserRole.RECEIVER ? null : (org.isEmpty() ? null : org),
                user.getRole() == UserRole.PROVIDER ? providerType : null,
                new ResultCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        // SessionManager's realtime listener picks up the new profile.
                        if (user instanceof Provider) {
                            String shownName = ((Provider) user).getProviderType().isBusiness() && !org.isEmpty() ? org : name;
                            String logo = pendingLogoUrl != null ? pendingLogoUrl : user.getProfileImageUrl();
                            ServiceLocator.get().food().refreshProviderBranding(user.getUid(), shownName, logo, new ResultCallback<Void>() {
                                @Override public void onSuccess(Void r) { }
                                @Override public void onError(@NonNull Exception e) { }
                            });
                        }
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
