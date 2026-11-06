package com.sharebite.app.ui.impact;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.sharebite.app.R;
import com.sharebite.app.data.model.FoodCategory;
import com.sharebite.app.data.model.User;
import com.sharebite.app.data.model.UserRole;
import com.sharebite.app.databinding.FragmentImpactBinding;
import com.sharebite.app.domain.service.ImpactReport;
import com.sharebite.app.ui.BaseTabFragment;
import com.sharebite.app.ui.home.MainViewModel;
import com.sharebite.app.ui.widget.BarChartView;
import com.sharebite.app.utils.ViewUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Impact dashboard. All figures come from COLLECTED reservations only — food that actually
 * changed hands — and no environmental estimates are shown (see ImpactReport).
 */
public class ImpactFragment extends BaseTabFragment {

    private FragmentImpactBinding binding;
    private ImpactViewModel viewModel;
    private UserRole role;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentImpactBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        User user = currentUser();
        if (user == null) return;
        role = user.getRole();
        viewModel = new ViewModelProvider(this).get(ImpactViewModel.class);
        boolean provider = role == UserRole.PROVIDER;
        bindHeader(binding.header, getString(R.string.impact_title),
                getString(provider ? R.string.impact_subtitle_provider : R.string.impact_subtitle_consumer));

        binding.heroLabel.setText(provider ? R.string.impact_hero_provider : R.string.impact_hero_consumer);
        binding.statCompleted.statLabel.setText(provider ? R.string.stat_completed_pickups : R.string.stat_completed_collections);
        binding.statCharity.statLabel.setText(provider ? R.string.stat_charity_donations : R.string.stat_charity_collections);
        binding.statPartners.statLabel.setText(provider ? R.string.stat_people_helped : R.string.stat_providers_supported);
        binding.statActive.statLabel.setText(provider ? R.string.stat_active_listings : R.string.stat_active_collections);
        ViewUtils.visible(binding.statCharityContainer, role != UserRole.RECEIVER);

        MainViewModel main = mainViewModel();
        main.reservations().observe(getViewLifecycleOwner(), s -> recalc());
        main.myListings().observe(getViewLifecycleOwner(), s -> recalc());
        viewModel.report().observe(getViewLifecycleOwner(), this::render);
    }

    private void recalc() {
        MainViewModel main = mainViewModel();
        ViewUtils.visible(binding.progress, main.reservations().getValue() == null || main.reservations().getValue().data == null);
        viewModel.recalculate(role,
                main.reservations().getValue() == null ? null : main.reservations().getValue().data,
                main.myListings().getValue() == null ? null : main.myListings().getValue().data);
    }

    private void render(ImpactReport r) {
        ViewUtils.visible(binding.progress, false);
        binding.heroValue.setText(String.valueOf(r.totalQuantityShared));
        binding.statCompleted.statValue.setText(String.valueOf(r.completedCollections));
        binding.statCharity.statValue.setText(String.valueOf(r.charityCollections));
        binding.statPartners.statValue.setText(String.valueOf(r.distinctPartners));
        binding.statActive.statValue.setText(String.valueOf(role == UserRole.PROVIDER
                ? r.activeListings : r.pendingReservations + r.confirmedReservations));

        List<BarChartView.Bar> bars = new ArrayList<>();
        for (ImpactReport.MonthBucket m : r.monthlyActivity) bars.add(new BarChartView.Bar(m.label, m.quantity));
        binding.monthlyChart.setBars(bars);

        binding.categoryRows.removeAllViews();
        int max = 1;
        for (int v : r.quantityByCategory.values()) max = Math.max(max, v);
        for (Map.Entry<FoodCategory, Integer> e : r.quantityByCategory.entrySet()) {
            View row = getLayoutInflater().inflate(R.layout.item_category_bar, binding.categoryRows, false);
            ((TextView) row.findViewById(R.id.categoryName)).setText(e.getKey().labelRes);
            ((TextView) row.findViewById(R.id.categoryValue)).setText(String.valueOf(e.getValue()));
            LinearProgressIndicator bar = row.findViewById(R.id.categoryBar);
            bar.setMax(max);
            bar.setProgressCompat(e.getValue(), false);
            binding.categoryRows.addView(row);
        }
        ViewUtils.visible(binding.categoryEmpty, r.quantityByCategory.isEmpty());
        ViewUtils.visible(binding.emptyBanner, r.isEmpty());
        binding.emptyBanner.setText(role == UserRole.PROVIDER ? R.string.impact_empty_provider : R.string.impact_empty_consumer);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
