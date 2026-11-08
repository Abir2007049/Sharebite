package com.sharebite.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.sharebite.app.data.model.Allergen;
import com.sharebite.app.domain.service.AllergenMatcher;

import org.junit.Test;

import java.util.EnumSet;
import java.util.Set;

public class AllergenMatcherTest {

    @Test
    public void findsAllergensFromCommonWording() {
        assertEquals(EnumSet.of(Allergen.GLUTEN, Allergen.MILK, Allergen.EGGS),
                AllergenMatcher.find("Wheat flour, butter and eggs"));
        assertEquals(EnumSet.of(Allergen.MILK), AllergenMatcher.find("Dairy (ghee)"));
        assertEquals(EnumSet.of(Allergen.GLUTEN, Allergen.SESAME), AllergenMatcher.find("Gluten, may contain sesame"));
    }

    @Test
    public void peanutIsNotATreeNut() {
        assertEquals(EnumSet.of(Allergen.PEANUTS), AllergenMatcher.find("Peanuts"));
        assertEquals(EnumSet.of(Allergen.TREE_NUTS), AllergenMatcher.find("Nuts (almond)"));
    }

    @Test
    public void negatedMentionsAreIgnored() {
        assertTrue(AllergenMatcher.find("Nut-free kitchen, gluten free").isEmpty());
        assertTrue(AllergenMatcher.find("No milk, without eggs").isEmpty());
        assertTrue(AllergenMatcher.find("None declared").isEmpty());
        assertTrue(AllergenMatcher.find(null).isEmpty());
    }

    @Test
    public void conflictsAreOnlyTheAvoidedOnes() {
        Set<Allergen> avoided = EnumSet.of(Allergen.MILK, Allergen.PEANUTS);
        assertEquals(EnumSet.of(Allergen.MILK), AllergenMatcher.conflicts("Gluten, milk, eggs, nuts", avoided));
        assertTrue(AllergenMatcher.conflicts("Gluten", avoided).isEmpty());
        assertTrue(AllergenMatcher.conflicts("Milk", EnumSet.noneOf(Allergen.class)).isEmpty());
    }
}
