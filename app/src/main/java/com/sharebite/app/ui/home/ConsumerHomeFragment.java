package com.sharebite.app.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.chip.Chip;
import com.sharebite.app.R;
import com.sharebite.app.adapter.FoodCardAdapter;
import com.sharebite.app.data.model.DistributionType;
import com.sharebite.app.data.model.FoodCategory;
import com.sharebite.app.data.model.FoodListing;
import com.sharebite.app.data.model.User;
import com.sharebite.app.databinding.FragmentConsumerHomeBinding;
import com.sharebite.app.databinding.ViewListingSectionBinding;
import com.sharebite.app.domain.service.ListingItem;
import com.sharebite.app.domain.service.ListingQuery;
import com.sharebite.app.ui.BaseTabFragment;
import com.sharebite.app.ui.LocationRequester;
import com.sharebite.app.ui.explore.MapActivity;
import com.sharebite.app.utils.UiState;
import com.sharebite.app.utils.ViewUtils;

import java.util.List;

/**
 * Home for people collecting food. Subclasses decide which sections matter for their role
 * (template method): receivers see free/discounted food, charities see charity donations first.
 */
public abstract class ConsumerHomeFragment extends BaseTabFragment {

    protected FragmentConsumerHomeBinding binding;
    private HomeViewModel homeViewModel;
    private LocationRequester locationRequester;

    private final FoodCardAdapter nearbyAdapter = new FoodCardAdapter(FoodCardAdapter.Style.COMPACT, this::openListing);
    private final FoodCardAdapter recentAdapter = new FoodCardAdapter(FoodCardAdapter.Style.COMPACT, this::openListing);
    private final FoodCardAdapter freeAdapter = new FoodCardAdapter(FoodCardAdapter.Style.COMPACT, this::openListing);
    private final FoodCardAdapter discountedAdapter = new FoodCardAdapter(FoodCardAdapter.Style.COMPACT, this::openListing);
    private final FoodCardAdapter charityAdapter = new FoodCardAdapter(FoodCardAdapter.Style.COMPACT, this::openListing);

    /** Whether the discounted-food section is relevant for this role. */
    protected abstract boolean showDiscountedSection();

    /** Whether charity donations are shown (first) on this home screen. */
    protected abstract boolean showCharitySection();

    @StringRes
    protected abstract int subtitleRes();

    /** Called whenever the profile changes (e.g. to refresh a verification banner). */
    protected void onUserChanged(@NonNull User user) {
    }

    /** Called once after the view is set up; register role-specific observers here. */
    protected void onViewReady() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        locationRequester = new LocationRequester(this, new LocationRequester.Callback() {
            @Override
            public void onLocation(@NonNull double[] latLng) {
                mainViewModel().setUserLocation(latLng);
            }

            @Override
            public void onUnavailable(@NonNull String reason) {
                if (binding != null) binding.locationMessage.setText(reason);
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentConsumerHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        User user = currentUser();
        String firstName = user == null ? "" : user.displayName().split(" ")[0];
        bindHeader(binding.header, getString(R.string.greeting, firstName), getString(subtitleRes()));

        setupSection(binding.sectionNearby, R.string.section_nearby, nearbyAdapter,
                ListingQuery.defaults().withSort(ListingQuery.Sort.NEAREST));
        setupSection(binding.sectionRecent, R.string.section_recent, recentAdapter, ListingQuery.defaults());
        setupSection(binding.sectionFree, R.string.section_free, freeAdapter,
                ListingQuery.defaults().withDistribution(DistributionType.FREE));
        setupSection(binding.sectionDiscounted, R.string.section_discounted, discountedAdapter,
                ListingQuery.defaults().withDistribution(DistributionType.DISCOUNTED));
        setupSection(binding.sectionCharity, R.string.section_charity, charityAdapter,
                ListingQuery.defaults().withDistribution(DistributionType.CHARITY));
        setupCategoryChips();

        binding.searchBar.setOnClickListener(v -> openExplore(ListingQuery.defaults()));
        binding.mapButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), MapActivity.class)));
        binding.locationButton.setOnClickListener(v -> locationRequester.request());

        MainViewModel main = mainViewModel();
        main.availableListings().observe(getViewLifecycleOwner(), this::onListings);
        main.userLocation().observe(getViewLifecycleOwner(), loc -> {
            ViewUtils.visible(binding.locationCard, loc == null);
            rebuild();
        });
        homeViewModel.feed().observe(getViewLifecycleOwner(), this::renderFeed);
        com.sharebite.app.utils.ServiceLocator.get().allergens().changes().observe(getViewLifecycleOwner(), avoided -> {
            for (FoodCardAdapter a : new FoodCardAdapter[]{nearbyAdapter, recentAdapter, freeAdapter, discountedAdapter, charityAdapter}) {
                a.setAvoidedAllergens(avoided);
            }
        });
        com.sharebite.app.utils.ServiceLocator.get().session().currentUser().observe(getViewLifecycleOwner(), u -> {
            if (u != null) {
                onUserChanged(u);
                rebuild();
            }
        });

        if (main.userLocation().getValue() == null) locationRequester.fetchIfPermitted();
        onViewReady();
    }

    private void onListings(UiState<List<FoodListing>> state) {
        ViewUtils.visible(binding.progress, state.isLoading() && state.data == null);
        ViewUtils.visible(binding.errorText, state.message != null);
        if (state.message != null) binding.errorText.setText(state.message);
        rebuild();
    }

    private void rebuild() {
        UiState<List<FoodListing>> state = mainViewModel().availableListings().getValue();
        if (state == null || state.data == null) return;
        homeViewModel.rebuild(state.data, currentUser(), mainViewModel().userLocation().getValue());
    }

    private void renderFeed(HomeFeed feed) {
        binding.heroTitle.setText(getResources().getQuantityString(R.plurals.listings_available, feed.totalAvailable, feed.totalAvailable));
        binding.heroSubtitle.setText(feed.endingSoon > 0
                ? getResources().getQuantityString(R.plurals.ending_soon, feed.endingSoon, feed.endingSoon)
                : getString(R.string.hero_subtitle_default));

        renderSection(binding.sectionNearby, nearbyAdapter, feed.nearby, feed.hasLocation, R.string.empty_nearby);
        renderSection(binding.sectionRecent, recentAdapter, feed.recent, true, R.string.empty_recent);
        renderSection(binding.sectionFree, freeAdapter, feed.free, true, R.string.empty_free);
        renderSection(binding.sectionDiscounted, discountedAdapter, feed.discounted, showDiscountedSection(), R.string.empty_discounted);
        renderSection(binding.sectionCharity, charityAdapter, feed.charity, showCharitySection(), R.string.empty_charity);
    }

    private void renderSection(ViewListingSectionBinding s, FoodCardAdapter adapter, List<ListingItem> items,
                               boolean visible, @StringRes int emptyText) {
        ViewUtils.visible(s.getRoot(), visible);
        if (!visible) return;
        adapter.submitList(items);
        ViewUtils.visible(s.sectionList, !items.isEmpty());
        ViewUtils.visible(s.sectionEmpty, items.isEmpty());
        ViewUtils.visible(s.seeAll, !items.isEmpty());
        s.sectionEmpty.setText(emptyText);
    }

    private void setupSection(ViewListingSectionBinding s, @StringRes int title, FoodCardAdapter adapter, ListingQuery query) {
        s.sectionTitle.setText(title);
        s.sectionList.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        s.sectionList.setAdapter(adapter);
        s.seeAll.setOnClickListener(v -> openExplore(query));
    }

    private void setupCategoryChips() {
        binding.categoryChips.removeAllViews();
        for (FoodCategory category : FoodCategory.values()) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.view_chip_action, binding.categoryChips, false);
            chip.setText(category.labelRes);
            chip.setOnClickListener(v -> openExplore(ListingQuery.defaults().withCategory(category)));
            binding.categoryChips.addView(chip);
        }
    }

    private void openExplore(ListingQuery query) {
        mainViewModel().requestExplore(query);
        selectTab(R.id.nav_explore);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
