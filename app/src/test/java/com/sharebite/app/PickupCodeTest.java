package com.sharebite.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.sharebite.app.domain.service.PickupCode;

import org.junit.Test;

public class PickupCodeTest {

    @Test
    public void codesAreRandom128BitHex() {
        String a = PickupCode.generate();
        String b = PickupCode.generate();
        assertTrue(a.matches("[0-9a-f]{32}"));
        assertNotEquals(a, b);
    }

    @Test
    public void payloadRoundTrips() {
        String code = PickupCode.generate();
        PickupCode.Scanned s = PickupCode.parse(PickupCode.payload("res_AB-12", code));
        assertNotNull(s);
        assertEquals("res_AB-12", s.reservationId);
        assertEquals(code, s.code);
    }

    @Test
    public void foreignOrTamperedCodesAreRejected() {
        assertNull(PickupCode.parse("https://example.com"));
        assertNull(PickupCode.parse("sharebite:pickup:v1:res1:short"));
        assertNull(PickupCode.parse("sharebite:pickup:v1:res1:" + "Z".repeat(32)));
        assertNull(PickupCode.parse(null));
    }
}
