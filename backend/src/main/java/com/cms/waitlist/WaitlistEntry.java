package com.cms.waitlist;

import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.patient.account.PatientAccount;
import com.cms.scheduling.Slot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * 031: a patient's place in line for either a specific doctor or a specialization with no
 * doctor preference - exactly one of {@link #doctorProfile}/{@link #specialization} is ever
 * set (data-model.md), enforced by the constructor below. No uniqueness constraint - a
 * duplicate join is explicitly allowed (research.md R6). {@link #offeredAt}/
 * {@link #offerExpiresAt} are owned here rather than on {@code NotificationEvent}, so 029's
 * future claim-window enforcement never needs to reach into the notification module
 * (research.md R5).
 */
@Entity
@Table(name = "waitlist_entry")
public class WaitlistEntry {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "clinic_id", nullable = false)
    private Clinic clinic;

    @ManyToOne(optional = false)
    @JoinColumn(name = "patient_account_id", nullable = false)
    private PatientAccount patientAccount;

    @ManyToOne
    @JoinColumn(name = "doctor_profile_id")
    private DoctorProfile doctorProfile;

    @Column private String specialization;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WaitlistEntryStatus status;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "offered_at")
    private Instant offeredAt;

    @Column(name = "offer_expires_at")
    private Instant offerExpiresAt;

    /** 032: which specific Slot an OFFERED entry refers to - 031 itself never needed to remember this. */
    @ManyToOne
    @JoinColumn(name = "offered_slot_id")
    private Slot offeredSlot;

    protected WaitlistEntry() {
        // JPA
    }

    /** FR-001/FR-002/FR-003: exactly one of {@code doctorProfile}/{@code specialization} must be non-null. */
    public WaitlistEntry(
            Clinic clinic, PatientAccount patientAccount, DoctorProfile doctorProfile, String specialization) {
        boolean hasDoctor = doctorProfile != null;
        boolean hasSpecialization = specialization != null && !specialization.isBlank();
        if (hasDoctor == hasSpecialization) {
            throw new WaitlistTargetRequiredException();
        }
        this.clinic = clinic;
        this.patientAccount = patientAccount;
        this.doctorProfile = doctorProfile;
        this.specialization = hasSpecialization ? specialization : null;
        this.status = WaitlistEntryStatus.WAITING;
        this.joinedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Clinic getClinic() {
        return clinic;
    }

    public PatientAccount getPatientAccount() {
        return patientAccount;
    }

    public DoctorProfile getDoctorProfile() {
        return doctorProfile;
    }

    public String getSpecialization() {
        return specialization;
    }

    public WaitlistEntryStatus getStatus() {
        return status;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public Instant getOfferedAt() {
        return offeredAt;
    }

    public Instant getOfferExpiresAt() {
        return offerExpiresAt;
    }

    public Slot getOfferedSlot() {
        return offeredSlot;
    }

    /** 031 FR-008: transitions WAITING -> OFFERED and stamps the 30-minute claim window. 032: also records which Slot this offer refers to. */
    public void offer(Instant now, Slot slot) {
        this.status = WaitlistEntryStatus.OFFERED;
        this.offeredAt = now;
        this.offerExpiresAt = now.plusSeconds(30 * 60);
        this.offeredSlot = slot;
    }

    /**
     * 032: syncs in-memory state after {@code WaitlistEntryRepository.claimIfOffered}'s
     * data-layer-guarded update, which bypasses the persistence context (mirrors
     * {@code Booking.setStatus}'s own documented purpose).
     */
    public void markClaimed() {
        this.status = WaitlistEntryStatus.CLAIMED;
    }

    /**
     * 032: syncs in-memory state after a confirmed OFFERED -> EXPIRED transition - either
     * {@code WaitlistEntryRepository.expireIfOffered}'s guarded update (decline/sweep), or a
     * direct save when this entry is already exclusively owned (the claim-failure pivot,
     * research.md R4, where no further race guard is needed since only this caller could have
     * just transitioned it to CLAIMED).
     */
    public void expire() {
        this.status = WaitlistEntryStatus.EXPIRED;
    }
}
