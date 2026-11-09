// Demo-data seeder for a REAL Firebase project (the one in app/google-services.json).
//
//   cd firebase-tests
//   npm install
//   python -I demo-assets/prepare_images.py   # once: openly licensed food photos + generated logos
//   node seed.mjs                              # creates / refreshes the demo data
//
// Everything is written through the normal client SDK as the demo users themselves, so every write
// passes your deployed security rules exactly like the app's writes (no admin key is used).
//
// What it creates (all fictional businesses and people; e-mails use the reserved .example domain;
// addresses are marked "(demo)"; map pins are spread around Dhaka neighbourhoods):
//   - 8 food providers with generated logos, cover photos and an "about" text
//   - ~29 food listings with real, openly licensed photos (credited in the app)
//   - 3 receivers and 2 charities (charities stay UNVERIFIED: verification is a reviewer step)
//   - reservations in different states (pending, confirmed with a chat, collected) so the
//     Reservations and Impact screens have content
//
// Re-run it before a demo: expired demo listings are recreated with fresh pickup windows; anything
// still open is left alone. The shared demo password comes from SEED_PASSWORD or the git-ignored
// .seed-credentials.json (generated on first run).

import { randomBytes } from 'node:crypto';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { initializeApp } from 'firebase/app';
import { createUserWithEmailAndPassword, getAuth, signInWithEmailAndPassword } from 'firebase/auth';
import {
  Bytes, Timestamp, collection, deleteDoc, doc, getDoc, getDocs, getFirestore, query, runTransaction,
  serverTimestamp, setDoc, terminate, updateDoc, where,
} from 'firebase/firestore';

const here = dirname(fileURLToPath(import.meta.url));
const configPath = join(here, '..', 'app', 'google-services.json');
if (!existsSync(configPath)) {
  console.error('app/google-services.json not found. Add your Firebase config first (see README).');
  process.exit(1);
}
const gs = JSON.parse(readFileSync(configPath, 'utf8'));
const firebaseConfig = {
  apiKey: gs.client[0].api_key[0].current_key,
  projectId: gs.project_info.project_id,
  appId: gs.client[0].client_info.mobilesdk_app_id,
  authDomain: `${gs.project_info.project_id}.firebaseapp.com`,
};

const credentialsPath = join(here, '.seed-credentials.json');
let password = process.env.SEED_PASSWORD;
if (!password && existsSync(credentialsPath)) password = JSON.parse(readFileSync(credentialsPath, 'utf8')).password;
if (!password) {
  password = `Demo-${randomBytes(6).toString('base64url')}-1a`;
  writeFileSync(credentialsPath, JSON.stringify({ password }, null, 2));
}

const assets = join(here, 'demo-assets');
const creditsPath = join(assets, 'credits.json');
const credits = existsSync(creditsPath) ? JSON.parse(readFileSync(creditsPath, 'utf8')) : {};
const photoPath = (key) => join(assets, 'photos', `${key}.jpg`);
const logoPath = (key) => join(assets, 'logos', `${key}.jpg`);

// ---- places (approximate neighbourhood centres in Dhaka) -------------------------------------

const AREAS = {
  banani: [23.7937, 90.4066], dhanmondi: [23.7461, 90.3742], gulshan: [23.7949, 90.4143],
  shahbag: [23.7383, 90.3958], mirpur: [23.8069, 90.3687], lalbagh: [23.7189, 90.3880],
  uttara: [23.8759, 90.3795], mohammadpur: [23.7662, 90.3589],
};
const PHONE = '0000000000'; // placeholder, not a real number

// ---- people ------------------------------------------------------------------------------------

const providers = [
  { key: 'golden_crust', email: 'bakery.demo@sharebite.example', fullName: 'Tanvir Hasan', org: 'Golden Crust Bakery',
    type: 'BAKERY', area: 'banani', cover: 'croissants',
    bio: 'Neighbourhood bakery baking bread, croissants and cakes daily. Whatever is left at closing time goes on ShareBite.' },
  { key: 'spice_route', email: 'kitchen.demo@sharebite.example', fullName: 'Farhana Akter', org: 'Spice Route Kitchen',
    type: 'RESTAURANT', area: 'dhanmondi', cover: 'biryani',
    bio: 'Home-style curries, biryani and fresh naan. We cook to order and share surplus portions every evening.' },
  { key: 'green_leaf', email: 'cafe.demo@sharebite.example', fullName: 'Sabrina Rahman', org: 'Green Leaf Cafe',
    type: 'RESTAURANT', area: 'gulshan', cover: 'salad',
    bio: 'Salads, sandwiches and pasta made fresh each morning. Unsold lunch items are shared in the afternoon.' },
  { key: 'campus_bites', email: 'canteen.demo@sharebite.example', fullName: 'Mahmudul Karim', org: 'Campus Bites Canteen',
    type: 'CAMPUS_CANTEEN', area: 'shahbag', cover: 'fried_rice',
    bio: 'Student canteen serving affordable meals. Extra trays after lunch rush are shared with students and charities.' },
  { key: 'fresh_basket', email: 'grocer.demo@sharebite.example', fullName: 'Rafiq Uddin', org: 'Fresh Basket Grocers',
    type: 'OTHER', area: 'mirpur', cover: 'vegetables',
    bio: 'Local fruit and vegetable shop. Produce that is ripe but not pretty enough for the shelf gets a second chance here.' },
  { key: 'royal_biryani', email: 'biryani.demo@sharebite.example', fullName: 'Imran Chowdhury', org: 'Royal Biryani House',
    type: 'RESTAURANT', area: 'lalbagh', cover: 'kacchi',
    bio: 'Old Dhaka style kacchi and roast. Large pots mean leftovers on busy days, packed fresh for pickup.' },
  { key: 'sweet_moments', email: 'patisserie.demo@sharebite.example', fullName: 'Nusrat Jahan', org: 'Sweet Moments Patisserie',
    type: 'BAKERY', area: 'uttara', cover: 'cupcakes',
    bio: 'Cupcakes, doughnuts and cookies. End-of-day treats are discounted or given to charities.' },
  { key: 'nadias_kitchen', email: 'homekitchen.demo@sharebite.example', fullName: 'Nadia Islam', org: null,
    type: 'HOUSEHOLD', area: 'mohammadpur', cover: 'pitha',
    bio: 'Home cook who sometimes makes too much. Pitha, curries and rice shared with neighbours.' },
];
const receivers = [
  { key: 'receiver', email: 'receiver.demo@sharebite.example', fullName: 'Arif Hossain' },
  { key: 'receiver2', email: 'receiver2.demo@sharebite.example', fullName: 'Ayesha Siddiqua' },
  { key: 'receiver3', email: 'receiver3.demo@sharebite.example', fullName: 'Rahim Mia' },
];
const charities = [
  { key: 'charity', email: 'charity.demo@sharebite.example', fullName: 'Kamal Ahmed', org: 'Community Meals Group' },
  { key: 'charity2', email: 'charity2.demo@sharebite.example', fullName: 'Shirin Begum', org: 'Hope Food Bank Network' },
];

// ---- listings ----------------------------------------------------------------------------------
// [id, provider, photo, title, category, qty, unit, type, price [orig, disc], hoursLeft, preparedHoursAgo, storage, allergens, description]

const H = 3600 * 1000;
const listings = [
  ['croissants', 'golden_crust', 'croissants', 'Butter croissants', 'BAKERY', 12, 'PIECES', 'DISCOUNTED', [120, 40], 4, 5, 'Room temperature, paper bag', 'Gluten, milk, eggs', 'Flaky all-butter croissants from this morning. Best warmed for a few minutes.'],
  ['bread', 'golden_crust', 'bread', 'Day-end bread loaves', 'BAKERY', 10, 'PIECES', 'FREE', null, 3, 7, 'Room temperature in paper bags', 'Gluten, may contain sesame', 'White and whole-wheat loaves baked today and unsold at closing time.'],
  ['cinnamon', 'golden_crust', 'cinnamon', 'Cinnamon rolls', 'BAKERY', 8, 'PIECES', 'FREE', null, 26, 6, 'Cool, dry place; eat within a day', 'Gluten, milk, eggs', 'Soft cinnamon rolls with a light glaze, packed in pairs.'],
  ['chocolate_cake', 'golden_crust', 'chocolate_cake', 'Chocolate cake slices', 'BAKERY', 6, 'BOXES', 'DISCOUNTED', [450, 150], 30, 8, 'Refrigerate below 5 C', 'Gluten, milk, eggs, may contain nuts', 'Boxed slices of chocolate fudge cake from a cancelled order.'],
  ['biryani', 'spice_route', 'biryani', 'Chicken biryani portions', 'COOKED_MEALS', 15, 'PORTIONS', 'FREE', null, 2.5, 1.5, 'Keep hot or refrigerate; eat within 2 hours', 'Milk (yoghurt marinade)', 'Fragrant chicken biryani with egg, packed in sealed boxes.'],
  ['veg_curry', 'spice_route', 'veg_curry', 'Mixed vegetable curry', 'COOKED_MEALS', 12, 'PORTIONS', 'FREE', null, 3, 1, 'Keep hot or refrigerate', 'None declared', 'Mild vegetable curry with potatoes, peas and cauliflower. Vegan.'],
  ['dal', 'spice_route', 'dal', 'Dal with rice', 'COOKED_MEALS', 20, 'PORTIONS', 'CHARITY', null, 5, 1, 'Keep hot; eat within 2 hours', 'None declared', 'Yellow lentil dal with plain rice, packed for community kitchens.'],
  ['naan', 'spice_route', 'naan', 'Fresh naan bread', 'BAKERY', 25, 'PIECES', 'DISCOUNTED', [30, 10], 4, 1, 'Wrapped in foil; reheat on a pan', 'Gluten, milk', 'Tandoor naan from tonight\'s service.'],
  ['sandwich', 'green_leaf', 'sandwich', 'Assorted sandwiches', 'COOKED_MEALS', 14, 'PIECES', 'DISCOUNTED', [220, 90], 5, 4, 'Refrigerate; eat today', 'Gluten, milk, eggs, mustard', 'Chicken, egg and veggie sandwiches from the lunch counter.'],
  ['salad', 'green_leaf', 'salad', 'Garden salad bowls', 'VEGETABLES', 8, 'PORTIONS', 'FREE', null, 4, 3, 'Refrigerate below 5 C', 'Mustard (dressing on the side)', 'Crisp salad bowls with dressing packed separately.'],
  ['pasta', 'green_leaf', 'pasta', 'Tomato basil pasta', 'COOKED_MEALS', 10, 'PORTIONS', 'FREE', null, 3, 2, 'Refrigerate; reheat thoroughly', 'Gluten, milk', 'Penne in tomato and basil sauce with parmesan.'],
  ['muffins', 'green_leaf', 'muffins', 'Blueberry muffins', 'BAKERY', 12, 'PIECES', 'DISCOUNTED', [150, 50], 28, 6, 'Room temperature, sealed box', 'Gluten, milk, eggs', 'Blueberry muffins from the morning batch.'],
  ['fried_rice', 'campus_bites', 'fried_rice', 'Vegetable fried rice', 'COOKED_MEALS', 30, 'PORTIONS', 'CHARITY', null, 3, 1, 'Keep hot or refrigerate', 'Soy, eggs', 'Large trays of vegetable fried rice for groups and charities.'],
  ['samosa', 'campus_bites', 'samosa', 'Potato samosas', 'COOKED_MEALS', 40, 'PIECES', 'FREE', null, 2, 2, 'Room temperature; eat today', 'Gluten', 'Crispy potato and pea samosas from the afternoon snack counter.'],
  ['khichuri', 'campus_bites', 'khichuri', 'Khichuri', 'COOKED_MEALS', 18, 'PORTIONS', 'FREE', null, 4, 1, 'Keep hot; eat within 2 hours', 'Milk (ghee)', 'Rice and lentil khichuri with a little ghee.'],
  ['paratha', 'campus_bites', 'paratha', 'Parathas', 'BAKERY', 30, 'PIECES', 'DISCOUNTED', [20, 8], 5, 2, 'Wrapped; reheat on a pan', 'Gluten', 'Layered parathas from breakfast service.'],
  ['bananas', 'fresh_basket', 'bananas', 'Ripe bananas', 'FRUITS', 6, 'KILOGRAMS', 'FREE', null, 48, 10, 'Room temperature', 'None declared', 'Very ripe bananas, perfect for eating today or baking.'],
  ['mangoes', 'fresh_basket', 'mangoes', 'Seasonal mangoes', 'FRUITS', 8, 'KILOGRAMS', 'DISCOUNTED', [200, 80], 72, 12, 'Cool place; wash before eating', 'None declared', 'Sweet mangoes with small blemishes. Price per kg.'],
  ['vegetables', 'fresh_basket', 'vegetables', 'Mixed vegetable bags', 'VEGETABLES', 15, 'KILOGRAMS', 'CHARITY', null, 96, 12, 'Cool, ventilated place', 'None declared', 'Bags of mixed vegetables for community kitchens to collect in bulk.'],
  ['yogurt', 'fresh_basket', 'yogurt', 'Yoghurt cups', 'DAIRY', 24, 'PACKS', 'DISCOUNTED', [60, 20], 40, 2, 'Refrigerate below 5 C', 'Milk', 'Sealed yoghurt cups, two days before best-before.'],
  ['kacchi', 'royal_biryani', 'kacchi', 'Kacchi biryani', 'COOKED_MEALS', 20, 'PORTIONS', 'FREE', null, 3, 1, 'Keep hot; eat within 2 hours', 'Milk (yoghurt), may contain nuts', 'Mutton kacchi biryani with potato, from a large pot.'],
  ['chicken_roast', 'royal_biryani', 'chicken_roast', 'Chicken roast', 'COOKED_MEALS', 12, 'PORTIONS', 'DISCOUNTED', [250, 90], 4, 1, 'Keep hot or refrigerate', 'Milk, may contain nuts', 'Wedding-style chicken roast portions.'],
  ['phirni', 'royal_biryani', 'phirni', 'Phirni cups', 'DAIRY', 16, 'PACKS', 'FREE', null, 20, 3, 'Refrigerate below 5 C', 'Milk, nuts', 'Rice pudding with cardamom in clay cups.'],
  ['cupcakes', 'sweet_moments', 'cupcakes', 'Cupcake boxes', 'BAKERY', 10, 'BOXES', 'DISCOUNTED', [400, 120], 24, 8, 'Cool, dry place', 'Gluten, milk, eggs', 'Boxes of four assorted cupcakes.'],
  ['doughnuts', 'sweet_moments', 'doughnuts', 'Glazed doughnuts', 'BAKERY', 18, 'PIECES', 'FREE', null, 6, 6, 'Room temperature; eat today', 'Gluten, milk, eggs, soy', 'Glazed and chocolate doughnuts from today.'],
  ['cookies', 'sweet_moments', 'cookies', 'Chocolate chip cookies', 'BAKERY', 12, 'PACKS', 'CHARITY', null, 120, 10, 'Sealed packs; keep dry', 'Gluten, milk, eggs, soy', 'Packs of six cookies for charities to distribute.'],
  ['pitha', 'nadias_kitchen', 'pitha', 'Homemade pitha', 'BAKERY', 20, 'PIECES', 'FREE', null, 8, 3, 'Room temperature; eat today', 'Gluten, milk, coconut', 'Traditional winter pitha made at home, more than we can eat.'],
  ['chicken_curry', 'nadias_kitchen', 'chicken_curry', 'Chicken curry', 'COOKED_MEALS', 6, 'PORTIONS', 'FREE', null, 3, 1, 'Refrigerate; reheat thoroughly', 'None declared', 'Home-cooked chicken curry. Bring your own container if you can.'],
  ['fish_curry', 'nadias_kitchen', 'fish_curry', 'Fish curry with rice', 'COOKED_MEALS', 5, 'PORTIONS', 'FREE', null, 2, 1, 'Refrigerate; eat today', 'Fish', 'Rui fish curry with rice, packed in two-portion boxes.'],
];

// ---- helpers ---------------------------------------------------------------------------------------

async function signUpOrIn(app, email) {
  const auth = getAuth(app);
  try {
    return (await createUserWithEmailAndPassword(auth, email, password)).user;
  } catch (e) {
    if (e.code !== 'auth/email-already-in-use') throw e;
    return (await signInWithEmailAndPassword(auth, email, password)).user;
  }
}

const sessions = {};
async function account(a, role) {
  const app = initializeApp(firebaseConfig, a.key);
  const user = await signUpOrIn(app, a.email);
  const db = getFirestore(app);
  const ref = doc(db, 'users', user.uid);
  if (!(await getDoc(ref)).exists()) {
    await setDoc(ref, {
      uid: user.uid, fullName: a.fullName, email: a.email, phone: PHONE, organizationName: a.org ?? null,
      verificationStatus: role === 'CHARITY' ? 'UNVERIFIED' : 'NOT_REQUIRED', profileImageUrl: null,
      createdAt: serverTimestamp(), active: true, role, ...(role === 'PROVIDER' ? { providerType: a.type } : {}),
    });
    console.log(`+ ${role.toLowerCase()} ${a.email}`);
  } else if (role === 'PROVIDER') {
    // Older seed runs used other demo names for the first two providers.
    await updateDoc(ref, { fullName: a.fullName, organizationName: a.org ?? null, providerType: a.type });
  }
  sessions[a.key] = { ...a, role, uid: user.uid, db };
  return sessions[a.key];
}

/** Uploads a JPEG into images/ (the app's free-plan photo store) once; returns its URL. */
async function image(s, id, file, purpose) {
  if (!existsSync(file)) return null;
  const ref = doc(s.db, 'images', id);
  if (!(await getDoc(ref)).exists()) {
    const bytes = readFileSync(file);
    if (bytes.length > 740 * 1024) throw new Error(`${file} is too large for Firestore`);
    await setDoc(ref, {
      imageId: id, ownerId: s.uid, purpose, contentType: 'image/jpeg',
      data: Bytes.fromUint8Array(new Uint8Array(bytes)), createdAt: serverTimestamp(),
    });
  }
  return `firestore-image://images/${id}`;
}

// Deterministic small offset so pins of one provider don't overlap exactly.
const jitter = (seed) => (((seed * 9301 + 49297) % 233280) / 233280 - 0.5) * 0.004;

// ---- run ---------------------------------------------------------------------------------------------

for (const p of providers) await account(p, 'PROVIDER');
for (const r of receivers) await account(r, 'RECEIVER');
for (const c of charities) await account(c, 'CHARITY');

// Storefronts: logo, cover, about.
for (const p of providers) {
  const s = sessions[p.key];
  const logo = await image(s, `demo-logo-${p.key}`, logoPath(p.key), 'logo');
  const cover = await image(s, `demo-cover-${p.key}`, photoPath(p.cover), 'cover');
  await updateDoc(doc(s.db, 'users', s.uid), { bio: p.bio, profileImageUrl: logo, coverImageUrl: cover });
  s.logo = logo;
  s.display = p.org ?? p.fullName;
}
console.log('Storefronts ready.');

// Remove first-version demo listings once they have expired.
for (const key of ['golden_crust', 'spice_route']) {
  const s = sessions[key];
  for (const id of ['demo-bread', 'demo-pastries', 'demo-curry', 'demo-fruit', 'demo-yoghurt', 'demo-veg']) {
    const snap = await getDoc(doc(s.db, 'foodListings', id)).catch(() => null);
    if (snap?.exists() && snap.data().providerId === s.uid && snap.data().pickupDeadline.toMillis() < Date.now()) {
      await deleteDoc(snap.ref);
    }
  }
}

let created = 0, kept = 0, idx = 0;
for (const [key, owner, photo, title, category, qty, unit, type, price, hoursLeft, preparedAgo, storage, allergens, description] of listings) {
  idx++;
  const s = sessions[owner];
  const id = `demo2-${key}`;
  const ref = doc(s.db, 'foodListings', id);
  const snap = await getDoc(ref);
  if (snap.exists()) {
    const d = snap.data();
    const open = (d.status === 'AVAILABLE' || d.status === 'FULLY_RESERVED') && d.pickupDeadline.toMillis() > Date.now() + H;
    if (open) { kept++; continue; }
    await deleteDoc(ref); // expired or closed: recreate with a fresh pickup window
  }
  const img = await image(s, `demo-photo-${photo}`, photoPath(photo), 'listing');
  const [lat, lng] = AREAS[s.area];
  const now = Date.now();
  await setDoc(ref, {
    listingId: id, providerId: s.uid, providerName: s.display, providerType: s.type,
    providerLogoUrl: s.logo, title, description, category, imageUrl: img,
    imageCredit: img && credits[photo] ? credits[photo].credit : null,
    totalQuantity: qty, availableQuantity: qty, unit, distributionType: type,
    originalPrice: type === 'DISCOUNTED' ? price[0] : 0, discountedPrice: type === 'DISCOUNTED' ? price[1] : 0,
    preparationTime: Timestamp.fromMillis(now - preparedAgo * H), pickupStartTime: Timestamp.fromMillis(now - 0.25 * H),
    pickupDeadline: Timestamp.fromMillis(now + hoursLeft * H),
    pickupAddress: `${s.display}, ${s.area[0].toUpperCase()}${s.area.slice(1)}, Dhaka (demo)`,
    latitude: +(lat + jitter(idx)).toFixed(6), longitude: +(lng + jitter(idx * 7)).toFixed(6),
    storageInformation: storage, allergens, foodSafetyNotes: null, safetyDeclared: true,
    status: 'AVAILABLE', lastReservationId: null, createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
  });
  created++;
}
console.log(`Listings: ${created} created, ${kept} still open.`);

// ---- reservations in different states ----------------------------------------------------------------

async function reserve(rKey, listingKey, qty, resId) {
  const r = sessions[rKey];
  const resRef = doc(r.db, 'reservations', resId);
  if ((await getDoc(resRef).catch(() => null))?.exists()) return false;
  await runTransaction(r.db, async (tx) => {
    const lRef = doc(r.db, 'foodListings', `demo2-${listingKey}`);
    const l = (await tx.get(lRef)).data();
    if (!l || l.status !== 'AVAILABLE' || l.availableQuantity < qty) throw new Error(`cannot reserve ${listingKey}`);
    const unitPrice = l.distributionType === 'DISCOUNTED' ? l.discountedPrice : 0;
    const newQty = l.availableQuantity - qty;
    tx.set(resRef, {
      reservationId: resId, listingId: l.listingId, listingTitle: l.title, listingImageUrl: l.imageUrl ?? null,
      category: l.category, unit: l.unit, providerId: l.providerId, providerName: l.providerName,
      receiverId: r.uid, receiverName: r.org ?? r.fullName, receiverRole: r.role, receiverPhone: PHONE,
      quantity: qty, distributionType: l.distributionType, unitPrice, totalPrice: qty * unitPrice,
      pickupAddress: l.pickupAddress, pickupStartTime: l.pickupStartTime, pickupDeadline: l.pickupDeadline,
      status: 'PENDING', reservedAt: Timestamp.now(), confirmedAt: null, rejectedAt: null, cancelledAt: null,
      collectedAt: null, collectionConfirmedBy: null,
    });
    tx.set(doc(r.db, 'reservations', resId, 'private', 'pickup'), { code: randomBytes(16).toString('hex') });
    tx.update(lRef, {
      availableQuantity: newQty, status: newQty === 0 ? 'FULLY_RESERVED' : 'AVAILABLE',
      lastReservationId: resId, updatedAt: serverTimestamp(),
    });
    tx.set(doc(r.db, 'notifications', `n-${resId}`), {
      notificationId: `n-${resId}`, recipientId: l.providerId, senderId: r.uid, title: 'New reservation request',
      message: `${r.org ?? r.fullName} wants ${qty} x ${l.title}.`, type: 'RESERVATION_REQUEST',
      relatedListingId: l.listingId, relatedReservationId: resId, isRead: false, createdAt: serverTimestamp(),
    });
  });
  return true;
}

const providerOf = (listingKey) => sessions[listings.find((l) => l[0] === listingKey)[1]];
async function confirm(listingKey, resId) {
  await updateDoc(doc(providerOf(listingKey).db, 'reservations', resId), { status: 'CONFIRMED', confirmedAt: serverTimestamp() });
}
async function collect(listingKey, resId) {
  await updateDoc(doc(providerOf(listingKey).db, 'reservations', resId),
    { status: 'COLLECTED', collectedAt: serverTimestamp(), collectionConfirmedBy: 'PROVIDER' });
}
async function chat(resId, lines) {
  for (const [i, [who, text]] of lines.entries()) {
    const s = sessions[who];
    const id = `${resId}-m${i}`;
    const ref = doc(s.db, 'reservations', resId, 'messages', id);
    if ((await getDoc(ref).catch(() => null))?.exists()) continue;
    await setDoc(ref, { messageId: id, senderId: s.uid, senderName: s.display ?? s.org ?? s.fullName, text, createdAt: serverTimestamp() });
  }
}

const stamp = new Date().toISOString().slice(0, 10).replace(/-/g, '');
const plan = [
  // [receiver, listing, qty, final state]
  ['receiver', 'biryani', 2, 'PENDING'],
  ['receiver', 'croissants', 2, 'CONFIRMED'],
  ['receiver', 'samosa', 4, 'COLLECTED'],
  ['receiver', 'bananas', 2, 'COLLECTED'],
  ['receiver2', 'pasta', 1, 'CONFIRMED'],
  ['receiver2', 'doughnuts', 3, 'COLLECTED'],
  ['receiver3', 'khichuri', 2, 'PENDING'],
  ['receiver3', 'pitha', 4, 'COLLECTED'],
  ['charity', 'kacchi', 8, 'COLLECTED'],
  ['charity2', 'bread', 5, 'CONFIRMED'],
];
let reservations = 0;
for (const [i, [rKey, listingKey, qty, state]] of plan.entries()) {
  const resId = `demo2-${stamp}-${i}`;
  try {
    if (!(await reserve(rKey, listingKey, qty, resId))) continue;
    if (state !== 'PENDING') await confirm(listingKey, resId);
    if (state === 'COLLECTED') await collect(listingKey, resId);
    reservations++;
  } catch (e) {
    console.log(`  skipped reservation ${listingKey}: ${e.message}`);
  }
}
await chat(`demo2-${stamp}-1`, [
  ['receiver', 'Hi! I can come at 6:30, is that okay?'],
  ['golden_crust', 'Sure, they will be packed at the counter. Ask for Tanvir.'],
  ['receiver', 'Great, thank you!'],
]).catch(() => {});
console.log(`Reservations created: ${reservations}.`);

console.log('\nDemo logins (all use the same password):');
for (const a of [...providers, ...receivers, ...charities]) console.log(`  ${sessions[a.key].role.padEnd(8)} ${a.email}`);
console.log(`\nPassword: ${password}  (also in firebase-tests/.seed-credentials.json, git-ignored)`);
console.log('Charities are UNVERIFIED on purpose: set users/<uid>.verificationStatus to VERIFIED in the console to try charity-only food.');

await Promise.all(Object.values(sessions).map((s) => terminate(s.db)));
process.exit(0);
