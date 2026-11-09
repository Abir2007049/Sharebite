package com.sharebite.app.debug;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import android.app.Activity;

import com.google.firebase.auth.FirebaseAuth;
import com.sharebite.app.data.model.FoodListing;
import com.sharebite.app.data.model.User;
import com.sharebite.app.domain.interfaces.ResultCallback;
import com.sharebite.app.ui.auth.WelcomeActivity;
import com.sharebite.app.ui.home.MainActivity;
import com.sharebite.app.ui.listing.ListingDetailActivity;
import com.sharebite.app.ui.listing.ListingEditorActivity;
import com.sharebite.app.ui.listing.ProviderProfileActivity;
import com.sharebite.app.ui.profile.EditProfileActivity;
import com.sharebite.app.ui.profile.NotificationsActivity;
import com.sharebite.app.ui.reservation.ChatActivity;
import com.sharebite.app.ui.reservation.ReservationDetailActivity;
import com.sharebite.app.ui.explore.MapActivity;
import com.sharebite.app.utils.ServiceLocator;
import com.sharebite.app.utils.SessionManager;

/**
 * DEBUG BUILDS ONLY (src/debug): opens a given screen, optionally signed in as a given demo account,
 * so report screenshots can be taken from a computer with adb, on phones that block input injection.
 *
 * <pre>
 * adb shell am start -n com.sharebite.app/.debug.DemoTourActivity \
 *     --es screen listing --es id demo2-biryani [--es email ... --es password ...]
 * </pre>
 * Screens: welcome, guest, home, explore, listings, reservations, impact, profile, listing, provider,
 * reservation, chat, editor, editprofile, notifications, map.
 */
public class DemoTourActivity extends Activity {

    private static final String TAG = "DemoTour";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ServiceLocator.isFirebaseConfigured(this)) {
            finish();
            return;
        }
        String email = getIntent().getStringExtra("email");
        String password = getIntent().getStringExtra("password");
        SessionManager session = ServiceLocator.get().session();
        FirebaseAuth auth = FirebaseAuth.getInstance();
        boolean sameUser = auth.getCurrentUser() != null && email != null && email.equalsIgnoreCase(auth.getCurrentUser().getEmail());
        if (email == null || password == null || (sameUser && session.user() != null)) {
            ensureProfileThenOpen();
            return;
        }
        session.signOut();
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(r -> ensureProfileThenOpen())
                .addOnFailureListener(e -> {
                    Log.w(TAG, "sign-in failed", e);
                    finish();
                });
    }

    private void ensureProfileThenOpen() {
        SessionManager session = ServiceLocator.get().session();
        String uid = session.uid();
        String screen = getIntent().getStringExtra("screen");
        if (uid == null || session.user() != null || "welcome".equals(screen) || "guest".equals(screen)) {
            open();
            return;
        }
        ServiceLocator.get().users().getById(uid, new ResultCallback<User>() {
            @Override
            public void onSuccess(User user) {
                if (user != null) session.start(user);
                open();
            }

            @Override
            public void onError(@NonNull Exception error) {
                finish();
            }
        });
    }

    private void open() {
        String screen = getIntent().getStringExtra("screen");
        String id = getIntent().getStringExtra("id");
        if (screen == null) screen = "home";
        Intent target;
        switch (screen) {
            case "welcome":
                ServiceLocator.get().session().signOut();
                target = new Intent(this, WelcomeActivity.class);
                break;
            case "guest":
                ServiceLocator.get().session().signOut();
                ServiceLocator.get().session().startGuest();
                target = main("home");
                break;
            case "home": case "explore": case "listings": case "reservations": case "impact": case "profile":
                target = main(screen);
                break;
            case "listing": target = ListingDetailActivity.intent(this, id); break;
            case "reservation": target = ReservationDetailActivity.intent(this, id); break;
            case "chat": target = ChatActivity.intent(this, id); break;
            case "editor": target = ListingEditorActivity.createIntent(this); break;
            case "editprofile": target = new Intent(this, EditProfileActivity.class); break;
            case "notifications": target = new Intent(this, NotificationsActivity.class); break;
            case "map": target = new Intent(this, MapActivity.class); break;
            case "provider":
                openProviderOf(id);
                return;
            default:
                target = main("home");
        }
        startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }

    private Intent main(String tab) {
        return new Intent(this, MainActivity.class).putExtra(MainActivity.EXTRA_TAB, tab);
    }

    /** Storefront of the provider who owns listing {@code listingId}. */
    private void openProviderOf(String listingId) {
        ServiceLocator.get().food().getById(listingId, new ResultCallback<FoodListing>() {
            @Override
            public void onSuccess(FoodListing l) {
                if (l != null) {
                    startActivity(ProviderProfileActivity.intent(DemoTourActivity.this, l.getProviderId(),
                            l.getProviderName(), l.getProviderLogoUrl()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                }
                finish();
            }

            @Override
            public void onError(@NonNull Exception error) {
                finish();
            }
        });
    }
}
