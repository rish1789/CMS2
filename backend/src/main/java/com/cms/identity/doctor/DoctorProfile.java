package com.cms.identity.doctor;

import com.cms.identity.account.Account;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * The Doctor's global clinical identity - one per Account, not per clinic. Created here
 * by 004-staff-onboarding-direct-hire (FR-007); the verification workflow that flips
 * {@code licenseVerified}, the {@code visible} toggle, and onboarding-time dedup/reuse
 * by {@code licenseNumber} are 007-doctor-profile-license-queue's responsibility
 * (data-model.md).
 */
@Entity
@Table(
        name = "doctor_profile",
        uniqueConstraints = @UniqueConstraint(name = "uq_doctor_profile_account", columnNames = "account_id"))
public class DoctorProfile {

    /** Super Admin console redesign: why a Pending license-verification queue entry was rejected as not genuine. */
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

    @OneToOne(optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false)
    private String specialization;

    @Column(name = "license_number", nullable = false)
    private String licenseNumber;

    @Column(name = "experience_years", nullable = false)
    private int experienceYears;

    @Column(name = "license_verified", nullable = false)
    private boolean licenseVerified = false;

    @Column(nullable = false)
    private boolean visible = true;

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

    protected DoctorProfile() {
        // JPA
    }

    public DoctorProfile(Account account, String specialization, String licenseNumber, int experienceYears) {
        this.account = account;
        this.specialization = specialization;
        this.licenseNumber = licenseNumber;
        this.experienceYears = experienceYears;
        this.licenseVerified = false;
    }

    public UUID getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public String getSpecialization() {
        return specialization;
    }

    /** 008-doctor-license-reverification-reset: edited by DoctorVerificationService.edit(). */
    public void setSpecialization(String specialization) {
        this.specialization = specialization;
    }

    public String getLicenseNumber() {
        return licenseNumber;
    }

    /** 008-doctor-license-reverification-reset: edited by DoctorVerificationService.edit(). */
    public void setLicenseNumber(String licenseNumber) {
        this.licenseNumber = licenseNumber;
    }

    public int getExperienceYears() {
        return experienceYears;
    }

    /** 008-doctor-license-reverification-reset: edited by DoctorVerificationService.edit(). */
    public void setExperienceYears(int experienceYears) {
        this.experienceYears = experienceYears;
    }

    public boolean isLicenseVerified() {
        return licenseVerified;
    }

    /** 007-doctor-profile-license-queue: flipped by DoctorVerificationService's verify action. */
    public void setLicenseVerified(boolean licenseVerified) {
        this.licenseVerified = licenseVerified;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isRejected() {
        return rejected;
    }

    /** The caller MUST have already checked this profile isn't license-verified - rejection only ever applies to a Pending queue entry. */
    public void reject(RejectionReason reason, String detail, String rejectedBy) {
        this.rejected = true;
        this.rejectionReason = reason;
        this.rejectionDetail = detail;
        this.rejectedAt = Instant.now();
        this.rejectedBy = rejectedBy;
    }

    /** Reverses {@link #reject} - moves the profile back to Pending, clearing the rejection record. */
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
