package com.cms.identity.clinic;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "clinic")
public class Clinic {

    /** Super Admin console redesign: why a Pending clinic registration was rejected as not genuine. */
    public enum RejectionReason {
        DUPLICATE_REGISTRATION,
        SUSPECTED_FRAUD,
        INVALID_DETAILS,
        UNREACHABLE_CONTACT,
        OTHER
    }

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String address;

    /** patient-search-advanced-filtering: optional, structured (not parsed out of {@code address}) - the hard city-scoping filter on public discovery search reads this column. Null for clinics registered before this field existed. */
    @Column
    private String city;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_mobile")
    private String contactMobile;

    @Column(nullable = false)
    private boolean verified = false;

    @Column(nullable = false)
    private boolean rejected = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_reason")
    private RejectionReason rejectionReason;

    @Column(name = "rejection_detail")
    private String rejectionDetail;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "rejected_by")
    private String rejectedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Clinic() {
        // JPA
    }

    /** Pre-existing call sites (every feature registered before city existed) - city defaults to null. */
    public Clinic(String name, String address, String contactEmail, String contactMobile) {
        this(name, address, null, contactEmail, contactMobile);
    }

    public Clinic(String name, String address, String city, String contactEmail, String contactMobile) {
        this.name = name;
        this.address = address;
        this.city = city;
        this.contactEmail = contactEmail;
        this.contactMobile = contactMobile;
        this.verified = false;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getCity() {
        return city;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getContactMobile() {
        return contactMobile;
    }

    public boolean isVerified() {
        return verified;
    }

    /** Mutator added by 003-super-admin-verification - the only feature that ever flips this flag post-creation. */
    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public boolean isRejected() {
        return rejected;
    }

    /** The caller MUST have already checked this clinic isn't verified - rejection only ever applies to a Pending registration. */
    public void reject(RejectionReason reason, String detail, String rejectedBy) {
        this.rejected = true;
        this.rejectionReason = reason;
        this.rejectionDetail = detail;
        this.rejectedAt = Instant.now();
        this.rejectedBy = rejectedBy;
    }

    /** Reverses {@link #reject} - moves the clinic back to Pending, clearing the rejection record. */
    public void restore() {
        this.rejected = false;
        this.rejectionReason = null;
        this.rejectionDetail = null;
        this.rejectedAt = null;
        this.rejectedBy = null;
    }

    public RejectionReason getRejectionReason() {
        return rejectionReason;
    }

    public String getRejectionDetail() {
        return rejectionDetail;
    }

    public Instant getRejectedAt() {
        return rejectedAt;
    }

    public String getRejectedBy() {
        return rejectedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
