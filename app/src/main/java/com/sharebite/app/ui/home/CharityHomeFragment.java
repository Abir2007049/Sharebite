package com.sharebite.app.ui.home;

import android.content.Intent;

import androidx.annotation.NonNull;

import com.sharebite.app.R;
import com.sharebite.app.data.model.Reservation;
import com.sharebite.app.data.model.ReservationStatus;
import com.sharebite.app.data.model.User;
import com.sharebite.app.data.model.VerificationStatus;
import com.sharebite.app.ui.profile.CharityVerificationActivity;
import com.sharebite.app.utils.ViewUtils;

import java.util.List;

/**
 * Home for charity organisations: verification status, collection stats, and charity donations
 * first. Discounted food is hidden — charities collect donations, not purchases.
 */
public class CharityHomeFragment extends ConsumerHomeFragment {

    @Override
    protected boolean showDiscountedSection() {
        return false;
    }

    @Override
    protected boolean showCharitySection() {
        return true;
    }

    @Override
    protected int subtitleRes() {
        return R.string.home_subtitle_charity;
    }

    @Override
    protected void onUserChanged(@NonNull User user) {
        VerificationStatus status = user.getVerificationStatus() == null ? VerificationStatus.UNVERIFIED : user.getVerificationStatus();
        ViewUtils.visible(binding.verificationBanner, status != VerificationStatus.VERIFIED);
        ViewUtils.styleVerification(binding.verificationBadge, status);
        int message;
        switch (status) {
            case PENDING: message = R.string.verification_banner_pending; break;
            case REJECTED: message = R.string.verification_banner_rejected; break;
            default: message = R.string.verification_banner_unverified; break;
        }
        binding.verificationMessage.setText(message);
        ViewUtils.visible(binding.verificationButton, status.canSubmit());
        binding.verificationButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), CharityVerificationActivity.class)));
    }

    @Override
    protected void onViewReady() {
        ViewUtils.visible(binding.statsRow, true);
        binding.statPending.statLabel.setText(R.string.stat_active_collections);
        binding.statCompleted.statLabel.setText(R.string.stat_completed_collections);
        mainViewModel().reservations().observe(getViewLifecycleOwner(), state -> renderStats(state.data));
    }

    private void renderStats(List<Reservation> list) {
        if (binding == null) return;
        int active = 0, done = 0;
        if (list != null) {
            for (Reservation r : list) {
                if (r.holdsQuantity()) active++;
                if (r.getStatus() == ReservationStatus.COLLECTED) done++;
            }
        }
        binding.statPending.statValue.setText(String.valueOf(active));
        binding.statCompleted.statValue.setText(String.valueOf(done));
    }
}
