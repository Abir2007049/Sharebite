package com.sharebite.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.sharebite.app.utils.FirestoreImageLoader;
import com.sharebite.app.utils.Formatters;
import com.sharebite.app.utils.Monogram;

import org.junit.Test;

public class StorefrontHelpersTest {

    @Test
    public void firestoreImageReferencesAreRecognised() {
        assertNotNull(FirestoreImageLoader.Ref.parse("firestore-image://images/abc123"));
        assertEquals(FirestoreImageLoader.Ref.parse("firestore-image://images/abc123"),
                FirestoreImageLoader.Ref.parse("firestore-image://images/abc123"));
        assertNull(FirestoreImageLoader.Ref.parse("https://example.com/a.jpg"));
        assertNull(FirestoreImageLoader.Ref.parse("firestore-image://images/"));
        assertNull(FirestoreImageLoader.Ref.parse("firestore-image://a/b/c"));
        assertNull(FirestoreImageLoader.Ref.parse(null));
    }

    @Test
    public void monogramUsesUpToTwoInitials() {
        assertEquals("GC", Monogram.initials("Golden Crust Bakery"));
        assertEquals("N", Monogram.initials("nadia"));
        assertEquals("?", Monogram.initials("  "));
        assertEquals(Monogram.colorFor("Spice Route"), Monogram.colorFor(" spice route "));
    }

    @Test
    public void pricesAreShownInTaka() {
        java.util.Locale.setDefault(java.util.Locale.US); // decimal separator for the assertion
        assertEquals(Formatters.CURRENCY_SYMBOL + "40", Formatters.money(40));
        assertEquals(Formatters.CURRENCY_SYMBOL + "45.5", Formatters.money(45.5));
    }

    @Test
    public void greetingFollowsTheTimeOfDay() {
        assertEquals(R.string.greeting_morning, Formatters.greetingFor(8));
        assertEquals(R.string.greeting_afternoon, Formatters.greetingFor(14));
        assertEquals(R.string.greeting_evening, Formatters.greetingFor(20));
        assertEquals(R.string.greeting_evening, Formatters.greetingFor(2));
    }
}
