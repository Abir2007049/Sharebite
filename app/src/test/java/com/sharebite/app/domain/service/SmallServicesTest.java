package com.sharebite.app.domain.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.sharebite.app.data.model.FoodCategory;
import com.sharebite.app.data.model.Reservation;
import com.sharebite.app.data.model.ReservationStatus;
import com.sharebite.app.data.remote.dto.WeatherInfo;
import com.sharebite.app.domain.service.ReservationActions.Action;
import com.sharebite.app.workers.ImageCompressionTask;

import org.junit.Test;

/** Distance, food-handling advice, reservation action policy and image sampling. */
public class SmallServicesTest {

    @Test
    public void haversineMatchesKnownDistances() {
        assertEquals(0.0, DistanceCalculator.distanceKm(23.78, 90.41, 23.78, 90.41), 1e-9);
        // One degree of latitude ≈ 111.2 km.
        assertEquals(111.2, DistanceCalculator.distanceKm(0, 0, 1, 0), 0.2);
        // Dhaka ↔ Chattogram ≈ 213 km great-circle.
        assertEquals(213, DistanceCalculator.distanceKm(23.8103, 90.4125, 22.3569, 91.7832), 5);
    }

    @Test
    public void handlingAdviceForHeatAndRain() {
        WeatherInfo hot = new WeatherInfo(33, 60.0, 0, "2026-11-07T12:00");
        assertTrue(FoodHandlingAdvisor.advise(FoodCategory.DAIRY, hot).contains("cool box"));
        assertNotNull(FoodHandlingAdvisor.advise(FoodCategory.BAKERY, hot));
        WeatherInfo mild = new WeatherInfo(20, 50.0, 1, "2026-11-07T12:00");
        assertNull(FoodHandlingAdvisor.advise(FoodCategory.COOKED_MEALS, mild));
        WeatherInfo rain = new WeatherInfo(20, 90.0, 63, "2026-11-07T12:00");
        assertTrue(FoodHandlingAdvisor.advise(FoodCategory.BAKERY, rain).contains("dry"));
    }

    @Test
    public void actionsDependOnViewerAndStatus() {
        Reservation r = new Reservation();
        r.setProviderId("p");
        r.setReceiverId("c");
        r.setStatus(ReservationStatus.PENDING);
        assertEquals(Action.CONFIRM, ReservationActions.forViewer(r, "p").primary);
        assertEquals(Action.REJECT, ReservationActions.forViewer(r, "p").secondary);
        assertNull(ReservationActions.forViewer(r, "c").primary);
        assertEquals(Action.CANCEL, ReservationActions.forViewer(r, "c").secondary);
        assertTrue(ReservationActions.forViewer(r, "stranger").isEmpty());

        r.setStatus(ReservationStatus.CONFIRMED);
        assertEquals(Action.COLLECT, ReservationActions.forViewer(r, "p").primary);
        assertEquals(Action.COLLECT, ReservationActions.forViewer(r, "c").primary);

        r.setStatus(ReservationStatus.COLLECTED);
        assertTrue(ReservationActions.forViewer(r, "p").isEmpty());
        assertTrue(ReservationActions.forViewer(r, "c").isEmpty());
    }

    @Test
    public void imageSampleSizeKeepsBothSidesAboveTarget() {
        assertEquals(1, ImageCompressionTask.calculateInSampleSize(1280, 960, 1280));
        assertEquals(2, ImageCompressionTask.calculateInSampleSize(4000, 3000, 1280));
        assertEquals(4, ImageCompressionTask.calculateInSampleSize(8000, 6000, 1280));
    }
}
