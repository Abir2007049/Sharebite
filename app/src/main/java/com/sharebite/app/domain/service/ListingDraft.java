package com.sharebite.app.domain.service;

import androidx.annotation.Nullable;

import com.sharebite.app.data.model.DistributionType;
import com.sharebite.app.data.model.FoodCategory;
import com.sharebite.app.data.model.QuantityUnit;

import java.util.Date;

/** Raw values from the listing editor form, validated by {@link ListingValidator}. */
public class ListingDraft {

    public String title;
    public String description;
    @Nullable public FoodCategory category;
    public String quantityText;
    @Nullable public QuantityUnit unit;
    @Nullable public DistributionType distributionType;
    public String originalPriceText;
    public String discountedPriceText;
    @Nullable public Date preparationTime;
    @Nullable public Date pickupStartTime;
    @Nullable public Date pickupDeadline;
    public String pickupAddress;
    @Nullable public Double latitude;
    @Nullable public Double longitude;
    public String storageInformation;
    public String allergens;
    public String foodSafetyNotes;
    public boolean safetyDeclared;
    /** Units already reserved (edit mode) — the new quantity may not drop below this. */
    public int reservedQuantity;
}
