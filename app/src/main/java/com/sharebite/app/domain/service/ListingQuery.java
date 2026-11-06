package com.sharebite.app.domain.service;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.sharebite.app.data.model.DistributionType;
import com.sharebite.app.data.model.FoodCategory;

/** Immutable search/filter/sort criteria for the discovery screens. */
public final class ListingQuery {

    public enum Sort { NEWEST, ENDING_SOON, NEAREST }

    @NonNull public final String searchText;
    @Nullable public final FoodCategory category;
    @Nullable public final DistributionType distributionType;
    @NonNull public final Sort sort;

    public ListingQuery(@Nullable String searchText, @Nullable FoodCategory category,
                        @Nullable DistributionType distributionType, @NonNull Sort sort) {
        this.searchText = searchText == null ? "" : searchText.trim();
        this.category = category;
        this.distributionType = distributionType;
        this.sort = sort;
    }

    public static ListingQuery defaults() {
        return new ListingQuery("", null, null, Sort.NEWEST);
    }

    public ListingQuery withSearch(String text) { return new ListingQuery(text, category, distributionType, sort); }
    public ListingQuery withCategory(FoodCategory c) { return new ListingQuery(searchText, c, distributionType, sort); }
    public ListingQuery withDistribution(DistributionType t) { return new ListingQuery(searchText, category, t, sort); }
    public ListingQuery withSort(Sort s) { return new ListingQuery(searchText, category, distributionType, s); }
}
