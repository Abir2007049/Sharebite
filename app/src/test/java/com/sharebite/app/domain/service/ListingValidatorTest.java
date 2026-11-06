package com.sharebite.app.domain.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.sharebite.app.data.model.DistributionType;
import com.sharebite.app.data.model.FoodCategory;
import com.sharebite.app.data.model.QuantityUnit;
import com.sharebite.app.domain.service.ListingValidator.Field;

import org.junit.Test;

import java.util.Date;

public class ListingValidatorTest {

    private static final long HOUR = 3_600_000L;
    private final Date now = new Date();

    private ListingDraft valid() {
        ListingDraft d = new ListingDraft();
        d.title = "Vegetable biryani";
        d.description = "Freshly cooked vegetable biryani, mild.";
        d.category = FoodCategory.COOKED_MEALS;
        d.quantityText = "12";
        d.unit = QuantityUnit.PORTIONS;
        d.distributionType = DistributionType.FREE;
        d.preparationTime = new Date(now.getTime() - HOUR);
        d.pickupStartTime = now;
        d.pickupDeadline = new Date(now.getTime() + 3 * HOUR);
        d.pickupAddress = "12 Lake Road, Dhaka";
        d.storageInformation = "Kept hot above 60°C";
        d.allergens = "None";
        d.safetyDeclared = true;
        return d;
    }

    @Test
    public void completeDraftIsValid() {
        assertTrue(ListingValidator.validate(valid(), now).isValid());
    }

    @Test
    public void foodSafetyFieldsAreRequired() {
        ListingDraft d = valid();
        d.storageInformation = "";
        d.allergens = " ";
        d.safetyDeclared = false;
        d.preparationTime = null;
        ListingValidator.Result r = ListingValidator.validate(d, now);
        assertNotNull(r.errorFor(Field.STORAGE));
        assertNotNull(r.errorFor(Field.ALLERGENS));
        assertNotNull(r.errorFor(Field.SAFETY_DECLARATION));
        assertNotNull(r.errorFor(Field.PREPARATION_TIME));
    }

    @Test
    public void deadlineMustBeFutureAndAfterStart() {
        ListingDraft past = valid();
        past.pickupDeadline = new Date(now.getTime() - 1);
        assertNotNull(ListingValidator.validate(past, now).errorFor(Field.PICKUP_DEADLINE));

        ListingDraft beforeStart = valid();
        beforeStart.pickupStartTime = new Date(now.getTime() + 4 * HOUR);
        assertNotNull(ListingValidator.validate(beforeStart, now).errorFor(Field.PICKUP_DEADLINE));

        ListingDraft tooLong = valid();
        tooLong.pickupDeadline = new Date(now.getTime() + 8 * 24 * HOUR);
        assertNotNull(ListingValidator.validate(tooLong, now).errorFor(Field.PICKUP_DEADLINE));
    }

    @Test
    public void preparationCannotBeInTheFuture() {
        ListingDraft d = valid();
        d.preparationTime = new Date(now.getTime() + HOUR);
        assertNotNull(ListingValidator.validate(d, now).errorFor(Field.PREPARATION_TIME));
    }

    @Test
    public void discountedListingsNeedValidPrices() {
        ListingDraft d = valid();
        d.distributionType = DistributionType.DISCOUNTED;
        d.originalPriceText = "100";
        d.discountedPriceText = "120";
        assertNotNull(ListingValidator.validate(d, now).errorFor(Field.DISCOUNTED_PRICE));
        d.discountedPriceText = "45,5"; // comma decimal separator is accepted
        assertTrue(ListingValidator.validate(d, now).isValid());
    }

    @Test
    public void freeListingsIgnorePriceFields() {
        ListingDraft d = valid();
        d.originalPriceText = "garbage";
        assertNull(ListingValidator.validate(d, now).errorFor(Field.ORIGINAL_PRICE));
    }

    @Test
    public void quantityMustBePositiveWholeAndAtLeastReserved() {
        ListingDraft d = valid();
        d.quantityText = "2.5";
        assertNotNull(ListingValidator.validate(d, now).errorFor(Field.QUANTITY));
        d.quantityText = "0";
        assertNotNull(ListingValidator.validate(d, now).errorFor(Field.QUANTITY));
        d.quantityText = "3";
        d.reservedQuantity = 4;
        assertNotNull(ListingValidator.validate(d, now).errorFor(Field.QUANTITY));
        d.reservedQuantity = 3;
        assertFalse(ListingValidator.validate(d, now).errors().containsKey(Field.QUANTITY));
    }

    @Test
    public void moneyIsRoundedToCents() {
        assertTrue(Math.abs(ListingValidator.roundMoney(3.14159) - 3.14) < 1e-9);
        assertTrue(Math.abs(ListingValidator.roundMoney(45.4999) - 45.5) < 1e-9);
        assertTrue(Math.abs(ListingValidator.roundMoney(19.999) - 20.0) < 1e-9);
    }
}
