package com.cms.clinical;

import com.cms.booking.Booking;
import com.cms.identity.doctor.DoctorProfile;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * 035: a permanent, immutable clinical record belonging to exactly one {@link Booking} - unlike
 * {@link ConsultationNote}, a Booking may have zero or more (research.md R3, no uniqueness
 * constraint). No setter exists for any field beyond what JPA's own lifecycle needs, and no
 * update code path exists anywhere in this module - immutability from an editing standpoint is
 * structural. 038 research.md R3: the one foreseen exception is permanent deletion under the
 * DPDP retention-purge precondition (anonymized patient + 3-year-old booking), which is why
 * {@code items}' cascade includes {@code REMOVE} below - never called from this module itself.
 */
@Entity
@Table(name = "prescription")
public class Prescription {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(optional = false)
    @JoinColumn(name = "doctor_profile_id", nullable = false)
    private DoctorProfile doctorProfile;

    /**
     * EAGER (not the JPA default LAZY): {@code PrescriptionService.list()}'s {@code
     * @Transactional(readOnly = true)} boundary closes before {@code
     * StaffPrescriptionController} maps to {@code PrescriptionResponse} (which calls {@code
     * getItems()}, open-in-view: false) - a lazy collection here throws {@code
     * LazyInitializationException} on every {@code GET .../prescriptions} call, the same
     * bug class found and fixed in {@code Schedule.daysOfWeek}. {@code create()} is
     * unaffected either way (a freshly-constructed Prescription's {@code items} is always
     * a plain, already-populated list by response time). A Prescription's item count is
     * always small, so EAGER here is not a real fetch-cost concern.
     */
    @OneToMany(mappedBy = "prescription", cascade = {CascadeType.PERSIST, CascadeType.REMOVE}, fetch = FetchType.EAGER)
    private List<PrescriptionItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Prescription() {
        // JPA
    }

    public Prescription(Booking booking, DoctorProfile doctorProfile) {
        this.booking = booking;
        this.doctorProfile = doctorProfile;
        this.createdAt = Instant.now();
    }

    /** research.md R4: items are added after construction, then persisted together with the Prescription via cascade PERSIST. */
    public void addItem(String medicationName, String dosage, String frequency, String duration, String instructions) {
        items.add(new PrescriptionItem(this, medicationName, dosage, frequency, duration, instructions));
    }

    public UUID getId() {
        return id;
    }

    public Booking getBooking() {
        return booking;
    }

    public DoctorProfile getDoctorProfile() {
        return doctorProfile;
    }

    public List<PrescriptionItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
