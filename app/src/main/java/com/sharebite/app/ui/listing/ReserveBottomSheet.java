package com.sharebite.app.ui.listing;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.sharebite.app.R;
import com.sharebite.app.data.model.DistributionType;
import com.sharebite.app.data.model.FoodListing;
import com.sharebite.app.data.model.User;
import com.sharebite.app.databinding.SheetReserveBinding;
import com.sharebite.app.utils.Formatters;
import com.sharebite.app.utils.ViewUtils;

/**
 * Quantity selection and confirmation. The maximum shown here (role limit ∩ stock) is only a UI
 * hint — the reservation transaction re-checks stock against the latest server data.
 */
public class ReserveBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "reserve_sheet";
    private static final String STATE_QTY = "qty";

    /** Implemented by the hosting activity. */
    public interface Host {
        @Nullable FoodListing listingForReservation();

        @NonNull User reservingUser();

        void onReserveConfirmed(int quantity);
    }

    private SheetReserveBinding binding;
    private Host host;
    private int quantity = 1;

    public static ReserveBottomSheet newInstance() {
        return new ReserveBottomSheet();
    }

    @Override
    public int getTheme() {
        return R.style.ShareBite_BottomSheetDialog;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (!(context instanceof Host)) throw new IllegalStateException("Host activity must implement ReserveBottomSheet.Host");
        host = (Host) context;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = SheetReserveBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        FoodListing l = host.listingForReservation();
        if (l == null) {
            dismiss();
            return;
        }
        if (savedInstanceState != null) quantity = savedInstanceState.getInt(STATE_QTY, 1);
        int max = host.reservingUser().maxReservableQuantity(l);
        if (max <= 0) {
            dismiss();
            return;
        }
        quantity = Math.max(1, Math.min(quantity, max));

        binding.sheetTitle.setText(l.getTitle());
        binding.maxNote.setText(max < l.getAvailableQuantity()
                ? getString(R.string.max_per_reservation, Formatters.quantity(requireContext(), max, l.getUnit()))
                : getString(R.string.available_now, Formatters.quantity(requireContext(), max, l.getUnit())));
        binding.pickupNote.setText(getString(R.string.collect_by, Formatters.dateTime(requireContext(), l.getPickupDeadline()), l.getPickupAddress()));

        boolean allergens = l.hasDeclaredAllergens();
        ViewUtils.visible(binding.allergenCheck, allergens);
        if (allergens) binding.allergenCheck.setText(getString(R.string.allergen_acknowledge, l.getAllergens()));
        binding.allergenCheck.setOnCheckedChangeListener((b, checked) -> updateConfirm(allergens));

        binding.minus.setOnClickListener(v -> setQuantity(quantity - 1, max, l));
        binding.plus.setOnClickListener(v -> setQuantity(quantity + 1, max, l));
        binding.confirmButton.setOnClickListener(v -> {
            host.onReserveConfirmed(quantity);
            dismiss();
        });
        setQuantity(quantity, max, l);
        updateConfirm(allergens);
    }

    private void setQuantity(int value, int max, FoodListing l) {
        quantity = Math.max(1, Math.min(value, max));
        binding.quantityValue.setText(String.valueOf(quantity));
        binding.quantityUnit.setText(Formatters.quantity(requireContext(), quantity, l.getUnit()).replaceFirst("^\\d+\\s*", ""));
        binding.minus.setEnabled(quantity > 1);
        binding.plus.setEnabled(quantity < max);
        if (l.getDistributionType() == DistributionType.DISCOUNTED) {
            binding.total.setText(getString(R.string.total_pay_at_pickup, Formatters.money(quantity * l.unitPrice())));
        } else {
            binding.total.setText(l.getDistributionType() == DistributionType.FREE ? R.string.total_free : R.string.total_charity);
        }
    }

    private void updateConfirm(boolean needsAllergenAck) {
        binding.confirmButton.setEnabled(!needsAllergenAck || binding.allergenCheck.isChecked());
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_QTY, quantity);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
