package com.sharebite.app.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.sharebite.app.BuildConfig;
import com.sharebite.app.R;
import com.sharebite.app.data.model.User;
import com.sharebite.app.data.model.UserRole;
import com.sharebite.app.data.model.VerificationStatus;
import com.sharebite.app.databinding.FragmentProfileBinding;
import com.sharebite.app.ui.BaseTabFragment;
import com.sharebite.app.utils.ServiceLocator;
import com.sharebite.app.utils.ViewUtils;

import java.util.Locale;

/** Account overview, settings entry points and logout. */
public class ProfileFragment extends BaseTabFragment {

    private FragmentProfileBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        bindHeader(binding.header, getString(R.string.profile_title), null);
        binding.menuEditProfile.setOnClickListener(v -> startActivity(new Intent(requireContext(), EditProfileActivity.class)));
        binding.menuNotifications.setOnClickListener(v -> startActivity(new Intent(requireContext(), NotificationsActivity.class)));
        binding.menuVerification.setOnClickListener(v -> startActivity(new Intent(requireContext(), CharityVerificationActivity.class)));
        binding.menuSafety.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.food_safety_guidelines)
                .setMessage(R.string.food_safety_guidelines_body)
                .setPositiveButton(R.string.done, null)
                .show());
        binding.logoutButton.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.log_out_title)
                .setMessage(R.string.log_out_message)
                .setNegativeButton(R.string.not_now, null)
                .setPositiveButton(R.string.log_out, (d, w) -> ServiceLocator.get().session().signOut())
                .show());
        binding.versionText.setText(getString(R.string.version_format, BuildConfig.VERSION_NAME));

        ServiceLocator.get().session().currentUser().observe(getViewLifecycleOwner(), this::render);
        mainViewModel().notifications().observe(getViewLifecycleOwner(), list -> {
            int unread = mainViewModel().unreadCount();
            binding.menuNotifications.setText(unread > 0
                    ? getString(R.string.notifications_unread, unread) : getString(R.string.notifications));
        });
    }

    private void render(@Nullable User user) {
        if (user == null || binding == null) return;
        String name = user.displayName() == null ? "" : user.displayName().trim();
        binding.avatar.setText(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.getDefault()));
        binding.displayName.setText(name);
        binding.roleBadge.setText(user.getRole().labelRes);
        binding.email.setText(user.getEmail());
        binding.phone.setText(user.getPhone());

        boolean hasOrg = user.getOrganizationName() != null && !user.getOrganizationName().isEmpty()
                && !user.getOrganizationName().equals(name);
        ViewUtils.visible(binding.organization, hasOrg);
        binding.organization.setText(user.getOrganizationName());

        boolean charity = user.getRole() == UserRole.CHARITY;
        ViewUtils.visible(binding.verificationRow, charity);
        ViewUtils.visible(binding.menuVerification, charity);
        if (charity) {
            VerificationStatus s = user.getVerificationStatus() == null ? VerificationStatus.UNVERIFIED : user.getVerificationStatus();
            ViewUtils.styleVerification(binding.verificationBadge, s);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
