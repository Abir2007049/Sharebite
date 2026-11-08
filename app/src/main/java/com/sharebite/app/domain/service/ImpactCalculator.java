package com.sharebite.app.domain.service;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.sharebite.app.data.model.DistributionType;
import com.sharebite.app.data.model.FoodCategory;
import com.sharebite.app.data.model.FoodListing;
import com.sharebite.app.data.model.ListingStatus;
import com.sharebite.app.data.model.Reservation;
import com.sharebite.app.data.model.ReservationStatus;
import com.sharebite.app.data.model.UserRole;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * Builds an {@link ImpactReport} from a user's reservations. Pure Java; executed on a background
 * thread by the impact screen because history can contain hundreds of records.
 */
public final class ImpactCalculator {

    public static final int MONTHS_SHOWN = 6;

    private ImpactCalculator() {
    }

    /**
     * @param role         perspective of the report (provider = shared; receiver/charity = collected)
     * @param reservations every reservation where the user is provider or receiver
     * @param ownListings  the provider's listings (empty for other roles)
     */
    @WorkerThread
    @NonNull
    public static ImpactReport calculate(@NonNull UserRole role, @NonNull List<Reservation> reservations,
                                         @NonNull List<FoodListing> ownListings, @NonNull Date now,
                                         @NonNull TimeZone zone, @NonNull Locale locale) {
        int total = 0, completed = 0, charity = 0, pending = 0, confirmed = 0;
        Set<String> partners = new HashSet<>();
        Map<FoodCategory, Integer> byCategory = new EnumMap<>(FoodCategory.class);

        // Prepare month buckets (oldest → newest) keyed by year*12+month.
        Calendar cal = Calendar.getInstance(zone, locale);
        cal.setTime(now);
        int currentKey = cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH);
        int[] monthQty = new int[MONTHS_SHOWN];
        int[] monthCount = new int[MONTHS_SHOWN];

        for (Reservation r : reservations) {
            ReservationStatus s = r.getStatus();
            if (s == ReservationStatus.PENDING) pending++;
            else if (s == ReservationStatus.CONFIRMED) confirmed++;
            if (s != ReservationStatus.COLLECTED) continue;

            completed++;
            total += r.getQuantity();
            if (r.getDistributionType() == DistributionType.CHARITY || r.getReceiverRole() == UserRole.CHARITY) {
                charity++;
            }
            partners.add(role == UserRole.PROVIDER ? r.getReceiverId() : r.getProviderId());
            FoodCategory c = r.getCategory() == null ? FoodCategory.OTHER : r.getCategory();
            Integer prev = byCategory.get(c);
            byCategory.put(c, (prev == null ? 0 : prev) + r.getQuantity());

            if (r.getCollectedAt() != null) {
                cal.setTime(r.getCollectedAt().toDate());
                int key = cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH);
                int index = MONTHS_SHOWN - 1 - (currentKey - key);
                if (index >= 0 && index < MONTHS_SHOWN) {
                    monthQty[index] += r.getQuantity();
                    monthCount[index]++;
                }
            }
        }

        int active = 0;
        for (FoodListing l : ownListings) {
            ListingStatus st = l.effectiveStatus(now);
            if (st == ListingStatus.AVAILABLE || st == ListingStatus.FULLY_RESERVED) active++;
        }

        SimpleDateFormat monthFormat = new SimpleDateFormat("MMM", locale);
        monthFormat.setTimeZone(zone);
        List<ImpactReport.MonthBucket> months = new ArrayList<>(MONTHS_SHOWN);
        for (int i = 0; i < MONTHS_SHOWN; i++) {
            int key = currentKey - (MONTHS_SHOWN - 1 - i);
            cal.clear();
            cal.set(key / 12, key % 12, 1);
            months.add(new ImpactReport.MonthBucket(monthFormat.format(cal.getTime()), monthQty[i], monthCount[i]));
        }

        return new ImpactReport(total, completed, charity, active, pending, confirmed, partners.size(),
                sortByValueDesc(byCategory), months, MealEstimate.of(reservations));
    }

    private static Map<FoodCategory, Integer> sortByValueDesc(Map<FoodCategory, Integer> in) {
        List<Map.Entry<FoodCategory, Integer>> entries = new ArrayList<>(in.entrySet());
        Collections.sort(entries, (a, b) -> Integer.compare(b.getValue(), a.getValue()));
        Map<FoodCategory, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<FoodCategory, Integer> e : entries) out.put(e.getKey(), e.getValue());
        return out;
    }
}
