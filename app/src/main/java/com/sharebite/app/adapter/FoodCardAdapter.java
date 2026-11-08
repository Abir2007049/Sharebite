package com.sharebite.app.adapter;

import android.content.Context;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.sharebite.app.R;
import com.sharebite.app.data.model.Allergen;
import com.sharebite.app.data.model.DistributionType;
import com.sharebite.app.data.model.FoodListing;
import com.sharebite.app.domain.service.AllergenMatcher;
import com.sharebite.app.domain.service.ListingItem;
import com.sharebite.app.utils.AllergenText;
import com.sharebite.app.utils.Formatters;
import com.sharebite.app.utils.ImageLoader;
import com.sharebite.app.utils.ViewUtils;

import java.util.Collections;
import java.util.Date;
import java.util.Objects;
import java.util.Set;

/** Food listing cards. {@link Style#FULL} for vertical lists, {@link Style#COMPACT} for carousels. */
public class FoodCardAdapter extends ListAdapter<ListingItem, FoodCardAdapter.Holder> {

    public enum Style { FULL, COMPACT }

    public interface OnListingClick {
        /** {@code image} is the card photo, used for the shared-element transition into the detail screen. */
        void onClick(@NonNull FoodListing listing, @NonNull View image);
    }

    private final Style style;
    private final OnListingClick listener;
    private Set<Allergen> avoidedAllergens = Collections.emptySet();

    public FoodCardAdapter(@NonNull Style style, @NonNull OnListingClick listener) {
        super(DIFF);
        this.style = style;
        this.listener = listener;
    }

    /** The user's allergen profile; cards declaring any of these get a warning. */
    public void setAvoidedAllergens(@NonNull Set<Allergen> avoided) {
        if (avoided.equals(avoidedAllergens)) return;
        avoidedAllergens = avoided;
        notifyItemRangeChanged(0, getItemCount());
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = style == Style.FULL ? R.layout.item_food_card : R.layout.item_food_card_compact;
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        ListingItem item = getItem(position);
        FoodListing l = item.listing;
        Context c = h.itemView.getContext();
        Date now = new Date();

        ImageLoader.loadFood(h.image, l.getImageUrl());
        h.title.setText(l.getTitle());
        ViewUtils.styleDistributionBadge(h.badge, l.getDistributionType());
        h.timeLeft.setText(Formatters.timeLeft(c, l.getPickupDeadline(), now));
        h.timeLeft.setBackgroundTintList(c.getColorStateList(Formatters.endingWithin(l.getPickupDeadline(), now, 60)
                ? R.color.error_red : R.color.scrim_dark));
        h.quantity.setText(c.getString(R.string.left_format, Formatters.quantity(c, l.getAvailableQuantity(), l.getUnit())));

        if (h.provider != null) {
            String category = l.getCategory() == null ? "" : c.getString(l.getCategory().labelRes);
            h.provider.setText(c.getString(R.string.provider_category_format, l.getProviderName(), category));
        }
        if (h.price != null) bindPrice(c, h, l);
        if (h.distance != null) {
            ViewUtils.visible(h.distance, item.distanceKm != null);
            if (item.distanceKm != null) h.distance.setText(Formatters.distance(c, item.distanceKm));
        }
        Set<Allergen> conflicts = AllergenMatcher.conflicts(l.getAllergens(), avoidedAllergens);
        String conflictText = conflicts.isEmpty() ? null
                : c.getString(R.string.allergen_conflict_card, AllergenText.join(c, conflicts));
        if (h.allergens != null) {
            boolean show = conflictText != null || l.hasDeclaredAllergens();
            ViewUtils.visible(h.allergens, show);
            if (show) {
                boolean conflict = conflictText != null;
                h.allergens.setText(conflict ? conflictText : c.getString(R.string.contains_format, l.getAllergens()));
                int fg = c.getColor(conflict ? R.color.error_red : R.color.warning_amber);
                h.allergens.setTextColor(fg);
                h.allergens.setBackgroundTintList(c.getColorStateList(conflict ? R.color.error_bg : R.color.warning_bg));
                androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(h.allergens,
                        android.content.res.ColorStateList.valueOf(fg));
            }
        }
        if (h.allergenAlert != null) {
            ViewUtils.visible(h.allergenAlert, conflictText != null);
            if (conflictText != null) h.allergenAlert.setText(conflictText);
        }
        if (h.eligibility != null) {
            boolean showNote = !item.reservableByUser && l.getDistributionType() == DistributionType.CHARITY;
            ViewUtils.visible(h.eligibility, showNote);
        }
        h.itemView.setOnClickListener(v -> listener.onClick(l, h.image));
        h.itemView.setContentDescription(l.getTitle() + ", " + c.getString(l.getDistributionType().labelRes)
                + ", " + h.quantity.getText());
    }

    private void bindPrice(Context c, Holder h, FoodListing l) {
        if (l.getDistributionType() == DistributionType.DISCOUNTED) {
            h.price.setText(Formatters.money(l.getDiscountedPrice()));
            h.originalPrice.setVisibility(View.VISIBLE);
            h.originalPrice.setText(Formatters.money(l.getOriginalPrice()));
            h.originalPrice.setPaintFlags(h.originalPrice.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            h.price.setText(l.getDistributionType() == DistributionType.FREE ? R.string.price_free : R.string.price_charity);
            h.originalPrice.setVisibility(View.GONE);
        }
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView title, badge, timeLeft, quantity;
        final TextView provider, price, originalPrice, distance, allergens, eligibility; // FULL only (nullable)
        final TextView allergenAlert; // COMPACT only (nullable)

        Holder(@NonNull View v) {
            super(v);
            image = v.findViewById(R.id.foodImage);
            title = v.findViewById(R.id.foodTitle);
            badge = v.findViewById(R.id.distributionBadge);
            timeLeft = v.findViewById(R.id.timeLeft);
            quantity = v.findViewById(R.id.quantity);
            provider = v.findViewById(R.id.providerName);
            price = v.findViewById(R.id.price);
            originalPrice = v.findViewById(R.id.originalPrice);
            distance = v.findViewById(R.id.distance);
            allergens = v.findViewById(R.id.allergenWarning);
            eligibility = v.findViewById(R.id.eligibilityNote);
            allergenAlert = v.findViewById(R.id.allergenAlert);
        }
    }

    private static final DiffUtil.ItemCallback<ListingItem> DIFF = new DiffUtil.ItemCallback<ListingItem>() {
        @Override
        public boolean areItemsTheSame(@NonNull ListingItem a, @NonNull ListingItem b) {
            return a.listing.getListingId().equals(b.listing.getListingId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull ListingItem a, @NonNull ListingItem b) {
            FoodListing x = a.listing, y = b.listing;
            return x.getAvailableQuantity() == y.getAvailableQuantity()
                    && Objects.equals(x.getTitle(), y.getTitle())
                    && Objects.equals(x.getImageUrl(), y.getImageUrl())
                    && Objects.equals(x.getStatus(), y.getStatus())
                    && Objects.equals(x.getPickupDeadline(), y.getPickupDeadline())
                    && x.getDiscountedPrice() == y.getDiscountedPrice()
                    && Objects.equals(a.distanceKm, b.distanceKm)
                    && a.reservableByUser == b.reservableByUser;
        }
    };
}
