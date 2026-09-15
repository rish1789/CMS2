package com.cms.clinical;

import com.cms.booking.Booking;
import com.cms.identity.doctor.DoctorProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * 034: a permanent, immutable clinical record for exactly one {@link Booking} - the "exactly
 * one" is a data-layer {@code UNIQUE} constraint on {@code booking_id} (research.md R2), not
 * merely an application-level check. No setter exists for any field beyond what JPA's own
 * lifecycle needs, and no update/delete code path exists anywhere in this module (research.md
 * R7) - immutability is structural, not just unexposed.
 */
@Entity
@Table(name = "consultation_note")
public class ConsultationNote {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @OneToOne(optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(optional = false)
    @JoinColumn(name = "doctor_profile_id", nullable = false)
    private DoctorProfile doctorProfile;

    @Column(nullable = false)
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ConsultationNote() {
        // JPA
    }

    public ConsultationNote(Booking booking, DoctorProfile doctorProfile, String content) {
        this.booking = booking;
        this.doctorProfile = doctorProfile;
        this.content = content;
        this.createdAt = Instant.now();
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

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
