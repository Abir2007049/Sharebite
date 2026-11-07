// Demo-data seeder for a REAL Firebase project (the one in app/google-services.json).
//
//   cd firebase-tests
//   npm install
//   node seed.mjs                       # no map pins
//   SEED_PLACE="Kolkata, India" node seed.mjs   # pins spread around a place found via OpenStreetMap
//
// It signs up four demo accounts through the normal client SDK, so every write goes through your
// deployed security rules exactly like the app's writes (no admin key is needed or used):
//   - two providers, one receiver, one charity (left UNVERIFIED - verification is a reviewer step)
//   - a handful of clearly labelled demo listings and one pending reservation
//
// The shared demo password comes from SEED_PASSWORD, or from the git-ignored file
// .seed-credentials.json (generated on first run). It is never printed to logs of other tools and
// never committed. Re-running is safe: existing accounts and listings are left alone.

import { randomBytes } from 'node:crypto';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { initializeApp } from 'firebase/app';
import {
  createUserWithEmailAndPassword, getAuth, signInWithEmailAndPassword,
} from 'firebase/auth';
import {
  Timestamp, doc, getDoc, getFirestore, runTransaction, serverTimestamp, setDoc, terminate,
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

// ---- password -----------------------------------------------------------------------------

const credentialsPath = join(here, '.seed-credentials.json');
let password = process.env.SEED_PASSWORD;
if (!password && existsSync(credentialsPath)) password = JSON.parse(readFileSync(credentialsPath, 'utf8')).password;
if (!password) {
  password = `Demo-${randomBytes(6).toString('base64url')}-1a`;
  writeFileSync(credentialsPath, JSON.stringify({ password }, null, 2));
}

// ---- optional real place for map pins ------------------------------------------------------

async function geocode(place) {
  const url = `https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&q=${encodeURIComponent(place)}`;
  const res = await fetch(url, { headers: { 'User-Agent': 'ShareBite-demo-seed/1.0 (student project)' } });
  if (!res.ok) throw new Error(`Geocoding failed: HTTP ${res.status}`);
  const hit = (await res.json())[0];
  if (!hit) throw new Error(`Could not find "${place}" on OpenStreetMap.`);
  return { lat: Number(hit.lat), lng: Number(hit.lon), name: hit.display_name };
}

let center = null;
if (process.env.SEED_PLACE) {
  center = await geocode(process.env.SEED_PLACE);
  console.log(`Using ${center.name} (${center.lat.toFixed(4)}, ${center.lng.toFixed(4)}) as the centre for demo pickups.`);
} else {
  console.log('No SEED_PLACE set: demo listings will have no map pins (addresses only).');
}
// ~1 km steps; these are pickup points for DEMO listings only, not anyone's location.
const around = (dLat, dLng) => (center
  ? { latitude: +(center.lat + dLat * 0.009).toFixed(6), longitude: +(center.lng + dLng * 0.009).toFixed(6) }
  : { latitude: null, longitude: null });

// ---- accounts --------------------------------------------------------------------------------

const PHONE = '0000000000'; // placeholder, not a real number

const accounts = [
  { key: 'bakery', role: 'PROVIDER', providerType: 'BAKERY', fullName: 'Demo Bakery Owner',
    organizationName: 'Demo Corner Bakery', email: 'bakery.demo@sharebite.example' },
  { key: 'kitchen', role: 'PROVIDER', providerType: 'RESTAURANT', fullName: 'Demo Kitchen Manager',
    organizationName: 'Demo Lakeside Kitchen', email: 'kitchen.demo@sharebite.example' },
  { key: 'receiver', role: 'RECEIVER', fullName: 'Demo Receiver', organizationName: null,
    email: 'receiver.demo@sharebite.example' },
  { key: 'charity', role: 'CHARITY', fullName: 'Demo Charity Coordinator',
    organizationName: 'Demo Community Meals Group', email: 'charity.demo@sharebite.example' },
];

async function signUpOrIn(app, email) {
  const auth = getAuth(app);
  try {
    return (await createUserWithEmailAndPassword(auth, email, password)).user;
  } catch (e) {
    if (e.code !== 'auth/email-already-in-use') throw e;
    try {
      return (await signInWithEmailAndPassword(auth, email, password)).user;
    } catch (e2) {
      throw new Error(`${email} already exists with a different password. Set SEED_PASSWORD to it, or delete the account in the console. (${e2.code})`);
    }
  }
}

const sessions = {};
for (const a of accounts) {
  const app = initializeApp(firebaseConfig, a.key);
  const user = await signUpOrIn(app, a.email);
  const db = getFirestore(app);
  const ref = doc(db, 'users', user.uid);
  if (!(await getDoc(ref)).exists()) {
    await setDoc(ref, {
      uid: user.uid, fullName: a.fullName, email: a.email, phone: PHONE,
      organizationName: a.organizationName,
      verificationStatus: a.role === 'CHARITY' ? 'UNVERIFIED' : 'NOT_REQUIRED',
      profileImageUrl: null, createdAt: serverTimestamp(), active: true, role: a.role,
      ...(a.role === 'PROVIDER' ? { providerType: a.providerType } : {}),
    });
    console.log(`Created ${a.role.toLowerCase()} account ${a.email}`);
  } else {
    console.log(`Account ${a.email} already set up`);
  }
  sessions[a.key] = { ...a, uid: user.uid, db };
}

// ---- listings --------------------------------------------------------------------------------

const H = 3600 * 1000;
const at = (ms) => Timestamp.fromMillis(Date.now() + ms);

function listing(id, owner, f) {
  const discounted = f.distributionType === 'DISCOUNTED';
  return {
    listingId: id, providerId: owner.uid, providerName: owner.organizationName,
    providerType: owner.providerType, title: f.title, description: f.description,
    category: f.category, imageUrl: null, totalQuantity: f.qty, availableQuantity: f.qty, unit: f.unit,
    distributionType: f.distributionType,
    originalPrice: discounted ? f.originalPrice : 0, discountedPrice: discounted ? f.discountedPrice : 0,
    preparationTime: at(-f.preparedHoursAgo * H), pickupStartTime: at(-0.5 * H),
    pickupDeadline: at(f.untilHours * H), pickupAddress: f.address,
    ...around(f.dLat, f.dLng),
    storageInformation: f.storage, allergens: f.allergens, foodSafetyNotes: f.notes ?? null,
    safetyDeclared: true, status: 'AVAILABLE', lastReservationId: null,
    createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
  };
}

const demoListings = [
  ['demo-bread', 'bakery', { title: 'Day-end bread loaves', category: 'BAKERY', qty: 12, unit: 'PIECES',
    distributionType: 'FREE', description: 'Whole-wheat and white loaves baked this morning, unsold at closing time.',
    preparedHoursAgo: 6, untilHours: 5, address: 'Demo Corner Bakery, Market Street (demo listing)',
    dLat: 0.4, dLng: 0.3, storage: 'Room temperature in paper bags', allergens: 'Gluten, may contain sesame' }],
  ['demo-pastries', 'bakery', { title: 'Mixed pastry box', category: 'BAKERY', qty: 8, unit: 'BOXES',
    distributionType: 'DISCOUNTED', originalPrice: 12, discountedPrice: 4,
    description: 'Assorted pastries from today, boxed in fours. Best eaten the same day.',
    preparedHoursAgo: 5, untilHours: 4, address: 'Demo Corner Bakery, Market Street (demo listing)',
    dLat: 0.4, dLng: 0.3, storage: 'Cool, dry place; eat today', allergens: 'Gluten, milk, eggs, nuts' }],
  ['demo-curry', 'kitchen', { title: 'Vegetable curry and rice', category: 'COOKED_MEALS', qty: 20, unit: 'PORTIONS',
    distributionType: 'FREE', description: 'Freshly cooked vegetarian curry with rice, packed in sealed containers.',
    preparedHoursAgo: 1.5, untilHours: 3, address: 'Demo Lakeside Kitchen, Lake Road (demo listing)',
    dLat: -0.6, dLng: 0.5, storage: 'Keep hot or refrigerate; eat within 2 hours', allergens: 'Dairy (ghee)',
    notes: 'Cooked to order today; reheat thoroughly before eating.' }],
  ['demo-fruit', 'kitchen', { title: 'Surplus seasonal fruit', category: 'FRUITS', qty: 15, unit: 'KILOGRAMS',
    distributionType: 'FREE', description: 'Ripe bananas, apples and oranges from a catering order that was reduced.',
    preparedHoursAgo: 8, untilHours: 24, address: 'Demo Lakeside Kitchen, Lake Road (demo listing)',
    dLat: -0.6, dLng: 0.5, storage: 'Cool place; wash before eating', allergens: 'None declared' }],
  ['demo-yoghurt', 'kitchen', { title: 'Yoghurt cups', category: 'DAIRY', qty: 30, unit: 'PACKS',
    distributionType: 'DISCOUNTED', originalPrice: 3, discountedPrice: 1,
    description: 'Sealed single-serve yoghurt cups, two days before their best-before date.',
    preparedHoursAgo: 2, untilHours: 30, address: 'Demo Lakeside Kitchen, Lake Road (demo listing)',
    dLat: -0.6, dLng: 0.5, storage: 'Refrigerate at or below 5 C', allergens: 'Milk' }],
  ['demo-veg', 'bakery', { title: 'Vegetable crates for community kitchens', category: 'VEGETABLES', qty: 40, unit: 'KILOGRAMS',
    distributionType: 'CHARITY', description: 'Mixed vegetables for verified charities and community kitchens to collect in bulk.',
    preparedHoursAgo: 4, untilHours: 48, address: 'Demo Corner Bakery loading bay (demo listing)',
    dLat: 0.4, dLng: 0.3, storage: 'Cool, ventilated place', allergens: 'None declared' }],
];

const byId = {};
for (const [id, ownerKey, f] of demoListings) {
  const owner = sessions[ownerKey];
  const ref = doc(owner.db, 'foodListings', id);
  if ((await getDoc(ref)).exists()) {
    console.log(`Listing ${id} already exists`);
  } else {
    await setDoc(ref, listing(id, owner, f));
    console.log(`Created listing ${id}`);
  }
  byId[id] = owner;
}

// ---- one pending reservation (the client's real transaction) ---------------------------------

const resId = 'demo-res-1';
const receiver = sessions.receiver;
if ((await getDoc(doc(receiver.db, 'reservations', resId)).catch(() => null))?.exists()) {
  console.log('Demo reservation already exists');
} else {
  const qty = 2;
  await runTransaction(receiver.db, async (tx) => {
    const lRef = doc(receiver.db, 'foodListings', 'demo-bread');
    const l = (await tx.get(lRef)).data();
    if (l.availableQuantity < qty) throw new Error('not enough stock for the demo reservation');
    const newQty = l.availableQuantity - qty;
    tx.set(doc(receiver.db, 'reservations', resId), {
      reservationId: resId, listingId: l.listingId, listingTitle: l.title, listingImageUrl: null,
      category: l.category, unit: l.unit, providerId: l.providerId, providerName: l.providerName,
      receiverId: receiver.uid, receiverName: receiver.fullName, receiverRole: 'RECEIVER',
      receiverPhone: PHONE, quantity: qty, distributionType: l.distributionType, unitPrice: 0, totalPrice: 0,
      pickupAddress: l.pickupAddress, pickupStartTime: l.pickupStartTime, pickupDeadline: l.pickupDeadline,
      status: 'PENDING', reservedAt: Timestamp.now(), confirmedAt: null, rejectedAt: null, cancelledAt: null,
      collectedAt: null, collectionConfirmedBy: null,
    });
    tx.update(lRef, {
      availableQuantity: newQty, status: newQty === 0 ? 'FULLY_RESERVED' : 'AVAILABLE',
      lastReservationId: resId, updatedAt: serverTimestamp(),
    });
    tx.set(doc(receiver.db, 'notifications', `n-${resId}`), {
      notificationId: `n-${resId}`, recipientId: l.providerId, senderId: receiver.uid,
      title: 'New reservation request', message: `${receiver.fullName} wants ${qty} x ${l.title}.`,
      type: 'RESERVATION_REQUEST', relatedListingId: l.listingId, relatedReservationId: resId,
      isRead: false, createdAt: serverTimestamp(),
    });
  });
  console.log('Created a pending demo reservation (Demo Receiver -> Day-end bread loaves)');
}

// ---- summary -----------------------------------------------------------------------------------

console.log('\nDemo logins (all use the same password):');
for (const a of accounts) console.log(`  ${a.role.padEnd(8)} ${a.email}`);
console.log(`\nPassword: ${password}`);
console.log('(Stored in firebase-tests/.seed-credentials.json, which is git-ignored.)');
console.log('The charity account is UNVERIFIED on purpose: set verificationStatus to VERIFIED on its');
console.log('users/<uid> document in the Firebase console to try charity-only listings.');

await Promise.all(Object.values(sessions).map((s) => terminate(s.db)));
process.exit(0);
