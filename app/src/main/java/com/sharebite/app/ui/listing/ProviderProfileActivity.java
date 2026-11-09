package com.sharebite.app.ui.listing;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityOptionsCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.sharebite.app.R;
import com.sharebite.app.adapter.FoodCardAdapter;
import com.sharebite.app.data.model.FoodListing;
import com.sharebite.app.data.model.ListingStatus;
import com.sharebite.app.data.model.Provider;
import com.sharebite.app.data.model.User;
import com.sharebite.app.databinding.ActivityProviderProfileBinding;
import com.sharebite.app.domain.interfaces.ResultCallback;
import com.sharebite.app.domain.interfaces.Subscription;
import com.sharebite.app.domain.service.ListingItem;
import com.sharebite.app.ui.BaseActivity;
import com.sharebite.app.utils.ImageLoader;
import com.sharebite.app.utils.Monogram;
import com.sharebite.app.utils.ServiceLocator;
import com.sharebite.app.utils.ViewUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * A provider's public storefront: cover photo, logo, about text and the food they are sharing now.
 * Guests see it too; the profile document needs sign-in, so for guests the header falls back to
 * the name and logo copied onto the listings.
 */
public class ProviderProfileActivity extends BaseActivity {

    private static final String EXTRA_ID = "providerId";
    private static final String EXTRA_NAME = "providerName";
    private static final String EXTRA_LOGO = "providerLogo";

    public static Intent intent(Context context, String providerId, @Nullable String name, @Nullable String logoUrl) {
        return new Intent(context, ProviderProfileActivity.class)
                .putExtra(EXTRA_ID, providerId).putExtra(EXTRA_NAME, name).putExtra(EXTRA_LOGO, logoUrl);
    }

    public static class StorefrontViewModel extends ViewModel {
        private final MutableLiveData<User> provider = new MutableLiveData<>();
        private final MutableLiveData<List<FoodListing>> listings = new MutableLiveData<>();
        private Subscription sub = Subscription.NONE;
        private String id;

        void start(String providerId) {
            if (providerId.equals(id)) return;
            id = providerId;
            ServiceLocator s = ServiceLocator.get();
            if (s.session().isSignedIn()) {
                s.users().getById(providerId, new ResultCallback<User>() {
                    @Override public void onSuccess(User u) { provider.setValue(u); }
                    @Override public void onError(@NonNull Exception e) { /* header keeps the fallback */ }
                });
            }
            sub = s.food().observeAvailableFromProvider(providerId, new ResultCallback<List<FoodListing>>() {
                @Override public void onSuccess(List<FoodListing> result) { listings.setValue(result); }
                @Override public void onError(@NonNull Exception e) { listings.setValue(new ArrayList<>()); }
            });
        }

        LiveData<User> provider() { return provider; }
        LiveData<List<FoodListing>> listings() { return listings; }

        @Override
        protected void onCleared() {
            sub.cancel();
        }
    }

    private ActivityProviderProfileBinding binding;
    private String name;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String id = getIntent().getStringExtra(EXTRA_ID);
        if (id == null || !ServiceLocator.isFirebaseConfigured(this)) {
            finish();
            return;
        }
        binding = ActivityProviderProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot(), false, true);
        applySystemBarInsets(binding.toolbar, true, false);
        setupToolbar(binding.toolbar, true);

        name = getIntent().getStringExtra(EXTRA_NAME);
        renderHeader(name, null, getIntent().getStringExtra(EXTRA_LOGO), null, null);

        FoodCardAdapter adapter = new FoodCardAdapter(FoodCardAdapter.Style.FULL, (listing, image) -> {
            image.setTransitionName(ListingDetailActivity.SHARED_IMAGE);
            startActivity(ListingDetailActivity.intent(this, listing.getListingId()),
                    ActivityOptionsCompat.makeSceneTransitionAnimation(this, image, ListingDetailActivity.SHARED_IMAGE).toBundle());
        });
        binding.list.setLayoutManager(new LinearLayoutManager(this));
        binding.list.setAdapter(adapter);
        ServiceLocator.get().allergens().changes().observe(this, adapter::setAvoidedAllergens);

        StorefrontViewModel vm = new ViewModelProvider(this).get(StorefrontViewModel.class);
        vm.start(id);
        vm.provider().observe(this, u -> {
            if (u == null) return;
            Provider p = u instanceof Provider ? (Provider) u : null;
            renderHeader(u.displayName(), p == null ? null : getString(p.getProviderType().labelRes),
                    u.getProfileImageUrl(), p == null ? null : p.getCoverImageUrl(), p == null ? null : p.getBio());
        });
        vm.listings().observe(this, list -> {
            ViewUtils.visible(binding.progress, false);
            Date now = new Date();
            List<ListingItem> items = new ArrayList<>();
            FoodListing first = null;
            for (FoodListing l : list) {
                if (l.effectiveStatus(now) != ListingStatus.AVAILABLE) continue;
                if (first == null) first = l;
                items.add(new ListingItem(l, null, true));
            }
            adapter.submitList(items);
            binding.availableCount.setText(getResources().getQuantityString(R.plurals.storefront_available, items.size(), items.size()));
            ViewUtils.visible(binding.emptyText, items.isEmpty());
            // Guests: fill the header from the listings when the profile can't be read.
            if (vm.provider().getValue() == null && first != null) {
                renderHeader(first.getProviderName(), first.getProviderType() == null ? null : getString(first.getProviderType().labelRes),
                        first.getProviderLogoUrl(), null, null);
            }
        });
    }

    private void renderHeader(@Nullable String title, @Nullable String type, @Nullable String logoUrl,
                              @Nullable String coverUrl, @Nullable String bio) {
        if (title != null) {
            name = title;
            binding.name.setText(title);
        }
        ViewUtils.visible(binding.type, type != null);
        if (type != null) binding.type.setText(type);
        ImageLoader.loadLogo(binding.logo, logoUrl, new Monogram(this, name));
        if (coverUrl != null) ImageLoader.loadFood(binding.cover, coverUrl);
        boolean hasBio = bio != null && !bio.trim().isEmpty();
        ViewUtils.visible(binding.bio, hasBio);
        if (hasBio) binding.bio.setText(bio.trim());
    }
}
