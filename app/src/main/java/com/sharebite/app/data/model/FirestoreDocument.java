package com.sharebite.app.data.model;

/**
 * Implemented by every model persisted as a Firestore document, so generic repository code can
 * map a snapshot's document ID onto the model.
 *
 * <p>Method names deliberately avoid the {@code get}/{@code set} prefixes: Firestore's object
 * mapper serialises bean-style accessors, and these helpers must not become document fields.
 */
public interface FirestoreDocument {

    String documentId();

    void assignDocumentId(String id);
}
