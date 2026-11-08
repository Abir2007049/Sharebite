// Security-rules tests for ShareBite, executed against the Firestore emulator.
//
//   cd firebase-tests && npm install && npm test
//
// Documents are shaped exactly like the ones the Android client writes (see the Java models and
// Firestore*Repository classes), so passing tests mean the app's real writes are accepted and
// tampered writes are rejected.

import { readFileSync } from 'node:fs';
import { after, before, beforeEach, describe, test } from 'node:test';
import {
  assertFails, assertSucceeds, initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  Timestamp, collection, doc, getDoc, getDocs, query, runTransaction, serverTimestamp,
  setDoc, updateDoc, where, writeBatch,
} from 'firebase/firestore';

const PROJECT = 'demo-sharebite';
let env;

const HOUR = 3600 * 1000;
const ts = (offsetMs) => Timestamp.fromMillis(Date.now() + offsetMs);

// ---- fixtures mirroring the Java models -------------------------------------------------

function userDoc(uid, role, extra = {}) {
  return {
    uid, fullName: `User ${uid}`, email: `${uid}@example.com`, phone: '+8801700000000',
    organizationName: role === 'RECEIVER' ? null : `${uid} Org`,
    verificationStatus: role === 'CHARITY' ? 'UNVERIFIED' : 'NOT_REQUIRED',
    profileImageUrl: null, createdAt: serverTimestamp(), active: true, role,
    ...(role === 'PROVIDER' ? { providerType: 'RESTAURANT' } : {}),
    ...extra,
  };
}

function listingDoc(id, providerId, extra = {}) {
  return {
    listingId: id, providerId, providerName: 'Green Bakery', providerType: 'BAKERY',
    title: 'Fresh bread loaves', description: 'Whole wheat loaves baked this morning.',
    category: 'BAKERY', imageUrl: null, totalQuantity: 10, availableQuantity: 10, unit: 'PIECES',
    distributionType: 'FREE', originalPrice: 0, discountedPrice: 0,
    preparationTime: ts(-2 * HOUR), pickupStartTime: ts(-HOUR), pickupDeadline: ts(3 * HOUR),
    pickupAddress: '12 Lake Road, Dhaka', latitude: 23.78, longitude: 90.41,
    storageInformation: 'Room temperature, sealed bags', allergens: 'Gluten', foodSafetyNotes: null,
    safetyDeclared: true, status: 'AVAILABLE', lastReservationId: null,
    createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
    ...extra,
  };
}

function reservationDoc(id, listing, receiverId, receiverRole, quantity, extra = {}) {
  const unitPrice = listing.distributionType === 'DISCOUNTED' ? listing.discountedPrice : 0;
  return {
    reservationId: id, listingId: listing.listingId, listingTitle: listing.title, listingImageUrl: null,
    category: listing.category, unit: listing.unit, providerId: listing.providerId,
    providerName: listing.providerName, receiverId, receiverName: `User ${receiverId}`, receiverRole,
    receiverPhone: '+8801700000000', quantity, distributionType: listing.distributionType,
    unitPrice, totalPrice: quantity * unitPrice, pickupAddress: listing.pickupAddress,
    pickupStartTime: listing.pickupStartTime, pickupDeadline: listing.pickupDeadline, status: 'PENDING',
    reservedAt: Timestamp.now(), confirmedAt: null, rejectedAt: null, cancelledAt: null, collectedAt: null,
    collectionConfirmedBy: null,
    ...extra,
  };
}

function notificationDoc(id, recipientId, senderId, type, listingId, reservationId) {
  return {
    notificationId: id, recipientId, senderId, title: 'Update', message: 'Something happened.', type,
    relatedListingId: listingId, relatedReservationId: reservationId, isRead: false, createdAt: serverTimestamp(),
  };
}

const db = (uid) => env.authenticatedContext(uid, { email: `${uid}@example.com` }).firestore();

/** Seeds documents with rules disabled. */
async function seed(fn) {
  await env.withSecurityRulesDisabled(async (ctx) => fn(ctx.firestore()));
}

/** The client's reserve transaction (FirestoreReservationRepository#reserve). */
async function reserve(fs, uid, role, listingId, quantity, resId, overrides = {}) {
  return runTransaction(fs, async (tx) => {
    const lRef = doc(fs, 'foodListings', listingId);
    const snap = await tx.get(lRef);
    const l = snap.data();
    const newQty = overrides.newQty ?? l.availableQuantity - quantity;
    const r = reservationDoc(resId, l, uid, role, quantity, overrides.reservation);
    tx.set(doc(fs, 'reservations', resId), r);
    tx.update(lRef, {
      availableQuantity: newQty, status: newQty === 0 ? 'FULLY_RESERVED' : 'AVAILABLE',
      lastReservationId: resId, updatedAt: serverTimestamp(),
    });
    tx.set(doc(fs, 'notifications', `n-${resId}`),
      notificationDoc(`n-${resId}`, l.providerId, uid, 'RESERVATION_REQUEST', listingId, resId));
    if (overrides.pickupCode !== undefined) {
      tx.set(doc(fs, 'reservations', resId, 'private', 'pickup'), { code: overrides.pickupCode });
    }
  });
}

const CODE = '0123456789abcdef0123456789abcdef';

// ---- lifecycle ---------------------------------------------------------------------------

before(async () => {
  env = await initializeTestEnvironment({
    projectId: PROJECT,
    firestore: { rules: readFileSync('../firestore.rules', 'utf8'), host: '127.0.0.1', port: 8080 },
  });
});

after(async () => { await env?.cleanup(); });

beforeEach(async () => {
  await env.clearFirestore();
  await seed(async (fs) => {
    await setDoc(doc(fs, 'users', 'provider1'), userDoc('provider1', 'PROVIDER'));
    await setDoc(doc(fs, 'users', 'provider2'), userDoc('provider2', 'PROVIDER'));
    await setDoc(doc(fs, 'users', 'alice'), userDoc('alice', 'RECEIVER'));
    await setDoc(doc(fs, 'users', 'bob'), userDoc('bob', 'RECEIVER'));
    await setDoc(doc(fs, 'users', 'charityU'), userDoc('charityU', 'CHARITY'));
    await setDoc(doc(fs, 'users', 'charityV'), userDoc('charityV', 'CHARITY', { verificationStatus: 'VERIFIED' }));
    await setDoc(doc(fs, 'foodListings', 'free1'), listingDoc('free1', 'provider1'));
    await setDoc(doc(fs, 'foodListings', 'charity1'), listingDoc('charity1', 'provider1', { distributionType: 'CHARITY', totalQuantity: 50, availableQuantity: 50 }));
    await setDoc(doc(fs, 'foodListings', 'disc1'), listingDoc('disc1', 'provider1', { distributionType: 'DISCOUNTED', originalPrice: 120, discountedPrice: 45.5 }));
    await setDoc(doc(fs, 'foodListings', 'expired1'), listingDoc('expired1', 'provider1', { pickupStartTime: ts(-5 * HOUR), pickupDeadline: ts(-HOUR) }));
  });
});

// ---- users ---------------------------------------------------------------------------------

describe('users', () => {
  test('a new user can create their own profile', async () => {
    await assertSucceeds(setDoc(doc(db('newbie'), 'users', 'newbie'), userDoc('newbie', 'RECEIVER')));
  });

  test('a charity cannot register as already verified', async () => {
    await assertFails(setDoc(doc(db('fake'), 'users', 'fake'), userDoc('fake', 'CHARITY', { verificationStatus: 'VERIFIED' })));
  });

  test('profiles cannot be created for someone else', async () => {
    await assertFails(setDoc(doc(db('alice'), 'users', 'mallory'), userDoc('mallory', 'RECEIVER')));
  });

  test('users can edit their name and phone but not their role or verification', async () => {
    const fs = db('alice');
    await assertSucceeds(updateDoc(doc(fs, 'users', 'alice'), { fullName: 'Alice A.', phone: '+8801811111111' }));
    await assertFails(updateDoc(doc(fs, 'users', 'alice'), { role: 'PROVIDER' }));
    await assertFails(updateDoc(doc(db('charityU'), 'users', 'charityU'), { verificationStatus: 'VERIFIED' }));
  });

  test('a charity moves to PENDING only together with a verification submission', async () => {
    const fs = db('charityU');
    await assertFails(updateDoc(doc(fs, 'users', 'charityU'), { verificationStatus: 'PENDING' }));
    const batch = writeBatch(fs);
    batch.set(doc(fs, 'charityVerifications', 'charityU'), {
      organizationId: 'charityU', organizationName: 'Food For All', registrationNumber: 'NGO-1234',
      contactPerson: 'Chris', contactPhone: '+8801700000000', organizationAddress: '1 Main St',
      description: 'We run a community kitchen.', submittedDocuments: [], verificationStatus: 'PENDING',
      reviewerNote: null, submittedAt: Timestamp.now(), reviewedAt: null,
    });
    batch.update(doc(fs, 'users', 'charityU'), { verificationStatus: 'PENDING' });
    await assertSucceeds(batch.commit());
  });

  test('a charity cannot write an already-approved verification document', async () => {
    await assertFails(setDoc(doc(db('charityU'), 'charityVerifications', 'charityU'), {
      organizationId: 'charityU', organizationName: 'Food For All', registrationNumber: 'NGO-1234',
      contactPerson: 'Chris', contactPhone: '+8801700000000', organizationAddress: '1 Main St',
      description: 'x', submittedDocuments: [], verificationStatus: 'VERIFIED', reviewerNote: null,
      submittedAt: Timestamp.now(), reviewedAt: null,
    }));
  });
});

// ---- listings ------------------------------------------------------------------------------

describe('foodListings', () => {
  test('a provider can publish a complete listing', async () => {
    await assertSucceeds(setDoc(doc(db('provider1'), 'foodListings', 'new1'), listingDoc('new1', 'provider1')));
  });

  test('receivers cannot publish listings', async () => {
    await assertFails(setDoc(doc(db('alice'), 'foodListings', 'new2'), listingDoc('new2', 'alice')));
  });

  test('food-safety information is mandatory', async () => {
    const fs = db('provider1');
    await assertFails(setDoc(doc(fs, 'foodListings', 'a'), listingDoc('a', 'provider1', { allergens: '' })));
    await assertFails(setDoc(doc(fs, 'foodListings', 'b'), listingDoc('b', 'provider1', { storageInformation: '' })));
    await assertFails(setDoc(doc(fs, 'foodListings', 'c'), listingDoc('c', 'provider1', { safetyDeclared: false })));
  });

  test('a listing cannot be published with a past deadline or as already reserved', async () => {
    const fs = db('provider1');
    await assertFails(setDoc(doc(fs, 'foodListings', 'd'), listingDoc('d', 'provider1', { pickupStartTime: ts(-5 * HOUR), pickupDeadline: ts(-HOUR) })));
    await assertFails(setDoc(doc(fs, 'foodListings', 'e'), listingDoc('e', 'provider1', { availableQuantity: 3 })));
  });

  test('discounted listings need a discount below the original price', async () => {
    const fs = db('provider1');
    await assertFails(setDoc(doc(fs, 'foodListings', 'f'), listingDoc('f', 'provider1', { distributionType: 'DISCOUNTED', originalPrice: 50, discountedPrice: 60 })));
    await assertSucceeds(setDoc(doc(fs, 'foodListings', 'g'), listingDoc('g', 'provider1', { distributionType: 'DISCOUNTED', originalPrice: 50, discountedPrice: 20 })));
  });

  test('only the owner can edit a listing', async () => {
    await assertSucceeds(updateDoc(doc(db('provider1'), 'foodListings', 'free1'), { title: 'Bread loaves (fresh)', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db('provider2'), 'foodListings', 'free1'), { title: 'Hijacked' }));
    await assertFails(updateDoc(doc(db('alice'), 'foodListings', 'free1'), { title: 'Hijacked' }));
  });

  test('a receiver cannot change stock without a matching reservation', async () => {
    await assertFails(updateDoc(doc(db('alice'), 'foodListings', 'free1'), {
      availableQuantity: 0, status: 'FULLY_RESERVED', lastReservationId: 'ghost', updatedAt: serverTimestamp(),
    }));
  });

  test('signed-in users can read listings of any status', async () => {
    await assertSucceeds(getDocs(query(collection(db('alice'), 'foodListings'), where('status', '==', 'AVAILABLE'))));
    await seed((fs) => setDoc(doc(fs, 'foodListings', 'done1'), listingDoc('done1', 'provider1', { status: 'COMPLETED' })));
    await assertSucceeds(getDoc(doc(db('alice'), 'foodListings', 'done1')));
  });

  test('guests can browse open listings but nothing else', async () => {
    const guest = env.unauthenticatedContext().firestore();
    await assertSucceeds(getDocs(query(collection(guest, 'foodListings'), where('status', '==', 'AVAILABLE'))));
    await assertSucceeds(getDoc(doc(guest, 'foodListings', 'free1')));
    // Closed listings, profiles, reservations and notifications stay private.
    await seed((fs) => setDoc(doc(fs, 'foodListings', 'done1'), listingDoc('done1', 'provider1', { status: 'COMPLETED' })));
    await assertFails(getDoc(doc(guest, 'foodListings', 'done1')));
    await assertFails(getDocs(collection(guest, 'foodListings')));
    await assertFails(getDoc(doc(guest, 'users', 'provider1')));
    await assertFails(getDocs(collection(guest, 'reservations')));
    await assertFails(getDocs(collection(guest, 'notifications')));
  });

  test('guests cannot write listings or reserve stock', async () => {
    const guest = env.unauthenticatedContext().firestore();
    await assertFails(setDoc(doc(guest, 'foodListings', 'g1'), listingDoc('g1', 'provider1')));
    await assertFails(updateDoc(doc(guest, 'foodListings', 'free1'), {
      availableQuantity: 0, status: 'FULLY_RESERVED', lastReservationId: 'ghost', updatedAt: serverTimestamp(),
    }));
    await assertFails(updateDoc(doc(guest, 'foodListings', 'free1'), { title: 'Hacked title' }));
  });
});

// ---- reservations --------------------------------------------------------------------------

describe('reservations', () => {
  test('a receiver can reserve: reservation, stock update and notification commit together', async () => {
    await assertSucceeds(reserve(db('alice'), 'alice', 'RECEIVER', 'free1', 3, 'r1'));
    await seed(async (fs) => {
      const l = (await getDoc(doc(fs, 'foodListings', 'free1'))).data();
      if (l.availableQuantity !== 7) throw new Error(`expected 7 left, got ${l.availableQuantity}`);
    });
  });

  test('the stock decrement must equal the reserved quantity', async () => {
    await assertFails(reserve(db('alice'), 'alice', 'RECEIVER', 'free1', 1, 'r2', { newQty: 9 + 5 }));
    await assertFails(reserve(db('alice'), 'alice', 'RECEIVER', 'free1', 3, 'r3', { newQty: 9 }));
  });

  test('a reservation cannot exceed the available stock', async () => {
    await seed(async (fs) => updateDoc(doc(fs, 'foodListings', 'free1'), { availableQuantity: 2 }));
    await assertFails(reserve(db('alice'), 'alice', 'RECEIVER', 'free1', 3, 'r4'));
  });

  test('individual receivers are limited to 5 units per reservation', async () => {
    await assertFails(reserve(db('alice'), 'alice', 'RECEIVER', 'charity1', 1, 'r5'));
    await seed(async (fs) => setDoc(doc(fs, 'foodListings', 'big'), listingDoc('big', 'provider1', { totalQuantity: 40, availableQuantity: 40 })));
    await assertFails(reserve(db('alice'), 'alice', 'RECEIVER', 'big', 6, 'r6'));
    await assertSucceeds(reserve(db('alice'), 'alice', 'RECEIVER', 'big', 5, 'r7'));
  });

  test('charity donations require a verified charity', async () => {
    await assertFails(reserve(db('charityU'), 'charityU', 'CHARITY', 'charity1', 20, 'r8'));
    await assertSucceeds(reserve(db('charityV'), 'charityV', 'CHARITY', 'charity1', 20, 'r9'));
  });

  test('expired listings cannot be reserved', async () => {
    await assertFails(reserve(db('alice'), 'alice', 'RECEIVER', 'expired1', 1, 'r10'));
  });

  test('discounted totals must match the listing price', async () => {
    await assertSucceeds(reserve(db('alice'), 'alice', 'RECEIVER', 'disc1', 2, 'r11'));
    await assertFails(reserve(db('bob'), 'bob', 'RECEIVER', 'disc1', 2, 'r12', { reservation: { totalPrice: 1 } }));
  });

  test('providers cannot reserve listings', async () => {
    await assertFails(reserve(db('provider2'), 'provider2', 'PROVIDER', 'free1', 1, 'r13'));
  });

  test('only the two parties can read a reservation', async () => {
    await reserve(db('alice'), 'alice', 'RECEIVER', 'free1', 1, 'r14');
    await assertSucceeds(getDoc(doc(db('alice'), 'reservations', 'r14')));
    await assertSucceeds(getDoc(doc(db('provider1'), 'reservations', 'r14')));
    await assertFails(getDoc(doc(db('bob'), 'reservations', 'r14')));
    await assertSucceeds(getDocs(query(collection(db('alice'), 'reservations'), where('receiverId', '==', 'alice'))));
    await assertFails(getDocs(collection(db('bob'), 'reservations')));
  });

  test('the provider confirms and marks collected; collection cannot happen twice', async () => {
    await reserve(db('alice'), 'alice', 'RECEIVER', 'free1', 2, 'r15');
    const p = db('provider1');
    await assertFails(updateDoc(doc(db('alice'), 'reservations', 'r15'), { status: 'CONFIRMED', confirmedAt: Timestamp.now() }));
    await assertSucceeds(updateDoc(doc(p, 'reservations', 'r15'), { status: 'CONFIRMED', confirmedAt: Timestamp.now() }));
    await assertSucceeds(updateDoc(doc(p, 'reservations', 'r15'), { status: 'COLLECTED', collectedAt: Timestamp.now(), collectionConfirmedBy: 'PROVIDER' }));
    await assertFails(updateDoc(doc(p, 'reservations', 'r15'), { status: 'COLLECTED', collectedAt: Timestamp.now(), collectionConfirmedBy: 'PROVIDER' }));
    await assertFails(updateDoc(doc(db('alice'), 'reservations', 'r15'), { status: 'CANCELLED', cancelledAt: Timestamp.now() }));
  });

  test('a pending request cannot jump straight to collected', async () => {
    await reserve(db('alice'), 'alice', 'RECEIVER', 'free1', 1, 'r16');
    await assertFails(updateDoc(doc(db('provider1'), 'reservations', 'r16'), { status: 'COLLECTED', collectedAt: Timestamp.now(), collectionConfirmedBy: 'PROVIDER' }));
  });

  test('a receiver cancelling must return exactly the reserved stock', async () => {
    await reserve(db('alice'), 'alice', 'RECEIVER', 'free1', 4, 'r17');
    const fs = db('alice');
    // Status change alone (stock not returned) is rejected.
    await assertFails(updateDoc(doc(fs, 'reservations', 'r17'), { status: 'CANCELLED', cancelledAt: Timestamp.now() }));
    // Returning more than was reserved is rejected.
    await assertFails(runTransaction(fs, async (tx) => {
      tx.update(doc(fs, 'reservations', 'r17'), { status: 'CANCELLED', cancelledAt: Timestamp.now() });
      tx.update(doc(fs, 'foodListings', 'free1'), { availableQuantity: 10 + 0 + 4, lastReservationId: 'r17', updatedAt: serverTimestamp() });
    }));
    // The client's real cancel transaction succeeds.
    await assertSucceeds(runTransaction(fs, async (tx) => {
      const l = (await tx.get(doc(fs, 'foodListings', 'free1'))).data();
      tx.update(doc(fs, 'reservations', 'r17'), { status: 'CANCELLED', cancelledAt: Timestamp.now() });
      tx.update(doc(fs, 'foodListings', 'free1'), {
        availableQuantity: l.availableQuantity + 4, status: 'AVAILABLE', lastReservationId: 'r17', updatedAt: serverTimestamp(),
      });
      tx.set(doc(fs, 'notifications', 'n-cancel'), notificationDoc('n-cancel', 'provider1', 'alice', 'RESERVATION_CANCELLED', 'free1', 'r17'));
    }));
  });

  test('the provider declines a request and releases its stock in one transaction', async () => {
    await reserve(db('alice'), 'alice', 'RECEIVER', 'free1', 3, 'r21');
    const fs = db('provider1');
    await assertSucceeds(runTransaction(fs, async (tx) => {
      const l = (await tx.get(doc(fs, 'foodListings', 'free1'))).data();
      tx.update(doc(fs, 'reservations', 'r21'), { status: 'REJECTED', rejectedAt: Timestamp.now() });
      tx.update(doc(fs, 'foodListings', 'free1'), { availableQuantity: l.availableQuantity + 3, status: 'AVAILABLE', updatedAt: serverTimestamp() });
      tx.set(doc(fs, 'notifications', 'n-rej'), notificationDoc('n-rej', 'alice', 'provider1', 'RESERVATION_REJECTED', 'free1', 'r21'));
    }));
  });

  test('withdrawing a listing declines pending and cancels confirmed reservations', async () => {
    await reserve(db('alice'), 'alice', 'RECEIVER', 'free1', 2, 'r22');
    await reserve(db('bob'), 'bob', 'RECEIVER', 'free1', 1, 'r23');
    const fs = db('provider1');
    await updateDoc(doc(fs, 'reservations', 'r23'), { status: 'CONFIRMED', confirmedAt: Timestamp.now() });
    await assertSucceeds(runTransaction(fs, async (tx) => {
      const l = (await tx.get(doc(fs, 'foodListings', 'free1'))).data();
      tx.update(doc(fs, 'reservations', 'r22'), { status: 'REJECTED', rejectedAt: Timestamp.now() });
      tx.update(doc(fs, 'reservations', 'r23'), { status: 'CANCELLED', cancelledAt: Timestamp.now() });
      tx.set(doc(fs, 'notifications', 'n-w1'), notificationDoc('n-w1', 'alice', 'provider1', 'LISTING_CANCELLED', 'free1', 'r22'));
      tx.set(doc(fs, 'notifications', 'n-w2'), notificationDoc('n-w2', 'bob', 'provider1', 'LISTING_CANCELLED', 'free1', 'r23'));
      tx.update(doc(fs, 'foodListings', 'free1'), { status: 'CANCELLED', availableQuantity: l.availableQuantity + 3, updatedAt: serverTimestamp() });
    }));
    // A withdrawn listing cannot be reopened or reserved.
    await assertFails(updateDoc(doc(fs, 'foodListings', 'free1'), { status: 'AVAILABLE' }));
    await assertFails(reserve(db('alice'), 'alice', 'RECEIVER', 'free1', 1, 'r24'));
  });

  test('a receiver can confirm their own pickup of a confirmed reservation', async () => {
    await reserve(db('alice'), 'alice', 'RECEIVER', 'free1', 1, 'r25');
    await updateDoc(doc(db('provider1'), 'reservations', 'r25'), { status: 'CONFIRMED', confirmedAt: Timestamp.now() });
    const fs = db('alice');
    await assertFails(updateDoc(doc(fs, 'reservations', 'r25'), { status: 'COLLECTED', collectedAt: Timestamp.now(), collectionConfirmedBy: 'PROVIDER' }));
    await assertSucceeds(runTransaction(fs, async (tx) => {
      tx.update(doc(fs, 'reservations', 'r25'), { status: 'COLLECTED', collectedAt: Timestamp.now(), collectionConfirmedBy: 'RECEIVER' });
      tx.set(doc(fs, 'notifications', 'n-col'), notificationDoc('n-col', 'provider1', 'alice', 'COLLECTION_COMPLETED', 'free1', 'r25'));
    }));
  });

  test('editing quantity must preserve units already reserved', async () => {
    await reserve(db('alice'), 'alice', 'RECEIVER', 'free1', 4, 'r26'); // 6 of 10 left
    const fs = db('provider1');
    await assertFails(updateDoc(doc(fs, 'foodListings', 'free1'), { totalQuantity: 12, availableQuantity: 12 }));
    await assertSucceeds(updateDoc(doc(fs, 'foodListings', 'free1'), { totalQuantity: 12, availableQuantity: 8, updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(fs, 'foodListings', 'free1'), { distributionType: 'DISCOUNTED', originalPrice: 10, discountedPrice: 5 }));
  });

  test('another receiver cannot cancel someone else\'s reservation', async () => {
    await reserve(db('alice'), 'alice', 'RECEIVER', 'free1', 2, 'r18');
    const fs = db('bob');
    await assertFails(runTransaction(fs, async (tx) => {
      tx.update(doc(fs, 'reservations', 'r18'), { status: 'CANCELLED', cancelledAt: Timestamp.now() });
      tx.update(doc(fs, 'foodListings', 'free1'), { availableQuantity: 10, lastReservationId: 'r18', updatedAt: serverTimestamp() });
    }));
  });
});

// ---- QR pickup hand-over -------------------------------------------------------------------

describe('pickup QR code', () => {
  async function confirmed(resId) {
    await reserve(db('alice'), 'alice', 'RECEIVER', 'free1', 2, resId, { pickupCode: CODE });
    await updateDoc(doc(db('provider1'), 'reservations', resId), { status: 'CONFIRMED', confirmedAt: serverTimestamp() });
  }

  test('the receiver creates the code with the reservation and only they can read it', async () => {
    await confirmed('q1');
    await assertSucceeds(getDoc(doc(db('alice'), 'reservations', 'q1', 'private', 'pickup')));
    await assertFails(getDoc(doc(db('provider1'), 'reservations', 'q1', 'private', 'pickup')));
    await assertFails(getDoc(doc(db('bob'), 'reservations', 'q1', 'private', 'pickup')));
  });

  test('the code cannot be added or replaced after the reservation exists', async () => {
    await confirmed('q2');
    await assertFails(setDoc(doc(db('alice'), 'reservations', 'q2', 'private', 'pickup'), { code: 'f'.repeat(32) }));
    await reserve(db('alice'), 'alice', 'RECEIVER', 'free1', 1, 'q3');
    await assertFails(setDoc(doc(db('alice'), 'reservations', 'q3', 'private', 'pickup'), { code: CODE }));
  });

  test('the provider can mark a verified hand-over only with the matching code', async () => {
    await confirmed('q4');
    const verified = (code) => ({
      status: 'COLLECTED', collectedAt: serverTimestamp(), collectionConfirmedBy: 'PROVIDER',
      pickupVerified: true, pickupCode: code,
    });
    await assertFails(updateDoc(doc(db('provider1'), 'reservations', 'q4'), verified('f'.repeat(32))));
    await assertSucceeds(updateDoc(doc(db('provider1'), 'reservations', 'q4'), verified(CODE)));
  });

  test('a reservation cannot be created as already verified', async () => {
    await assertFails(reserve(db('alice'), 'alice', 'RECEIVER', 'free1', 1, 'q5',
      { reservation: { pickupVerified: true, pickupCode: CODE } }));
  });

  test('the plain (unverified) provider collection still works', async () => {
    await confirmed('q6');
    await assertSucceeds(updateDoc(doc(db('provider1'), 'reservations', 'q6'),
      { status: 'COLLECTED', collectedAt: serverTimestamp(), collectionConfirmedBy: 'PROVIDER' }));
  });
});

// ---- notifications -------------------------------------------------------------------------

describe('notifications', () => {
  test('notifications can only be sent between the parties of a reservation', async () => {
    await reserve(db('alice'), 'alice', 'RECEIVER', 'free1', 1, 'r19');
    await assertFails(setDoc(doc(db('bob'), 'notifications', 'spam'),
      notificationDoc('spam', 'provider1', 'bob', 'RESERVATION_REQUEST', 'free1', 'r19')));
  });

  test('recipients read and mark their own notifications; others cannot', async () => {
    await reserve(db('alice'), 'alice', 'RECEIVER', 'free1', 1, 'r20');
    await assertSucceeds(getDoc(doc(db('provider1'), 'notifications', 'n-r20')));
    await assertFails(getDoc(doc(db('bob'), 'notifications', 'n-r20')));
    await assertSucceeds(updateDoc(doc(db('provider1'), 'notifications', 'n-r20'), { isRead: true }));
    await assertFails(updateDoc(doc(db('provider1'), 'notifications', 'n-r20'), { message: 'edited' }));
  });
});
