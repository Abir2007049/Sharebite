package com.sharebite.app.ui.listing;

import android.content.ContentResolver;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.google.firebase.Timestamp;
import com.sharebite.app.data.model.DistributionType;
import com.sharebite.app.data.model.FoodCategory;
import com.sharebite.app.data.model.FoodListing;
import com.sharebite.app.data.model.Provider;
import com.sharebite.app.data.model.QuantityUnit;
import com.sharebite.app.data.model.User;
import com.sharebite.app.domain.interfaces.FoodRepository;
import com.sharebite.app.domain.interfaces.ResultCallback;
import com.sharebite.app.domain.service.ListingDraft;
import com.sharebite.app.domain.service.ListingValidator;
import com.sharebite.app.utils.ErrorMessages;
import com.sharebite.app.utils.Event;
import com.sharebite.app.utils.ServiceLocator;
import com.sharebite.app.workers.BackgroundTaskRunner;
import com.sharebite.app.workers.ImageCompressionTask;

import java.util.Date;

/**
 * State for creating/editing a listing. Non-text selections (dropdowns, dates, photo, map pin)
 * live in {@link SavedStateHandle} so they survive rotation and process death; text fields keep
 * their own state. Saving compresses the photo on a background thread before uploading.
 */
public class ListingEditorViewModel extends ViewModel {

    private static final String K_CATEGORY = "category", K_UNIT = "unit", K_TYPE = "type",
            K_PREP = "prep", K_START = "start", K_DEADLINE = "deadline", K_IMAGE = "image",
            K_LAT = "lat", K_LNG = "lng", K_LOADED = "loaded";

    private final SavedStateHandle state;
    private final FoodRepository food = ServiceLocator.get().food();
    private final BackgroundTaskRunner runner = new BackgroundTaskRunner("listing-editor", 1);

    private final MutableLiveData<FoodListing> original = new MutableLiveData<>();
    private final MutableLiveData<Boolean> saving = new MutableLiveData<>(false);
    private final MutableLiveData<Event<String>> error = new MutableLiveData<>();
    private final MutableLiveData<Event<FoodListing>> saved = new MutableLiveData<>();
    private final MutableLiveData<ListingValidator.Result> validation = new MutableLiveData<>();

    public ListingEditorViewModel(@NonNull SavedStateHandle state) {
        this.state = state;
    }

    public LiveData<FoodListing> original() { return original; }
    public LiveData<Boolean> saving() { return saving; }
    public LiveData<Event<String>> error() { return error; }
    public LiveData<Event<FoodListing>> saved() { return saved; }
    public LiveData<ListingValidator.Result> validation() { return validation; }

    public LiveData<FoodCategory> category() { return state.getLiveData(K_CATEGORY); }
    public LiveData<QuantityUnit> unit() { return state.getLiveData(K_UNIT, QuantityUnit.PORTIONS); }
    public LiveData<DistributionType> distribution() { return state.getLiveData(K_TYPE, DistributionType.FREE); }
    public LiveData<Date> preparationTime() { return state.getLiveData(K_PREP); }
    public LiveData<Date> pickupStart() { return state.getLiveData(K_START); }
    public LiveData<Date> pickupDeadline() { return state.getLiveData(K_DEADLINE); }
    public LiveData<Uri> imageUri() { return state.getLiveData(K_IMAGE); }
    public LiveData<Double> latitude() { return state.getLiveData(K_LAT); }

    public void setCategory(FoodCategory c) { state.set(K_CATEGORY, c); }
    public void setUnit(QuantityUnit u) { state.set(K_UNIT, u); }
    public void setDistribution(DistributionType t) { state.set(K_TYPE, t); }
    public void setImage(Uri uri) { state.set(K_IMAGE, uri); }

    public void setTime(String key, Date value) {
        state.set(key, value);
    }

    public void setLocation(@Nullable Double lat, @Nullable Double lng) {
        state.set(K_LNG, lng);
        state.set(K_LAT, lat); // Observed; set last.
    }

    @Nullable public Double lat() { return state.get(K_LAT); }
    @Nullable public Double lng() { return state.get(K_LNG); }

    public static String keyPrep() { return K_PREP; }
    public static String keyStart() { return K_START; }
    public static String keyDeadline() { return K_DEADLINE; }

    public boolean isEditing() {
        return original.getValue() != null;
    }

    /** Edit mode: loads the listing once and seeds selections (text fields are seeded by the Activity). */
    public void load(@NonNull String listingId, @NonNull ResultCallback<FoodListing> onLoaded) {
        if (original.getValue() != null) return;
        food.getById(listingId, new ResultCallback<FoodListing>() {
            @Override
            public void onSuccess(FoodListing l) {
                if (l == null) {
                    error.setValue(new Event<>("This listing no longer exists."));
                    return;
                }
                boolean firstLoad = !Boolean.TRUE.equals(state.get(K_LOADED));
                if (firstLoad) {
                    state.set(K_LOADED, true);
                    state.set(K_CATEGORY, l.getCategory());
                    state.set(K_UNIT, l.getUnit());
                    state.set(K_TYPE, l.getDistributionType());
                    state.set(K_PREP, toDate(l.getPreparationTime()));
                    state.set(K_START, toDate(l.getPickupStartTime()));
                    state.set(K_DEADLINE, toDate(l.getPickupDeadline()));
                    setLocation(l.getLatitude(), l.getLongitude());
                }
                original.setValue(l);
                if (firstLoad) onLoaded.onSuccess(l);
            }

            @Override
            public void onError(@NonNull Exception e) {
                error.setValue(new Event<>(ErrorMessages.from(e)));
            }
        });
    }

    /** Fills in the selections the form doesn't own (dates, dropdowns, pin) and validates. */
    public boolean validate(@NonNull ListingDraft draft) {
        draft.category = category().getValue();
        draft.unit = unit().getValue();
        draft.distributionType = distribution().getValue();
        draft.preparationTime = preparationTime().getValue();
        draft.pickupStartTime = pickupStart().getValue();
        draft.pickupDeadline = pickupDeadline().getValue();
        draft.latitude = lat();
        draft.longitude = lng();
        FoodListing o = original.getValue();
        draft.reservedQuantity = o == null ? 0 : o.reservedQuantity();
        ListingValidator.Result result = ListingValidator.validate(draft, new Date());
        validation.setValue(result);
        return result.isValid();
    }

    public void save(@NonNull ListingDraft draft, @NonNull User provider, @NonNull ContentResolver resolver) {
        if (Boolean.TRUE.equals(saving.getValue()) || !validate(draft)) return;
        saving.setValue(true);
        FoodListing listing = toListing(draft, provider);
        Uri image = imageUri().getValue();
        if (image == null) {
            persist(listing, null);
            return;
        }
        runner.submit(new ImageCompressionTask(resolver, image), new ResultCallback<byte[]>() {
            @Override
            public void onSuccess(byte[] bytes) {
                persist(listing, bytes);
            }

            @Override
            public void onError(@NonNull Exception e) {
                saving.setValue(false);
                error.setValue(new Event<>("Couldn't process the photo: " + ErrorMessages.from(e)));
            }
        });
    }

    private void persist(FoodListing listing, @Nullable byte[] image) {
        ResultCallback<FoodListing> done = new ResultCallback<FoodListing>() {
            @Override
            public void onSuccess(FoodListing result) {
                saving.setValue(false);
                saved.setValue(new Event<>(result));
            }

            @Override
            public void onError(@NonNull Exception e) {
                saving.setValue(false);
                error.setValue(new Event<>(ErrorMessages.from(e)));
            }
        };
        if (isEditing()) food.updateListing(listing, image, done);
        else food.createListing(listing, image, done);
    }

    private FoodListing toListing(ListingDraft d, User provider) {
        FoodListing o = original.getValue();
        FoodListing l = new FoodListing();
        l.setListingId(o != null ? o.getListingId() : food.newListingId());
        l.setProviderId(provider.getUid());
        l.setProviderName(provider.displayName());
        if (provider instanceof Provider) l.setProviderType(((Provider) provider).getProviderType());
        l.setTitle(d.title.trim());
        l.setDescription(d.description.trim());
        l.setCategory(d.category);
        l.setImageUrl(o != null ? o.getImageUrl() : null);
        l.setTotalQuantity(Integer.parseInt(d.quantityText.trim()));
        l.setUnit(d.unit);
        l.setDistributionType(d.distributionType);
        if (d.distributionType == DistributionType.DISCOUNTED) {
            l.setOriginalPrice(ListingValidator.roundMoney(Double.parseDouble(d.originalPriceText.trim().replace(',', '.'))));
            l.setDiscountedPrice(ListingValidator.roundMoney(Double.parseDouble(d.discountedPriceText.trim().replace(',', '.'))));
        }
        l.setPreparationTime(new Timestamp(d.preparationTime));
        l.setPickupStartTime(new Timestamp(d.pickupStartTime));
        l.setPickupDeadline(new Timestamp(d.pickupDeadline));
        l.setPickupAddress(d.pickupAddress.trim());
        l.setLatitude(d.latitude);
        l.setLongitude(d.longitude);
        l.setStorageInformation(d.storageInformation.trim());
        l.setAllergens(d.allergens.trim());
        l.setFoodSafetyNotes(d.foodSafetyNotes == null ? null : d.foodSafetyNotes.trim());
        l.setSafetyDeclared(d.safetyDeclared);
        return l;
    }

    private static Date toDate(@Nullable Timestamp t) {
        return t == null ? null : t.toDate();
    }

    @Override
    protected void onCleared() {
        runner.shutdown();
    }
}
