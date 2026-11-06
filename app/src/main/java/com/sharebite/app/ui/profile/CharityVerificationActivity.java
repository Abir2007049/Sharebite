package com.sharebite.app.ui.profile;

import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.sharebite.app.R;
import com.sharebite.app.data.model.CharityVerification;
import com.sharebite.app.data.model.User;
import com.sharebite.app.data.model.UserRole;
import com.sharebite.app.data.model.VerificationStatus;
import com.sharebite.app.databinding.ActivityCharityVerificationBinding;
import com.sharebite.app.domain.service.InputValidator;
import com.sharebite.app.ui.BaseActivity;
import com.sharebite.app.utils.Formatters;
import com.sharebite.app.utils.ImageLoader;
import com.sharebite.app.utils.ServiceLocator;
import com.sharebite.app.utils.UiState;
import com.sharebite.app.utils.ViewUtils;

/**
 * Charity submits organisation details for review. ShareBite never self-verifies: the status
 * becomes PENDING and only a reviewer can approve it (see README, "Reviewing charities").
 */
public class CharityVerificationActivity extends BaseActivity {

    private ActivityCharityVerificationBinding binding;
    private CharityVerificationViewModel viewModel;
    private User user;

    private final ActivityResultLauncher<PickVisualMediaRequest> picker =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) viewModel.setDocument(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        user = requireUser();
        if (user == null) return;
        if (user.getRole() != UserRole.CHARITY) {
            finish();
            return;
        }
        binding = ActivityCharityVerificationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot(), true, true);
        setupToolbar(binding.toolbar, true);
        viewModel = new ViewModelProvider(this).get(CharityVerificationViewModel.class);
        viewModel.load(user.getUid());

        if (savedInstanceState == null) {
            ViewUtils.setText(binding.orgNameInput, user.getOrganizationName());
            ViewUtils.setText(binding.contactInput, user.getFullName());
            ViewUtils.setText(binding.contactPhoneInput, user.getPhone());
        }
        binding.documentButton.setOnClickListener(v -> picker.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build()));
        binding.submitButton.setOnClickListener(v -> submit());

        ServiceLocator.get().session().currentUser().observe(this, u -> {
            if (u != null) renderStatus(u.getVerificationStatus());
        });
        viewModel.existing().observe(this, this::renderExisting);
        viewModel.document().observe(this, uri -> {
            ViewUtils.visible(binding.documentPreview, uri != null);
            if (uri != null) ImageLoader.loadLocal(binding.documentPreview, uri);
            binding.documentButton.setText(uri == null ? R.string.attach_document : R.string.replace_document);
        });
        viewModel.submitting().observe(this, s -> {
            ViewUtils.visible(binding.progress, s);
            binding.submitButton.setEnabled(!s);
        });
        viewModel.error().observe(this, e -> {
            String msg = e.consume();
            if (msg != null) showMessage(msg);
        });
        viewModel.submitted().observe(this, e -> {
            if (e.consume() == null) return;
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.verification_submitted_title)
                    .setMessage(R.string.verification_submitted_message)
                    .setPositiveButton(R.string.done, (d, w) -> finish())
                    .setCancelable(false)
                    .show();
        });
    }

    private void renderStatus(VerificationStatus status) {
        VerificationStatus s = status == null ? VerificationStatus.UNVERIFIED : status;
        ViewUtils.styleVerification(binding.statusBadge, s);
        int message;
        switch (s) {
            case VERIFIED: message = R.string.verification_status_verified; break;
            case PENDING: message = R.string.verification_status_pending; break;
            case REJECTED: message = R.string.verification_status_rejected; break;
            default: message = R.string.verification_status_unverified; break;
        }
        binding.statusMessage.setText(message);
        ViewUtils.visible(binding.formGroup, s.canSubmit());
    }

    private void renderExisting(UiState<CharityVerification> state) {
        CharityVerification v = state.data;
        if (v == null) return;
        if (v.getSubmittedAt() != null) {
            binding.submittedAt.setText(getString(R.string.submitted_on, Formatters.dateTime(this, v.getSubmittedAt())));
            ViewUtils.visible(binding.submittedAt, true);
        }
        if (v.getReviewerNote() != null && !v.getReviewerNote().isEmpty()) {
            ViewUtils.visible(binding.reviewerNote, true);
            binding.reviewerNote.setText(getString(R.string.reviewer_note, v.getReviewerNote()));
        }
        // Prefill a resubmission with the previous answers.
        if (ViewUtils.text(binding.registrationInput).isEmpty()) {
            ViewUtils.setText(binding.orgNameInput, v.getOrganizationName());
            ViewUtils.setText(binding.registrationInput, v.getRegistrationNumber());
            ViewUtils.setText(binding.contactInput, v.getContactPerson());
            ViewUtils.setText(binding.contactPhoneInput, v.getContactPhone());
            ViewUtils.setText(binding.addressInput, v.getOrganizationAddress());
            ViewUtils.setText(binding.descriptionInput, v.getDescription());
        }
    }

    private void submit() {
        String org = ViewUtils.text(binding.orgNameInput).trim();
        String reg = ViewUtils.text(binding.registrationInput).trim();
        String contact = ViewUtils.text(binding.contactInput).trim();
        String phone = ViewUtils.text(binding.contactPhoneInput).trim();
        String address = ViewUtils.text(binding.addressInput).trim();
        String desc = ViewUtils.text(binding.descriptionInput).trim();

        boolean invalid = ViewUtils.setError(binding.orgNameInput, InputValidator.validateOrganizationName(org, true));
        invalid |= ViewUtils.setError(binding.registrationInput, InputValidator.validateRequired(reg, getString(R.string.registration_number), 60));
        invalid |= ViewUtils.setError(binding.contactInput, InputValidator.validateFullName(contact));
        invalid |= ViewUtils.setError(binding.contactPhoneInput, InputValidator.validatePhone(phone));
        invalid |= ViewUtils.setError(binding.addressInput, InputValidator.validateRequired(address, getString(R.string.organisation_address), 200));
        invalid |= ViewUtils.setError(binding.descriptionInput, InputValidator.validateRequired(desc, getString(R.string.organisation_description), 1000));
        if (invalid) {
            showMessage(getString(R.string.error_fix_fields));
            return;
        }
        ViewUtils.hideKeyboard(this);
        CharityVerification v = new CharityVerification();
        v.setOrganizationId(user.getUid());
        v.setOrganizationName(org);
        v.setRegistrationNumber(reg);
        v.setContactPerson(contact);
        v.setContactPhone(phone);
        v.setOrganizationAddress(address);
        v.setDescription(desc);
        viewModel.submit(v, getContentResolver());
    }
}
