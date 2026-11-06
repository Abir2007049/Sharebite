package com.sharebite.app.utils;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.gson.Gson;
import com.sharebite.app.BuildConfig;
import com.sharebite.app.FirebaseSetup;
import com.sharebite.app.data.remote.HttpJsonClient;
import com.sharebite.app.data.remote.NominatimGeocodingService;
import com.sharebite.app.data.remote.OpenMeteoWeatherService;
import com.sharebite.app.data.repository.FirebaseAuthRepository;
import com.sharebite.app.data.repository.FirebaseImageStorage;
import com.sharebite.app.data.repository.FirestoreFoodRepository;
import com.sharebite.app.data.repository.FirestoreNotificationRepository;
import com.sharebite.app.data.repository.FirestoreReservationRepository;
import com.sharebite.app.data.repository.FirestoreUserRepository;
import com.sharebite.app.domain.interfaces.AuthRepository;
import com.sharebite.app.domain.interfaces.FoodRepository;
import com.sharebite.app.domain.interfaces.GeocodingService;
import com.sharebite.app.domain.interfaces.ImageStorage;
import com.sharebite.app.domain.interfaces.NotificationRepository;
import com.sharebite.app.domain.interfaces.ReservationRepository;
import com.sharebite.app.domain.interfaces.UserRepository;
import com.sharebite.app.domain.interfaces.WeatherService;

/**
 * Minimal dependency container. ViewModels depend on the repository <em>interfaces</em>; this is
 * the single place that knows the Firebase implementations.
 */
public final class ServiceLocator {

    private static volatile ServiceLocator instance;

    private final UserRepository userRepository;
    private final AuthRepository authRepository;
    private final FoodRepository foodRepository;
    private final ReservationRepository reservationRepository;
    private final NotificationRepository notificationRepository;
    private final ImageStorage imageStorage;
    private final GeocodingService geocodingService;
    private final WeatherService weatherService;
    private final SessionManager sessionManager;

    private ServiceLocator(Context context) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        imageStorage = new FirebaseImageStorage(FirebaseStorage::getInstance);
        userRepository = new FirestoreUserRepository(db);
        authRepository = new FirebaseAuthRepository(FirebaseAuth.getInstance(), userRepository);
        foodRepository = new FirestoreFoodRepository(db, imageStorage);
        reservationRepository = new FirestoreReservationRepository(db);
        notificationRepository = new FirestoreNotificationRepository(db);

        HttpJsonClient http = new HttpJsonClient(new Gson(),
                "ShareBite-Android/" + BuildConfig.VERSION_NAME + " (" + BuildConfig.APPLICATION_ID + ")");
        geocodingService = new NominatimGeocodingService(http);
        weatherService = new OpenMeteoWeatherService(http);
        sessionManager = new SessionManager(FirebaseAuth.getInstance(), userRepository);
    }

    /** True when Firebase initialised (from google-services.json or in emulator mode). */
    public static boolean isFirebaseConfigured(@NonNull Context context) {
        return FirebaseSetup.isReady(context);
    }

    public static void init(@NonNull Context context) {
        if (instance == null) {
            synchronized (ServiceLocator.class) {
                if (instance == null) instance = new ServiceLocator(context.getApplicationContext());
            }
        }
    }

    @NonNull
    public static ServiceLocator get() {
        ServiceLocator s = instance;
        if (s == null) throw new IllegalStateException("ServiceLocator.init() was not called (is Firebase configured?)");
        return s;
    }

    public UserRepository users() { return userRepository; }
    public AuthRepository auth() { return authRepository; }
    public FoodRepository food() { return foodRepository; }
    public ReservationRepository reservations() { return reservationRepository; }
    public NotificationRepository notifications() { return notificationRepository; }
    public ImageStorage images() { return imageStorage; }
    public GeocodingService geocoding() { return geocodingService; }
    public WeatherService weather() { return weatherService; }
    public SessionManager session() { return sessionManager; }
}
