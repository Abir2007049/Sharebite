# ShareBite demo guide

This guide follows the presentation checklist: what to show on the phone, and which file to open if asked how it works. All paths are under `app/src/main/` unless they start with the repo root.

Before the demo, run `cd firebase-tests && node seed.mjs` to refresh the demo listings (pickup windows expire). The demo password is in `firebase-tests/.seed-credentials.json`.

| Account | Use it to show |
|---|---|
| `receiver.demo@sharebite.example` | Receiver: browsing, reserving, QR code, chat, "On my way" |
| `bakery.demo@sharebite.example` | Provider (Golden Crust Bakery): dashboard, listings, scanning the QR |
| `charity.demo@sharebite.example` | Charity: verification flow |

---

## 1. Overview and features

ShareBite connects restaurants, bakeries and households that have surplus food with people and charities who can collect it before it goes to waste.

Suggested walkthrough (about 3 minutes):
1. **Welcome:** tap *Browse food without an account* (guest mode). Show Home: the ShareBite logo, the greeting, category tiles, *Places sharing food now*, and the food carousels with photos, logos and price tags.
2. **A restaurant page:** tap a place card to see its cover, logo, about text and live listings.
3. **A listing:** open one. Show the photo transition, allergens, weather advice, the map link and Share. *Sign in to reserve* takes guests to login.
4. **Log in as the receiver:** reserve something, then see the success animation and the reservation timeline.
5. **Log in as the provider** (a second phone, or log out and back in): confirm the request.
6. **Back on the receiver:** chat ("typing…", "Online"), *I'm on my way* (live notification), then *Show QR code*.
7. **Provider:** *Scan QR code*. The reservation becomes *Collected (verified by QR code)*.
8. **Impact tab:** collected food, the meals estimate (tap ⓘ for the method) and the monthly chart.

## 2. Architecture and navigation

- **MVVM:** Activities and Fragments contain only UI code; ViewModels (`ui/**/…ViewModel.java`) hold state in `LiveData`; repositories (`data/repository/`) talk to Firebase behind interfaces (`domain/interfaces/`).
- **Multiple activities:** Splash → Welcome / Login / Register → Main (bottom-navigation fragments) → ListingDetail, ProviderProfile, ReservationDetail, Chat, ListingEditor, Map, EditProfile, Notifications… (see `AndroidManifest.xml`).
- **Explicit intents:** `ListingDetailActivity.intent(context, id)`, `ChatActivity.intent(...)`, `ProviderProfileActivity.intent(...)`, plus extras and `ActivityResultLauncher` results such as the location picker.
- **Implicit intents:**
  - directions: `ACTION_VIEW` with a `geo:` URI (`ui/explore/MapSupport.java`);
  - calling a provider: `ACTION_DIAL` (`ListingDetailActivity`);
  - sharing a listing: `ACTION_SEND` with a chooser (`ListingDetailActivity#share`);
  - taking a photo: `TakePicture` (`ListingEditorActivity`).
- **View Binding:** enabled in `app/build.gradle` (`viewBinding true`); every screen uses `XxxBinding.inflate(...)`.
- **Event handling:** click listeners, `TextWatcher` (chat typing indicator), the IME send action, a menu item click (Share), bottom navigation selection, and the `ActivityResult` callbacks.

## 3. UI and lists

- **Layouts:**
  - `LinearLayout` is used throughout;
  - `ConstraintLayout`: `layout/item_provider_card.xml` (the logo overlaps the cover using constraints and a dimension ratio) and `layout/activity_splash.xml`;
  - `FrameLayout` for image overlays.
- **RecyclerView, adapters and ViewHolders** (`adapter/`):
  - `FoodCardAdapter`: two styles, DiffUtil, ViewHolder;
  - `ProviderChipAdapter`;
  - `ReservationAdapter`;
  - `ChatAdapter`: left/right message bubbles;
  - `NotificationAdapter`;
  - `MyListingAdapter`.
- **Polish:**
  - cards lift and shrink slightly when pressed (`animator/card_lift.xml`);
  - lists cascade in (`anim/layout_cards_enter.xml`);
  - skeleton placeholders replace spinners while loading (`ui/widget/SkeletonLayout.java`);
  - the card photo morphs into the details screen (shared-element transition).

## 4. Android components and permissions

| Requirement | Where | How to demo |
|---|---|---|
| **System broadcasts** | `utils/SystemStatusMonitor.java`, registered in `MainActivity#onStart` | Turn on **Airplane mode** from quick settings: a banner says you're offline and that changes will sync. Turn on **Battery Saver**: a banner appears and loading animations stop. The receiver handles `ACTION_AIRPLANE_MODE_CHANGED`, `ACTION_BATTERY_LOW/OKAY` and `ACTION_POWER_SAVE_MODE_CHANGED`. |
| **Content providers** | `FileProvider` in `AndroidManifest.xml` + `res/xml/file_paths.xml`; `ListingEditorActivity#launchCamera` | As a provider, open *New listing* and tap the photo, then *Take a photo*. The camera app writes into our provider's `content://` URI. *Choose from gallery* reads through the MediaStore provider via `ContentResolver` (`workers/ImageCompressionTask`). |
| **Foreground service with live notification** | `workers/OnMyWayService.java` (type `location`) | As the receiver, open a confirmed reservation and tap **I'm on my way**. An ongoing notification shows the distance to the pickup, the time left and a progress bar, updated as you move and every 30 s, with a **Stop** action. It ends automatically when the food is collected or the window closes. |
| **Background tasks** | `workers/PickupReminders` + `PickupReminderWorker` (WorkManager); `workers/BackgroundTaskRunner` (ExecutorService) | Pickup reminder 30 minutes before the window closes; image compression, API calls and filtering run off the main thread. |
| **Runtime permissions** | Location (`LocationRequester`), notifications (`MainActivity`), camera (`ListingEditorActivity`), location for the service (`ReservationDetailActivity`) | Each one is requested only when needed, and the app keeps working if you deny it. |

## 5. Networking and Firebase

- **HTTP and JSON:** `data/remote/HttpJsonClient.java` uses `HttpURLConnection` with timeouts, error mapping and Gson parsing.
  - `NominatimGeocodingService`: address → coordinates (OpenStreetMap);
  - `OpenMeteoWeatherService`: weather at the pickup point → food-handling advice on the listing.
- **Firebase Authentication:** email/password, password reset, persistent sessions (`FirebaseAuthRepository`).
- **Cloud Firestore:**
  - users, listings, reservations, notifications and chat messages, with realtime listeners;
  - **transactions** for reserving, confirming, cancelling and collecting;
  - security rules in `firestore.rules`, tested by 47 emulator tests (`firebase-tests/`).
- **Realtime Database:** `data/repository/RealtimePresenceRepository.java` and `database.rules.json`.
  - Chat shows **Online / Last seen …** using the `.info/connected` + `onDisconnect()` pattern;
  - a **typing…** indicator.
  - *Demo:* open the same chat on two phones and type on one.
- **Picture storage:** listing photos, provider logos and covers are compressed on a background thread, then stored as Firestore documents (`FirestoreImageStorage`, `images/` collection) and displayed by a custom Glide loader (`utils/FirestoreImageLoader.java`). This is because Firebase Storage needs a paid plan. Verification documents go to a private collection (`privateImages/`). Firebase Storage can be switched back on with `-Psharebite.cloudStorage=true`.

## 6. Layout responsiveness (optional)

- **Rotation:** not locked. State survives rotation because it lives in ViewModels (try rotating on the listing editor or in Explore with a filter set).
- **Dynamic layout:** Explore shows 1 column on phones, 2 in landscape or on small tablets (`values-w600dp/integers.xml`) and 3 on large tablets (`values-w900dp`).
- **Edge-to-edge:** system-bar insets are applied in `BaseActivity`, and the bottom bar floats above the gesture area.

## Tests to mention

- `./gradlew testDebugUnitTest`: **70 JVM unit tests** (domain rules, allergen matching, meals estimate, QR codes, reminders, formatting…).
- `cd firebase-tests && npm test`: **47 security-rules tests** on the Firestore emulator. They check that tampered writes are rejected, for example overselling, faking a QR verification, or reading someone else's chat.
- `./gradlew lintDebug`: 0 errors.
