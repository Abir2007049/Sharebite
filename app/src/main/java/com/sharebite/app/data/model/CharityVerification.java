package com.sharebite.app.data.model;

import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.List;

/**
 * Verification submission for a charity, stored at charityVerifications/{organizationId}
 * (one document per organisation). Reviewers set the outcome; see README for the process.
 */
public class CharityVerification implements FirestoreDocument {

    private String organizationId;
    private String organizationName;
    private String registrationNumber;
    private String contactPerson;
    private String contactPhone;
    private String organizationAddress;
    private String description;
    private List<String> submittedDocuments = new ArrayList<>();
    private VerificationStatus verificationStatus;
    private String reviewerNote;
    private Timestamp submittedAt;
    private Timestamp reviewedAt;

    public CharityVerification() {
        // Required by Firestore.
    }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getOrganizationName() { return organizationName; }
    public void setOrganizationName(String organizationName) { this.organizationName = organizationName; }

    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }

    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getOrganizationAddress() { return organizationAddress; }
    public void setOrganizationAddress(String organizationAddress) { this.organizationAddress = organizationAddress; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<String> getSubmittedDocuments() { return submittedDocuments; }
    public void setSubmittedDocuments(List<String> submittedDocuments) {
        this.submittedDocuments = submittedDocuments == null ? new ArrayList<>() : submittedDocuments;
    }

    public VerificationStatus getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(VerificationStatus verificationStatus) { this.verificationStatus = verificationStatus; }

    public String getReviewerNote() { return reviewerNote; }
    public void setReviewerNote(String reviewerNote) { this.reviewerNote = reviewerNote; }

    public Timestamp getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Timestamp submittedAt) { this.submittedAt = submittedAt; }

    public Timestamp getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Timestamp reviewedAt) { this.reviewedAt = reviewedAt; }

    @Override
    public String documentId() { return organizationId; }

    @Override
    public void assignDocumentId(String id) { this.organizationId = id; }
}
