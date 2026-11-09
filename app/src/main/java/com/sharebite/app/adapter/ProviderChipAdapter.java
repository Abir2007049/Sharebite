package com.sharebite.app.adapter;

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
import com.sharebite.app.data.model.FoodListing;
import com.sharebite.app.utils.ImageLoader;
import com.sharebite.app.utils.Monogram;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** "Places sharing food now": one card per provider with their logo and how many listings are open. */
public class ProviderChipAdapter extends ListAdapter<ProviderChipAdapter.Place, ProviderChipAdapter.Holder> {

    /** A provider summarised from their open listings. */
    public static final class Place {
        public final String providerId, name, logoUrl, coverUrl;
        public final int typeLabel;
        public final int listings;

        Place(String providerId, String name, String logoUrl, String coverUrl, int typeLabel, int listings) {
            this.providerId = providerId;
            this.name = name;
            this.logoUrl = logoUrl;
            this.coverUrl = coverUrl;
            this.typeLabel = typeLabel;
            this.listings = listings;
        }
    }

    public interface Listener {
        void onOpen(@NonNull Place place);
    }

    /** Groups listings by provider, busiest first. The newest listing photo serves as the card image. */
    @NonNull
    public static List<Place> summarise(@NonNull List<FoodListing> listings) {
        Map<String, List<FoodListing>> by = new LinkedHashMap<>();
        for (FoodListing l : listings) {
            if (l.getProviderId() == null) continue;
            List<FoodListing> group = by.get(l.getProviderId());
            if (group == null) by.put(l.getProviderId(), group = new ArrayList<>());
            group.add(l);
        }
        List<Place> out = new ArrayList<>();
        for (List<FoodListing> g : by.values()) {
            FoodListing f = g.get(0);
            String cover = null;
            for (FoodListing l : g) if (l.getImageUrl() != null) { cover = l.getImageUrl(); break; }
            out.add(new Place(f.getProviderId(), f.getProviderName(), f.getProviderLogoUrl(), cover,
                    f.getProviderType() == null ? R.string.role_provider : f.getProviderType().labelRes, g.size()));
        }
        out.sort((a, b) -> Integer.compare(b.listings, a.listings));
        return out;
    }

    private final Listener listener;

    public ProviderChipAdapter(@NonNull Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_provider_card, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Place p = getItem(position);
        h.name.setText(p.name);
        h.meta.setText(h.itemView.getResources().getQuantityString(R.plurals.place_listings, p.listings, p.listings));
        h.type.setText(p.typeLabel);
        ImageLoader.loadFood(h.cover, p.coverUrl);
        ImageLoader.loadLogo(h.logo, p.logoUrl, new Monogram(h.itemView.getContext(), p.name));
        h.itemView.setOnClickListener(v -> listener.onOpen(p));
        h.itemView.setContentDescription(p.name + ", " + h.meta.getText());
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView cover, logo;
        final TextView name, meta, type;

        Holder(@NonNull View v) {
            super(v);
            cover = v.findViewById(R.id.placeCover);
            logo = v.findViewById(R.id.placeLogo);
            name = v.findViewById(R.id.placeName);
            meta = v.findViewById(R.id.placeMeta);
            type = v.findViewById(R.id.placeType);
        }
    }

    private static final DiffUtil.ItemCallback<Place> DIFF = new DiffUtil.ItemCallback<Place>() {
        @Override
        public boolean areItemsTheSame(@NonNull Place a, @NonNull Place b) {
            return a.providerId.equals(b.providerId);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Place a, @NonNull Place b) {
            return a.listings == b.listings && Objects.equals(a.name, b.name)
                    && Objects.equals(a.logoUrl, b.logoUrl) && Objects.equals(a.coverUrl, b.coverUrl);
        }
    };
}
