package com.sharebite.app;

import static org.junit.Assert.assertEquals;

import com.sharebite.app.workers.PickupReminders;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class PickupRemindersTest {

    private static final long NOW = 1_700_000_000_000L;

    @Test
    public void remindsThirtyMinutesBeforeTheDeadline() {
        long deadline = NOW + TimeUnit.HOURS.toMillis(2);
        assertEquals(TimeUnit.MINUTES.toMillis(90), PickupReminders.delayUntilReminder(deadline, NOW));
    }

    @Test
    public void noReminderWhenTheWindowClosesTooSoon() {
        assertEquals(-1, PickupReminders.delayUntilReminder(NOW + TimeUnit.MINUTES.toMillis(20), NOW));
        assertEquals(-1, PickupReminders.delayUntilReminder(NOW - 1, NOW));
    }
}
