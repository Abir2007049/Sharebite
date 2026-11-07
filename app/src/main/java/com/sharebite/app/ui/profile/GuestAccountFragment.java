package com.sharebite.app.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.sharebite.app.R;
import com.sharebite.app.databinding.FragmentGuestAccountBinding;
import com.sharebite.app.ui.auth.LoginActivity;
import com.sharebite.app.ui.auth.RegisterActivity;

/** The "Account" tab while browsing without an account: explains why to sign up and links to it. */
public class GuestAccountFragment extends Fragment {

    private FragmentGuestAccountBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentGuestAccountBinding.inflate(inflater, container, false);
        binding.registerButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), RegisterActivity.class)));
        binding.loginButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), LoginActivity.class)));
        binding.safetyLink.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.food_safety_guidelines)
                .setMessage(R.string.food_safety_guidelines_body)
                .setPositiveButton(R.string.done, null)
                .show());
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
