package com.sharebite.app.ui.listing;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.sharebite.app.R;
import com.sharebite.app.data.model.DistributionType;
import com.sharebite.app.data.model.FoodCategory;
import com.sharebite.app.data.model.FoodListing;
import com.sharebite.app.data.model.QuantityUnit;
import com.sharebite.app.data.model.User;
import com.sharebite.app.databinding.ActivityListingEditorBinding;
import com.sharebite.app.domain.interfaces.ResultCallback;
import com.sharebite.app.domain.service.ListingDraft;
import com.sharebite.app.domain.service.ListingValidator;
import com.sharebite.app.domain.service.ListingValidator.Field;
import com.sharebite.app.ui.BaseActivity;
import com.sharebite.app.utils.Formatters;
import com.sharebite.app.utils.ImageLoader;
import com.sharebite.app.utils.ViewUtils;

import java.util.Date;
import java.util.Locale;

/** Create or edit a surplus food listing (providers only). */
public class ListingEditorActivity extends BaseActivity {

    private static final String EXTRA_LISTING_ID = "listingId";
    private static final String STATE_PENDING_DATE = "pendingDate";

    private ActivityListingEditorBinding binding;
    private ListingEditorViewModel viewModel;
    private DateTimePicker dateTimePicker;
    private User provider;

    private final ActivityResultLauncher<PickVisualMediaRequest> photoPicker =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) viewModel.setImage(uri);
            });

    private final ActivityResultLauncher<Intent> locationPicker =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
                Intent d = result.getData();
                viewModel.setLocation(d.getDoubleExtra(LocationPickerActivity.EXTRA_LAT, 0),
                        d.getDoubleExtra(LocationPickerActivity.EXTRA_LNG, 0));
                String address = d.getStringExtra(LocationPickerActivity.EXTRA_ADDRESS);
                if (address != null && ViewUtils.text(binding.addressInput).trim().isEmpty()) {
                    ViewUtils.setText(binding.addressInput, address);
                }
            });

    public static Intent createIntent(Context context) {
        return new Intent(context, ListingEditorActivity.class);
    }

    public static Intent editIntent(Context context, String listingId) {
        return new Intent(context, ListingEditorActivity.class).putExtra(EXTRA_LISTING_ID, listingId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        provider = requireUser();
        if (provider == null) return;
        if (!provider.canCreateListings()) {
            finish();
            return;
        }
        binding = ActivityListingEditorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot(), true, true);
        setupToolbar(binding.toolbar, true);
        viewModel = new ViewModelProvider(this).get(ListingEditorViewModel.class);

        dateTimePicker = new DateTimePicker(this, (key, value) -> viewModel.setTime(key, value));
        dateTimePicker.reattach(ListingEditorViewModel.keyPrep(), ListingEditorViewModel.keyStart(), ListingEditorViewModel.keyDeadline());
        if (savedInstanceState != null) dateTimePicker.restorePendingDate(savedInstanceState.getLong(STATE_PENDING_DATE));

        String listingId = getIntent().getStringExtra(EXTRA_LISTING_ID);
        binding.toolbar.setTitle(listingId == null ? R.string.new_listing : R.string.edit_listing);
        binding.saveButton.setText(listingId == null ? R.string.publish_listing : R.string.save_changes);
        if (listingId != null) {
            binding.formContent.setAlpha(0.4f);
            binding.formContent.setEnabled(false);
            viewModel.load(listingId, new ResultCallback<FoodListing>() {
                @Override public void onSuccess(FoodListing l) { populateTextFields(l); }
                @Override public void onError(@NonNull Exception e) { }
            });
        } else if (savedInstanceState == null) {
            applyDefaults();
        }

        setupDropdowns();
        setupDistributionToggle();
        binding.photoCard.setOnClickListener(v -> pickPhoto());
        binding.changePhotoButton.setOnClickListener(v -> pickPhoto());
        binding.prepInput.getEditText().setOnClickListener(v ->
                dateTimePicker.pick(ListingEditorViewModel.keyPrep(), viewModel.preparationTime().getValue(), DateTimePicker.Direction.PAST));
        binding.startInput.getEditText().setOnClickListener(v ->
                dateTimePicker.pick(ListingEditorViewModel.keyStart(), viewModel.pickupStart().getValue(), DateTimePicker.Direction.FUTURE));
        binding.deadlineInput.getEditText().setOnClickListener(v ->
                dateTimePicker.pick(ListingEditorViewModel.keyDeadline(), viewModel.pickupDeadline().getValue(), DateTimePicker.Direction.FUTURE));
        binding.pickOnMapButton.setOnClickListener(v -> locationPicker.launch(LocationPickerActivity.intent(this,
                viewModel.lat(), viewModel.lng(), ViewUtils.text(binding.addressInput).trim())));
        binding.clearPinButton.setOnClickListener(v -> viewModel.setLocation(null, null));
        binding.saveButton.setOnClickListener(v -> save());

        observe();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmDiscard();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (dateTimePicker != null) outState.putLong(STATE_PENDING_DATE, dateTimePicker.pendingDate());
    }

    private void applyDefaults() {
        // Sensible starting point: prepared now, pickup from now until 3 hours from now.
        Date now = new Date();
        viewModel.setTime(ListingEditorViewModel.keyPrep(), now);
        viewModel.setTime(ListingEditorViewModel.keyStart(), now);
        viewModel.setTime(ListingEditorViewModel.keyDeadline(), new Date(now.getTime() + 3 * 60 * 60 * 1000L));
    }

    private void observe() {
        viewModel.original().observe(this, l -> {
            if (l == null) return;
            binding.formContent.setAlpha(1f);
            binding.formContent.setEnabled(true);
            int reserved = l.reservedQuantity();
            ViewUtils.visible(binding.reservedNote, reserved > 0);
            if (reserved > 0) {
                binding.reservedNote.setText(getString(R.string.reserved_note, Formatters.quantity(this, reserved, l.getUnit())));
                // The distribution type is fixed once people have reserved under its terms.
                for (int i = 0; i < binding.distributionToggle.getChildCount(); i++) {
                    binding.distributionToggle.getChildAt(i).setEnabled(false);
                }
            }
        });
        viewModel.imageUri().observe(this, uri -> {
            if (uri != null) {
                ImageLoader.loadLocal(binding.photoPreview, uri);
                showPhoto(true);
            }
        });
        viewModel.original().observe(this, l -> {
            if (l != null && viewModel.imageUri().getValue() == null && l.getImageUrl() != null) {
                ImageLoader.loadFood(binding.photoPreview, l.getImageUrl());
                showPhoto(true);
            }
        });
        viewModel.category().observe(this, c -> setDropdown(binding.categoryInput, c == null ? null : getString(c.labelRes)));
        viewModel.unit().observe(this, u -> setDropdown(binding.unitInput, u == null ? null : getString(u.labelRes)));
        viewModel.distribution().observe(this, this::renderDistribution);
        viewModel.preparationTime().observe(this, d -> setDate(binding.prepInput, d));
        viewModel.pickupStart().observe(this, d -> setDate(binding.startInput, d));
        viewModel.pickupDeadline().observe(this, d -> setDate(binding.deadlineInput, d));
        viewModel.latitude().observe(this, lat -> {
            boolean pinned = lat != null && viewModel.lng() != null;
            binding.pinStatus.setText(pinned
                    ? getString(R.string.pin_set_format, String.format(Locale.US, "%.5f, %.5f", lat, viewModel.lng()))
                    : getString(R.string.pin_not_set));
            ViewUtils.visible(binding.clearPinButton, pinned);
            binding.pickOnMapButton.setText(pinned ? R.string.change_pin : R.string.pick_on_map);
        });
        viewModel.saving().observe(this, saving -> {
            binding.saveButton.setEnabled(!saving);
            ViewUtils.visible(binding.progress, saving);
            binding.saveButton.setText(saving ? R.string.saving
                    : viewModel.isEditing() ? R.string.save_changes : R.string.publish_listing);
        });
        viewModel.validation().observe(this, this::showValidation);
        viewModel.error().observe(this, e -> {
            String msg = e.consume();
            if (msg != null) showMessage(msg);
        });
        viewModel.saved().observe(this, e -> {
            FoodListing l = e.consume();
            if (l == null) return;
            setResult(Activity.RESULT_OK);
            if (!viewModel.isEditing()) startActivity(ListingDetailActivity.intent(this, l.getListingId()));
            finish();
        });
    }

    private void populateTextFields(FoodListing l) {
        ViewUtils.setText(binding.titleInput, l.getTitle());
        ViewUtils.setText(binding.descriptionInput, l.getDescription());
        ViewUtils.setText(binding.quantityInput, String.valueOf(l.getTotalQuantity()));
        if (l.getDistributionType() == DistributionType.DISCOUNTED) {
            ViewUtils.setText(binding.originalPriceInput, plain(l.getOriginalPrice()));
            ViewUtils.setText(binding.discountedPriceInput, plain(l.getDiscountedPrice()));
        }
        ViewUtils.setText(binding.addressInput, l.getPickupAddress());
        ViewUtils.setText(binding.storageInput, l.getStorageInformation());
        ViewUtils.setText(binding.allergenInput, l.getAllergens());
        ViewUtils.setText(binding.safetyNotesInput, l.getFoodSafetyNotes());
        binding.safetyCheckbox.setChecked(l.isSafetyDeclared());
    }

    private static String plain(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.format(Locale.US, "%.2f", value);
    }

    private void setupDropdowns() {
        FoodCategory[] categories = FoodCategory.values();
        String[] categoryLabels = new String[categories.length];
        for (int i = 0; i < categories.length; i++) categoryLabels[i] = getString(categories[i].labelRes);
        AutoCompleteTextView categoryField = (AutoCompleteTextView) binding.categoryInput.getEditText();
        categoryField.setAdapter(new ArrayAdapter<>(this, R.layout.item_dropdown, categoryLabels));
        categoryField.setOnItemClickListener((p, v, pos, id) -> viewModel.setCategory(categories[pos]));

        QuantityUnit[] units = QuantityUnit.values();
        String[] unitLabels = new String[units.length];
        for (int i = 0; i < units.length; i++) unitLabels[i] = getString(units[i].labelRes);
        AutoCompleteTextView unitField = (AutoCompleteTextView) binding.unitInput.getEditText();
        unitField.setAdapter(new ArrayAdapter<>(this, R.layout.item_dropdown, unitLabels));
        unitField.setOnItemClickListener((p, v, pos, id) -> viewModel.setUnit(units[pos]));
    }

    private void setDropdown(TextInputLayout layout, String label) {
        AutoCompleteTextView field = (AutoCompleteTextView) layout.getEditText();
        if (field != null) field.setText(label, false); // false: keep the full option list.
    }

    private void setupDistributionToggle() {
        binding.distributionToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.typeFree) viewModel.setDistribution(DistributionType.FREE);
            else if (checkedId == R.id.typeDiscounted) viewModel.setDistribution(DistributionType.DISCOUNTED);
            else if (checkedId == R.id.typeCharity) viewModel.setDistribution(DistributionType.CHARITY);
        });
    }

    private void renderDistribution(DistributionType type) {
        int id = type == DistributionType.DISCOUNTED ? R.id.typeDiscounted
                : type == DistributionType.CHARITY ? R.id.typeCharity : R.id.typeFree;
        if (binding.distributionToggle.getCheckedButtonId() != id) binding.distributionToggle.check(id);
        binding.distributionDescription.setText(type.descriptionRes);
        ViewUtils.visible(binding.priceRow, type == DistributionType.DISCOUNTED);
    }

    private void setDate(TextInputLayout layout, Date d) {
        ViewUtils.setText(layout, d == null ? "" : Formatters.dateTime(this, d));
    }

    /** Gallery (system photo picker, read through the MediaStore content provider) or camera. */
    private void pickPhoto() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.add_photo)
                .setItems(new CharSequence[]{getString(R.string.take_photo), getString(R.string.choose_from_gallery)}, (d, which) -> {
                    if (which == 0) takePhoto();
                    else photoPicker.launch(new PickVisualMediaRequest.Builder()
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                            .build());
                })
                .show();
    }

    // ---- camera via our FileProvider (a ContentProvider) -----------------------------------------
    private android.net.Uri pendingCapture;

    private final ActivityResultLauncher<android.net.Uri> camera =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), saved -> {
                if (Boolean.TRUE.equals(saved) && pendingCapture != null) viewModel.setImage(pendingCapture);
            });

    private final ActivityResultLauncher<String> cameraPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) launchCamera();
                else showMessage(getString(R.string.camera_permission_denied));
            });

    private void takePhoto() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) launchCamera();
        else cameraPermission.launch(android.Manifest.permission.CAMERA);
    }

    private void launchCamera() {
        try {
            java.io.File dir = new java.io.File(getCacheDir(), "camera");
            if (!dir.exists() && !dir.mkdirs()) throw new java.io.IOException("no cache dir");
            java.io.File file = java.io.File.createTempFile("listing_", ".jpg", dir);
            pendingCapture = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            camera.launch(pendingCapture);
        } catch (java.io.IOException | IllegalArgumentException | android.content.ActivityNotFoundException e) {
            showMessage(getString(R.string.camera_unavailable));
        }
    }

    private void showPhoto(boolean hasPhoto) {
        ViewUtils.visible(binding.photoPreview, hasPhoto);
        ViewUtils.visible(binding.photoPlaceholder, !hasPhoto);
        ViewUtils.visible(binding.changePhotoButton, hasPhoto);
    }

    private ListingDraft readDraft() {
        ListingDraft d = new ListingDraft();
        d.title = ViewUtils.text(binding.titleInput);
        d.description = ViewUtils.text(binding.descriptionInput);
        d.quantityText = ViewUtils.text(binding.quantityInput);
        d.originalPriceText = ViewUtils.text(binding.originalPriceInput);
        d.discountedPriceText = ViewUtils.text(binding.discountedPriceInput);
        d.pickupAddress = ViewUtils.text(binding.addressInput);
        d.storageInformation = ViewUtils.text(binding.storageInput);
        d.allergens = ViewUtils.text(binding.allergenInput);
        d.foodSafetyNotes = ViewUtils.text(binding.safetyNotesInput);
        d.safetyDeclared = binding.safetyCheckbox.isChecked();
        return d;
    }

    private void save() {
        ViewUtils.hideKeyboard(this);
        ListingDraft draft = readDraft();
        if (!viewModel.validate(draft)) {
            showMessage(getString(R.string.error_fix_fields));
            return;
        }
        viewModel.save(draft, provider, getContentResolver());
    }

    private void showValidation(ListingValidator.Result r) {
        ViewUtils.setError(binding.titleInput, r.errorFor(Field.TITLE));
        ViewUtils.setError(binding.descriptionInput, r.errorFor(Field.DESCRIPTION));
        ViewUtils.setError(binding.categoryInput, r.errorFor(Field.CATEGORY));
        ViewUtils.setError(binding.quantityInput, r.errorFor(Field.QUANTITY));
        ViewUtils.setError(binding.unitInput, r.errorFor(Field.UNIT));
        ViewUtils.setError(binding.originalPriceInput, r.errorFor(Field.ORIGINAL_PRICE));
        ViewUtils.setError(binding.discountedPriceInput, r.errorFor(Field.DISCOUNTED_PRICE));
        ViewUtils.setError(binding.prepInput, r.errorFor(Field.PREPARATION_TIME));
        ViewUtils.setError(binding.startInput, r.errorFor(Field.PICKUP_START));
        ViewUtils.setError(binding.deadlineInput, r.errorFor(Field.PICKUP_DEADLINE));
        ViewUtils.setError(binding.addressInput, r.errorFor(Field.ADDRESS));
        ViewUtils.setError(binding.storageInput, r.errorFor(Field.STORAGE));
        ViewUtils.setError(binding.allergenInput, r.errorFor(Field.ALLERGENS));
        String safety = r.errorFor(Field.SAFETY_DECLARATION);
        ViewUtils.visible(binding.safetyError, safety != null);
        binding.safetyError.setText(safety);
    }

    private void confirmDiscard() {
        if (Boolean.TRUE.equals(viewModel.saving().getValue())) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.discard_title)
                .setMessage(R.string.discard_message)
                .setNegativeButton(R.string.keep_editing, null)
                .setPositiveButton(R.string.discard, (d, w) -> finish())
                .show();
    }
}
