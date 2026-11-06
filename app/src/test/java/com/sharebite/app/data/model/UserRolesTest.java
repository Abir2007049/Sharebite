package com.sharebite.app.data.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.sharebite.app.TestData;

import org.junit.Test;

import java.util.Date;

/** Role-specific behaviour implemented by overriding methods of {@link User}. */
public class UserRolesTest {

    private final Date now = new Date();
    private final FoodListing free = TestData.listing("f", DistributionType.FREE, 50, 50, now);
    private final FoodListing discounted = TestData.listing("d", DistributionType.DISCOUNTED, 50, 50, now);
    private final FoodListing charity = TestData.listing("c", DistributionType.CHARITY, 50, 50, now);

    @Test
    public void receiversReserveFreeAndDiscountedUpToFive() {
        User r = new Receiver("r", "Rita", "r@example.com", "123456789");
        assertEquals(User.RECEIVER_MAX_PER_RESERVATION, r.maxReservableQuantity(free));
        assertEquals(User.RECEIVER_MAX_PER_RESERVATION, r.maxReservableQuantity(discounted));
        assertEquals(0, r.maxReservableQuantity(charity));
    }

    @Test
    public void receiverLimitIsCappedByStock() {
        User r = new Receiver("r", "Rita", "r@example.com", "123456789");
        assertEquals(2, r.maxReservableQuantity(TestData.listing("x", DistributionType.FREE, 10, 2, now)));
    }

    @Test
    public void charitiesNeedVerificationForCharityDonations() {
        Charity c = new Charity("c", "Chris", "c@example.com", "123456789", "Food For All");
        assertEquals(VerificationStatus.UNVERIFIED, c.getVerificationStatus());
        assertTrue(c.requiresVerification());
        assertEquals(0, c.maxReservableQuantity(charity));
        assertEquals("free food is open to unverified charities", 50, c.maxReservableQuantity(free));
        assertEquals(0, c.maxReservableQuantity(discounted));

        c.setVerificationStatus(VerificationStatus.VERIFIED);
        assertEquals("verified charities collect in bulk", 50, c.maxReservableQuantity(charity));
    }

    @Test
    public void providersShareButNeverReserve() {
        Provider p = new Provider("p", "Pat", "p@example.com", "123456789", "Green Bakery", ProviderType.BAKERY);
        assertTrue(p.canCreateListings());
        assertEquals(0, p.maxReservableQuantity(free));
        assertEquals(VerificationStatus.NOT_REQUIRED, p.getVerificationStatus());
    }

    @Test
    public void displayNameDependsOnRole() {
        Provider business = new Provider("p", "Pat", "p@example.com", "123456789", "Green Bakery", ProviderType.BAKERY);
        Provider household = new Provider("h", "Hana", "h@example.com", "123456789", "Ignored", ProviderType.HOUSEHOLD);
        Charity charityOrg = new Charity("c", "Chris", "c@example.com", "123456789", "Food For All");
        Receiver person = new Receiver("r", "Rita", "r@example.com", "123456789");
        assertEquals("Green Bakery", business.displayName());
        assertEquals("Hana", household.displayName());
        assertEquals("Food For All", charityOrg.displayName());
        assertEquals("Rita", person.displayName());
    }

    @Test
    public void registrationBuildsTheRightSubclassWithSafeDefaults() {
        User u = new RegistrationRequest("Chris", " Chris@Example.COM ", "secret123", "123456789",
                UserRole.CHARITY, "Food For All", null).toUser("uid1");
        assertTrue(u instanceof Charity);
        assertEquals("chris@example.com", u.getEmail());
        assertEquals(VerificationStatus.UNVERIFIED, u.getVerificationStatus());
        assertFalse(u.canCreateListings());
    }
}
