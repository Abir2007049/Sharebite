package com.sharebite.app.ui.listing;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.sharebite.app.R;
import com.sharebite.app.adapter.MyListingAdapter;
import com.sharebite.app.data.model.FoodListing;
import com.sharebite.app.data.model.ListingStatus;
import com.sharebite.app.databinding.FragmentMyListingsBinding;
import com.sharebite.app.ui.BaseTabFragment;
import com.sharebite.app.utils.UiState;
import com.sharebite.app.utils.ViewUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/** Provider's listings, split into active (open, before deadline) and past. */
public class MyListingsFragment extends BaseTabFragment {

    private FragmentMyListingsBinding binding;
    private MyListingAdapter adapter;
    private boolean showActive = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentMyListingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        bindHeader(binding.header, getString(R.string.my_listings_title), getString(R.string.my_listings_subtitle));
        adapter = new MyListingAdapter(this::openListing);
        binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.list.setAdapter(adapter);
        binding.addButton.setOnClickListener(v -> startActivity(ListingEditorActivity.createIntent(requireContext())));
        binding.list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy > 8) binding.addButton.shrink();
                else if (dy < -8) binding.addButton.extend();
            }
        });
        binding.filterToggle.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked) return;
            showActive = id == R.id.filterActive;
            render(mainViewModel().myListings().getValue());
        });
        binding.emptyState.emptyAction.setOnClickListener(v -> startActivity(ListingEditorActivity.createIntent(requireContext())));
        mainViewModel().myListings().observe(getViewLifecycleOwner(), this::render);
    }

    private void render(@Nullable UiState<List<FoodListing>> state) {
        if (state == null || binding == null) return;
        ViewUtils.visible(binding.progress, state.isLoading() && state.data == null);
        if (state.message != null) showMessage(state.message);
        if (state.data == null) return;

        Date now = new Date();
        List<FoodListing> shown = new ArrayList<>();
        for (FoodListing l : state.data) {
            ListingStatus s = l.effectiveStatus(now);
            boolean active = s == ListingStatus.AVAILABLE || s == ListingStatus.FULLY_RESERVED;
            if (active == showActive) shown.add(l);
        }
        Collections.sort(shown, (a, b) -> Long.compare(millis(b.getCreatedAt()), millis(a.getCreatedAt())));
        adapter.submitList(shown);

        boolean empty = shown.isEmpty();
        ViewUtils.visible(binding.emptyState.getRoot(), empty);
        if (empty) {
            binding.emptyState.emptyTitle.setText(showActive ? R.string.empty_active_listings_title : R.string.empty_past_listings_title);
            binding.emptyState.emptyMessage.setText(showActive ? R.string.empty_active_listings_message : R.string.empty_past_listings_message);
            ViewUtils.visible(binding.emptyState.emptyAction, showActive);
            binding.emptyState.emptyAction.setText(R.string.share_food);
        }
    }

    private static long millis(Timestamp t) {
        return t == null ? Long.MAX_VALUE : t.toDate().getTime();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
