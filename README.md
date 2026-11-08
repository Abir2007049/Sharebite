# ShareBite

**Rescue Food. Share Goodness. Reduce Waste.**

ShareBite is an Android app (Java) for food rescue. Restaurants, bakeries, campus canteens and households post surplus food. Individuals and registered charities reserve it and collect it in person. Listings can be **free**, **discounted** (paid to the provider at pickup), or **charity-only** (verified organisations).

There are no delivery riders, carts or payment gateways. The workflow is list → reserve → confirm → collect.

---

## Contents

- [Features](#features)
- [Technology stack](#technology-stack)
- [Architecture](#architecture)
- [Firestore data model](#firestore-data-model)
- [Security rules](#security-rules)
- [Getting started](#getting-started)
- [Firebase setup](#firebase-setup)
- [Running against the Firebase emulators](#running-against-the-firebase-emulators-no-cloud-project-needed)
- [External APIs](#external-apis)
- [Testing](#testing)
- [Screenshots](#screenshots)
- [Known limitations](#known-limitations)

---

## Features

### Accounts and roles
- Email/password sign-up and login with Firebase Authentication, password reset by email, and persistent sessions.
- Three roles, chosen at registration: **Food provider** (with provider type), **Food receiver**, and **Charity organisation**.
- Charities start as *Not verified*. They submit their organisation details (and an optional document photo) for review. Only a reviewer can set *Verified*: the client cannot, and the security rules enforce this.

### Home dashboards (role-specific)
- **Receivers:** search entry point, category shortcuts, an availability summary, and *Near you* (when location is shared), *Recently added*, *Free food* and *Discounted* carousels.
- **Charities:** verification status banner, active/completed collection counts, *Charity donations* first, then free food.
- **Providers:** a "Share food" quick action, plus active listings, units shared, pending requests, completed pickups, the pending queue and recent activity.

### Food listings (providers)
- Create, edit, withdraw, close and delete listings. Deleting is only allowed if the listing was never reserved; otherwise you withdraw it.
- Fields: title, description, category, photo, quantity and unit, distribution type, original and discounted price, preparation time, pickup window and deadline, address, an optional map pin, storage conditions, allergens, extra safety notes, and a mandatory "fit for human consumption" declaration.
- Photos are compressed on a background thread before upload, to at most 1280 px with EXIF rotation applied.
- Editing preserves units that are already reserved. The distribution type is locked once anyone has reserved.
- Each provider's overdue listings are marked `EXPIRED` automatically, and expired listings are never reservable (enforced client-side *and* in the rules).

### Discover
- Explore offers search (title, description, provider), category and distribution filters, and sorting by newest, ending soon or nearest.
- The map view uses OpenStreetMap. Tap a marker to open the listing or get directions in an external maps app.
- Distance is shown only when the device has a real location fix *and* the listing has coordinates. Nothing is guessed.

### Reservations
- A quantity sheet shows the role limit (5 units per reservation for individuals; bulk for charities). It also requires an explicit acknowledgement when allergens are declared.
- Every state change runs in a **Firestore transaction**: reserve, confirm, decline, cancel and mark collected. Each transaction re-reads the listing and reservation, so two people can't take the last portions and a reservation can't be processed twice.
- The status machine is `PENDING → CONFIRMED → COLLECTED`, with `PENDING → REJECTED` and `PENDING/CONFIRMED → CANCELLED`. Either party can confirm the hand-over, and who did is recorded.
- Stock is returned exactly when a reservation is declined or cancelled. Withdrawing a listing declines or cancels its open reservations and notifies those people.
- The reservation detail screen shows a status timeline and pickup information.
- **QR-verified hand-over.** Each reservation gets a one-time 128-bit pickup code, created in the same transaction and stored in a sub-document only the receiver can read. The receiver shows it as a QR code, and the provider scans it to mark the food collected. The rules accept a *verified* collection only when the scanned code matches, so a provider can't claim one without the receiver present. The manual "mark collected" stays as a fallback.
- **Private chat** per reservation ("I'm at the side gate"). Only the two parties can read or write. Messages can't be edited or spoofed, and writing closes when the reservation does.
- **Pickup reminders.** Receivers get a local notification 30 minutes before a confirmed pickup window closes (WorkManager, no server). The reminder re-checks the reservation first, so a cancelled or collected pickup doesn't remind.
- In-app notifications are written in the same transaction as the change they describe.

### Impact dashboard
- Shows units shared or rescued, completed collections, charity collections, people/charities helped (or providers supported), and active listings or reservations.
- Includes a monthly activity chart (custom Canvas view, last six months) and a breakdown by category.
- **Only `COLLECTED` reservations count.** No CO₂ or other environmental estimates are shown, because the app has no defensible model for them (this is stated in the UI).
- **Meals estimate, with its method shown in the app.** A portion counts as one meal. Kilograms are divided by 420 g per meal (the WRAP conversion factor for redistributed surplus food), rounded down. Pieces, boxes, packs and litres are reported as *not converted*, not guessed.

### Food safety
- Preparation time, pickup deadline, storage conditions and allergens are mandatory, both in the form validation and in the security rules.
- A prominent allergen warning appears on listings, along with disclaimers that ShareBite does not inspect or certify food, and a food-safety guidelines page in Profile.
- **Personal allergen profile.** Users choose the allergens they avoid from the 14 major allergens. Listings whose declaration mentions them are flagged on cards and the detail screen (synonyms such as ghee for milk are recognised, and negations such as "nut-free" are ignored). The profile is kept **on the device only**, because allergy information is health data and Firestore profiles are readable by other signed-in users.
- Weather near the pickup point (Open-Meteo) adds handling advice, e.g. bring a cool box when it's hot and the food is perishable.

---

## Technology stack

| Area | Choice |
|---|---|
| Language / UI | Java 17, XML layouts, ViewBinding, Material Components 3 |
| Architecture | MVVM: Activities/Fragments → ViewModels (LiveData) → repository interfaces → Firebase implementations |
| Backend | Firebase Authentication, Cloud Firestore, Firebase Storage |
| Concurrency | `ExecutorService` via a lifecycle-scoped `BackgroundTaskRunner`, plus Firebase async Tasks |
| Networking | `HttpURLConnection` + Gson (Nominatim geocoding, Open-Meteo weather) |
| Images / lists | Glide, RecyclerView with `ListAdapter`/DiffUtil |
| Maps & location | osmdroid (OpenStreetMap, no API key) and Fused Location Provider |
| Build | Gradle 8.13 (Groovy DSL), AGP 8.13.2, compileSdk/targetSdk 36, minSdk 24 |
| Tests | JUnit 4 (JVM), `@firebase/rules-unit-testing` + Firestore emulator (rules) |

---

## Architecture

```
 UI layer (ui/*)                     Domain (domain/*)                 Data (data/*)
 ─────────────────                   ───────────────────               ─────────────────────────
 Activities / Fragments  ──observe──▶ ViewModels ──call──▶ interfaces ◀──implements── Firestore*Repository
   ViewBinding layouts                (LiveData,           FoodRepository            FirebaseAuthRepository
   adapters/*                          UiState, Event)     ReservationRepository     FirebaseImageStorage
                                        │                  UserRepository            NominatimGeocodingService
                                        │                  NotificationRepository    OpenMeteoWeatherService
                                        ▼                  GeocodingService ...        └─ HttpJsonClient (Gson)
                                 BackgroundTaskRunner     domain/service (pure Java):
                                 (ExecutorService)        ListingValidator, ListingQueryEngine,
                                                          ImpactCalculator, DistanceCalculator,
                                                          FoodHandlingAdvisor, ReservationActions
```

- **Models** (`data/model`) hold the business rules. Examples: `FoodListing.reserve()`/`release()`, `Reservation.transitionTo()`, and the role behaviour on `User` (`canReserve`, `maxReservableQuantity`, `displayName`), which `Provider`, `Receiver` and `Charity` override. The repositories call these methods inside transactions, and the unit tests exercise them directly.
- **Generics:** `Repository<T, ID>`, `ResultCallback<T>`, `BaseFirestoreRepository<T extends FirestoreDocument>` (shared mapping and realtime queries), and `UiState<T>` / `Event<T>`.
- **Lifecycle:** realtime listeners return a `Subscription` that ViewModels cancel in `onCleared()`. Each ViewModel's `BackgroundTaskRunner` cancels in-flight work and shuts its executor down in `onCleared()`. Results are posted to the main thread and dropped after shutdown.
- **What runs on the background executor:** image decoding and compression, HTTP calls, filtering/sorting/distance for Explore and Home, and impact aggregation. Firestore and Storage calls use the SDK's own async Tasks. Nothing blocking runs on the main thread.
- `ServiceLocator` is the single place that knows the Firebase implementations. `SessionManager` keeps the signed-in profile live, so a charity sees its verification approval immediately.

Package layout:

```
com.sharebite.app
├── data/       model · repository · remote (+ dto)
├── domain/     interfaces · service
├── ui/         auth · home · explore · listing · reservation · impact · profile · widget
├── adapter/    FoodCardAdapter · MyListingAdapter · ReservationAdapter · NotificationAdapter
├── utils/      ServiceLocator · SessionManager · ErrorMessages · Formatters · LocationHelper ...
└── workers/    BackgroundTaskRunner · ImageCompressionTask
```

---

## Firestore data model

All dates are Firestore `Timestamp`s; enums are stored by name.

| Collection | Key fields |
|---|---|
| `users/{uid}` | `uid, fullName, email, phone, role (PROVIDER/RECEIVER/CHARITY), providerType, organizationName, verificationStatus (NOT_REQUIRED/UNVERIFIED/PENDING/VERIFIED/REJECTED), profileImageUrl, createdAt, active` |
| `foodListings/{listingId}` | `listingId, providerId, providerName, providerType, title, description, category, imageUrl, totalQuantity, availableQuantity, unit, distributionType, originalPrice, discountedPrice, preparationTime, pickupStartTime, pickupDeadline, pickupAddress, latitude, longitude, storageInformation, allergens, foodSafetyNotes, safetyDeclared, status, lastReservationId, createdAt, updatedAt` |
| `reservations/{reservationId}` | `reservationId, listingId, providerId, receiverId, receiverName, receiverRole, receiverPhone, quantity, distributionType, unitPrice, totalPrice, status, reservedAt, confirmedAt, rejectedAt, cancelledAt, collectedAt, collectionConfirmedBy`, plus listing details copied in for history (`listingTitle, category, unit, pickupAddress, pickupDeadline, ...`) |
| `notifications/{notificationId}` | `notificationId, recipientId, senderId, title, message, type, relatedListingId, relatedReservationId, isRead, createdAt` |
| `charityVerifications/{orgId}` | `organizationId, organizationName, registrationNumber, contactPerson, contactPhone, organizationAddress, description, submittedDocuments, verificationStatus, reviewerNote, submittedAt, reviewedAt` |

`lastReservationId` on a listing points to the reservation that last moved its stock. It lets the security rules verify that a stock change matches a reservation created or cancelled in the same transaction.

**Indexes:** every query is either a single equality filter or two equality filters, so no composite indexes are needed (`firestore.indexes.json` is empty on purpose). Sorting happens on the client.

---

## Security rules

[`firestore.rules`](firestore.rules) is the authority for every critical rule:

- Users can create only their own profile, can't register as verified, and can't change their `role`, `email` or `verificationStatus`. A charity can move itself to `PENDING` only in the same batch as its submission. Reviewers (custom claim `admin`) can do anything.
- **Open listings (`status == AVAILABLE`) are readable without an account**, so the app can be browsed as a guest. Closed listings, profiles, reservations and notifications still need sign-in, and guests can't write anything.
- Only providers create listings, and only for themselves. Food-safety fields, time ordering, prices and coordinates are validated. Owners can't reopen closed listings or drop below already-reserved units.
- **Non-owners can change a listing only by:**
  - *reserving:* a new `PENDING` reservation of theirs, created in the same transaction, for exactly the quantity removed, before the deadline, while `AVAILABLE`; or
  - *cancelling:* their own active reservation, returning exactly its quantity.
- Reservations enforce the role limits server-side: 5 units for individuals, charity-only for verified charities, providers can't reserve. The price must match the listing, and only the documented status transitions are allowed, by the right party.
- Notifications are accepted only between the two parties of the reservation they describe. Recipients can only mark them read.

[`storage.rules`](storage.rules): providers write only to `listing_images/{theirUid}/`, images under 5 MB. Verification documents are private to the organisation and reviewers.

---

## Getting started

Requirements: Android Studio (Ladybug or newer) or the command line, JDK 17, Android SDK 36.

```bash
git clone <your-repo-url> ShareBite
cd ShareBite
./gradlew assembleDebug          # builds without Firebase config, too
./gradlew testDebugUnitTest      # JVM unit tests
```

Without `app/google-services.json`, the build still succeeds. The app then opens a **"Connect a Firebase project"** screen instead of crashing. ShareBite never fakes a backend.

To use the app, either connect a Firebase project (below) or run it against the local emulators (next section).

---

## Firebase setup

1. **Create a project** at <https://console.firebase.google.com>.
2. **Add an Android app** with package name `com.sharebite.app`. Download `google-services.json` and save it as `app/google-services.json`. It is git-ignored; see `app/google-services.json.example` for the shape.
3. **Authentication** → Sign-in method → enable **Email/Password**.
4. **Firestore Database** → Create database (production mode, any region).
5. **Storage** → Get started. New Firebase projects need the **Blaze (pay-as-you-go)** plan for Cloud Storage; light usage stays within the no-cost quota. Without Storage the app still works, but listings have no photos and uploads show a clear error.
6. **Deploy the rules** (Firebase CLI; it's included as a dev dependency in `firebase-tests/`):
   ```bash
   cd firebase-tests && npm install
   npx firebase login
   npx firebase use --add          # choose your project
   npx firebase deploy --only firestore:rules,firestore:indexes,storage --config ../firebase.json
   ```
   Alternatively, paste `firestore.rules` and `storage.rules` into the console's *Rules* tabs.
7. Rebuild and install: `./gradlew installDebug`.

### Reviewing charities

Verification is deliberately manual; there is no in-app admin panel. Approve a charity in either of two ways:

- **Firebase console** (console edits bypass rules): in `charityVerifications/{uid}` set `verificationStatus` = `VERIFIED` (or `REJECTED` with a `reviewerNote`) and set `reviewedAt`. Then set `users/{uid}.verificationStatus` to the same value. The charity's app updates live.
- **Admin claim:** give a reviewer account the custom claim `{ admin: true }` with the Admin SDK (`admin.auth().setCustomUserClaims(uid, { admin: true })`). The rules then let that account update verification documents.

---

## Running against the Firebase emulators (no cloud project needed)

Debug builds can run against the local Firebase Emulator Suite (Auth, Firestore, Storage) under the demo project `demo-sharebite`. Every Firebase operation in the app then works locally, with no `google-services.json`.

```bash
# terminal 1: start the emulators (Emulator UI at http://localhost:4000)
cd firebase-tests && npm install
npx firebase emulators:start --project demo-sharebite --config ../firebase.json

# terminal 2: build a debug APK wired to the emulators
./gradlew installDebug -Psharebite.useEmulators=true
#   physical device on the same Wi-Fi:  -Psharebite.emulatorHost=192.168.x.y
```

The Android emulator reaches your machine at `10.0.2.2`, which is the default. Cleartext HTTP to the emulator hosts is allowed **in debug builds only** (`src/debug/res/xml/network_security_config.xml`).

---

## Demo data and guest browsing

- **Guest mode:** the Welcome screen has *Browse food without an account*. Guests can see Home, Explore, the map and listing details. Reserving, reservations, notifications and impact need an account, and the app sends guests to log in. A guest has no profile and no uid, and nothing is stored for them.
- **Demo seed:** `firebase-tests/seed.mjs` signs up four demo accounts (two providers, a receiver and a charity) and creates six clearly labelled demo listings plus one pending reservation in the project from `app/google-services.json`. It uses the normal client SDK, so every write passes through your deployed security rules (no admin key). Add `SEED_PLACE="Your City"` to pin the listings around a real place found via OpenStreetMap.

```bash
cd firebase-tests && npm install
node seed.mjs            # or:  SEED_PLACE="Kolkata, India" node seed.mjs
```

The shared demo password is generated on first run and saved to the git-ignored `firebase-tests/.seed-credentials.json` (or set `SEED_PASSWORD`). The demo emails use the reserved `.example` domain. The charity starts `UNVERIFIED`, because verification is a reviewer step (see above). Re-running leaves existing demo data alone. Delete the demo documents in the console to reseed.

---

## External APIs

Both APIs are free and need no key. Calls run on a background executor through `HttpJsonClient` (`HttpURLConnection`, 10 s connect / 15 s read timeouts). Responses are parsed with Gson into DTOs and mapped to app models. Network failures, timeouts, non-2xx responses, malformed JSON and empty results each produce a specific `ApiException` and a user-facing message.

| API | Used for | Where |
|---|---|---|
| [Nominatim](https://nominatim.org/) (OpenStreetMap) | Address search and reverse geocoding when pinning a pickup location | `NominatimGeocodingService`, `LocationPickerActivity` |
| [Open-Meteo](https://open-meteo.com/) | Current temperature and conditions at the pickup point, turned into food-handling advice | `OpenMeteoWeatherService`, `FoodHandlingAdvisor`, listing detail |

Nominatim's usage policy asks for an identifying `User-Agent` (sent as `ShareBite-Android/<version> (com.sharebite.app)`) and no more than one request per second. The app only calls it on explicit user action. If either API is unavailable, the app keeps working: the address can be typed manually, and the weather card shows "unavailable" with a retry.

---

## Testing

```bash
./gradlew testDebugUnitTest        # 65 JVM unit tests
./gradlew lintDebug                # Android lint (0 errors)

cd firebase-tests && npm install
npm test                           # 42 security-rules tests on the Firestore emulator
```

- **JVM unit tests** (`app/src/test`) cover:
  - listing stock rules (reserve/release/edit, expiry);
  - the reservation state machine and double-collection prevention;
  - role polymorphism;
  - registration and listing validation;
  - filtering/sorting/distance (no fabricated distances);
  - impact aggregation (collected-only, monthly buckets);
  - Gson parsing of both APIs, including malformed bodies;
  - the reservation action policy and image down-sampling;
  - allergen matching, the meals estimate, pickup-code generation/parsing and reminder timing.
- **Rules tests** (`firebase-tests/rules.test.mjs`) replay the app's real write shapes: the reserve transaction, cancel with stock release, provider decline, listing withdrawal, collection by either party, and charity verification. They also try tampered writes: overselling, self-verification, skipping states, cancelling someone else's reservation, and spoofed notifications. Newer suites cover guest browsing, the private pickup code (only the receiver reads it, it can't be replaced, and verification needs the matching code), and chat access.

---

## Screenshots

No screenshots yet. The app hasn't been run on a device in the environment where it was built (see limitations). To add them, run the app (the emulator mode above is the quickest way), capture the Home, Explore, Listing detail, Reserve sheet, Reservations and Impact screens, save them under `docs/screenshots/`, and link them here.

---

## Known limitations

- **Device testing is limited.** The app has been installed and launched on a physical Android phone against a real Firebase project (seeded demo data), but the newer features (allergen profile, reminders, QR hand-over, chat, motion) have not been exercised by hand yet. Automated checks: `assembleDebug`, lint (0 errors), 65 unit tests and 42 rules tests on the Firestore emulator.
- **Reminders are local.** They are scheduled on the receiver's phone, fire within a few minutes of the target time (Android may batch background work), and need notification permission on Android 13+.
- **Allergen warnings depend on the provider's declaration.** They can't detect undeclared allergens, and the app says so.
- **No Cloud Functions.** Expiry is enforced by the rules (no reservations after the deadline) and shown by the client (an open listing past its deadline displays as *Expired*). The stored status changes to `EXPIRED` when the provider next opens the app.
- **In-app notifications only.** There is no push (FCM), so notifications appear while the app is open.
- **Charity review is manual** (console or admin claim). There is no in-app reviewer UI.
- **Discounted food is paid in person.** There is no payment integration, by design.
- Quantities are whole units (portions, pieces, kg, …).
- Listings are loaded with a single `status == AVAILABLE` query and filtered on the device. This suits a community-sized deployment; a large deployment would need pagination and geo-queries (e.g. geohashes).
- Distance is straight-line (haversine), not route distance.
- Profiles (name and phone) are readable by any signed-in user, so pickups can be coordinated. A production app might split public and private profile data.
- Light theme only.
- Firebase Storage requires the Blaze plan on new Firebase projects.
