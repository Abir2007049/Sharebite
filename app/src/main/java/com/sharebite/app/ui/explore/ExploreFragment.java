package com.sharebite.app.ui.explore;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.sharebite.app.R;
import com.sharebite.app.adapter.FoodCardAdapter;
import com.sharebite.app.data.model.DistributionType;
import com.sharebite.app.data.model.FoodCategory;
import com.sharebite.app.data.model.FoodListing;
import com.sharebite.app.databinding.FragmentExploreBinding;
import com.sharebite.app.domain.service.ListingItem;
import com.sharebite.app.domain.service.ListingQuery;
import com.sharebite.app.ui.BaseTabFragment;
import com.sharebite.app.ui.LocationRequester;
import com.sharebite.app.ui.home.MainViewModel;
import com.sharebite.app.utils.UiState;
import com.sharebite.app.utils.ViewUtils;

import java.util.List;

/** Search, filter and sort all currently reservable listings. */
public class ExploreFragment extends BaseTabFragment {

    private FragmentExploreBinding binding;
    private ExploreViewModel viewModel;
    private FoodCardAdapter adapter;
    private LocationRequester locationRequester;
    private boolean syncingUi;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        locationRequester = new LocationRequester(this, new LocationRequester.Callback() {
            @Override
            public void onLocation(@NonNull double[] latLng) {
                mainViewModel().setUserLocation(latLng);
                viewModel.setQuery(viewModel.currentQuery().withSort(ListingQuery.Sort.NEAREST));
            }

            @Override
            public void onUnavailable(@NonNull String reason) {
                showMessage(reason);
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentExploreBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(ExploreViewModel.class);
        bindHeader(binding.header, getString(R.string.explore_title), getString(R.string.explore_subtitle));

        adapter = new FoodCardAdapter(FoodCardAdapter.Style.FULL, this::openListing);
        binding.results.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.results.setAdapter(adapter);
        buildChips();

        binding.searchInput.getEditText().addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override
            public void afterTextChanged(Editable s) {
                if (!syncingUi) viewModel.setQuery(viewModel.currentQuery().withSearch(s.toString()));
            }
        });
        binding.sortButton.setOnClickListener(this::showSortMenu);
        binding.mapButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), MapActivity.class)));
        binding.emptyState.emptyAction.setOnClickListener(v -> viewModel.setQuery(ListingQuery.defaults()));

        MainViewModel main = mainViewModel();
        main.availableListings().observe(getViewLifecycleOwner(), this::onListings);
        main.userLocation().observe(getViewLifecycleOwner(), loc -> pushInputs());
        main.exploreRequests().observe(getViewLifecycleOwner(), event -> {
            ListingQuery requested = event.consume();
            if (requested == null) return;
            if (requested.sort == ListingQuery.Sort.NEAREST && main.userLocation().getValue() == null) {
                locationRequester.request();
            }
            viewModel.setQuery(requested);
        });
        viewModel.query().observe(getViewLifecycleOwner(), this::syncUi);
        viewModel.results().observe(getViewLifecycleOwner(), this::renderResults);
    }

    private void onListings(UiState<List<FoodListing>> state) {
        ViewUtils.visible(binding.progress, state.isLoading() && state.data == null);
        if (state.message != null) showMessage(state.message);
        pushInputs();
    }

    private void pushInputs() {
        UiState<List<FoodListing>> state = mainViewModel().availableListings().getValue();
        viewModel.setInputs(state == null ? null : state.data, currentUser(), mainViewModel().userLocation().getValue());
    }

    private void renderResults(List<ListingItem> items) {
        adapter.submitList(items);
        binding.resultCount.setText(getResources().getQuantityString(R.plurals.results_count, items.size(), items.size()));
        boolean empty = items.isEmpty();
        ViewUtils.visible(binding.emptyState.getRoot(), empty);
        if (empty) {
            boolean filtered = !viewModel.currentQuery().searchText.isEmpty()
                    || viewModel.currentQuery().category != null || viewModel.currentQuery().distributionType != null;
            binding.emptyState.emptyTitle.setText(filtered ? R.string.empty_explore_filtered_title : R.string.empty_explore_title);
            binding.emptyState.emptyMessage.setText(filtered ? R.string.empty_explore_filtered_message : R.string.empty_explore_message);
            ViewUtils.visible(binding.emptyState.emptyAction, filtered);
            binding.emptyState.emptyAction.setText(R.string.clear_filters);
        }
    }

    private void buildChips() {
        ChipGroup categories = binding.categoryChips;
        categories.addView(filterChip(R.string.category_all, View.NO_ID));
        for (FoodCategory c : FoodCategory.values()) categories.addView(filterChip(c.labelRes, c.ordinal()));
        categories.setOnCheckedStateChangeListener((group, ids) -> {
            if (syncingUi || ids.isEmpty()) return;
            Object tag = group.findViewById(ids.get(0)).getTag();
            viewModel.setQuery(viewModel.currentQuery().withCategory(tag == null ? null : FoodCategory.values()[(int) tag]));
        });

        ChipGroup types = binding.distributionChips;
        types.addView(filterChip(R.string.all_types, View.NO_ID));
        for (DistributionType t : DistributionType.values()) types.addView(filterChip(t.labelRes, t.ordinal()));
        types.setOnCheckedStateChangeListener((group, ids) -> {
            if (syncingUi || ids.isEmpty()) return;
            Object tag = group.findViewById(ids.get(0)).getTag();
            viewModel.setQuery(viewModel.currentQuery().withDistribution(tag == null ? null : DistributionType.values()[(int) tag]));
        });
    }

    private Chip filterChip(int label, int ordinal) {
        Chip chip = (Chip) getLayoutInflater().inflate(R.layout.view_chip_filter, binding.categoryChips, false);
        chip.setId(View.generateViewId());
        chip.setText(label);
        chip.setTag(ordinal == View.NO_ID ? null : ordinal);
        return chip;
    }

    /** Reflects the query in the widgets (needed when Home opens Explore with a preset filter). */
    private void syncUi(ListingQuery q) {
        syncingUi = true;
        String current = ViewUtils.text(binding.searchInput);
        if (!current.equals(q.searchText)) ViewUtils.setText(binding.searchInput, q.searchText);
        checkChip(binding.categoryChips, q.category == null ? null : q.category.ordinal());
        checkChip(binding.distributionChips, q.distributionType == null ? null : q.distributionType.ordinal());
        int sortLabel = q.sort == ListingQuery.Sort.NEAREST ? R.string.sort_nearest
                : q.sort == ListingQuery.Sort.ENDING_SOON ? R.string.sort_ending_soon : R.string.sort_newest;
        binding.sortButton.setText(sortLabel);
        syncingUi = false;
    }

    private void checkChip(ChipGroup group, @Nullable Integer ordinal) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            Object tag = child.getTag();
            boolean match = ordinal == null ? tag == null : ordinal.equals(tag);
            if (match && !((Chip) child).isChecked()) group.check(child.getId());
        }
    }

    private void showSortMenu(View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 0, 0, R.string.sort_newest);
        menu.getMenu().add(0, 1, 1, R.string.sort_ending_soon);
        menu.getMenu().add(0, 2, 2, R.string.sort_nearest);
        menu.setOnMenuItemClickListener(item -> {
            ListingQuery.Sort sort = item.getItemId() == 1 ? ListingQuery.Sort.ENDING_SOON
                    : item.getItemId() == 2 ? ListingQuery.Sort.NEAREST : ListingQuery.Sort.NEWEST;
            if (sort == ListingQuery.Sort.NEAREST && mainViewModel().userLocation().getValue() == null) {
                // Distance needs a real location; ask, then apply the sort once we have it.
                locationRequester.request();
                return true;
            }
            viewModel.setQuery(viewModel.currentQuery().withSort(sort));
            return true;
        });
        menu.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
