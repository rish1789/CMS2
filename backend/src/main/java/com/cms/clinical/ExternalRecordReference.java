package com.cms.clinical;

import com.cms.booking.Booking;
import com.cms.identity.doctor.DoctorProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * 036: a permanent, immutable typed-summary reference to an external clinical record, belonging
 * to exactly one {@link Booking} - like {@link com.cms.clinical.Prescription}, a Booking may
 * have zero or more (no uniqueness constraint). No file/document field of any kind exists here,
 * anywhere (research.md R5) - the system has no upload capability at all. No setter exists for
 * any field beyond what JPA's own lifecycle needs, and no update/delete code path exists
 * anywhere in this module - immutability is structural.
 */
@Entity
@Table(name = "external_record_reference")
public class ExternalRecordReference {

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

    @Column(name = "record_type", nullable = false)
    private String recordType;

    @Column(name = "source_provider", nullable = false)
    private String sourceProvider;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    @Column(nullable = false)
    private String summary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ExternalRecordReference() {
        // JPA
    }

    public ExternalRecordReference(
            Booking booking,
            DoctorProfile doctorProfile,
            String recordType,
            String sourceProvider,
            LocalDate recordDate,
            String summary) {
        this.booking = booking;
        this.doctorProfile = doctorProfile;
        this.recordType = recordType;
        this.sourceProvider = sourceProvider;
        this.recordDate = recordDate;
        this.summary = summary;
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

    public String getRecordType() {
        return recordType;
    }

    public String getSourceProvider() {
        return sourceProvider;
    }

    public LocalDate getRecordDate() {
        return recordDate;
    }

    public String getSummary() {
        return summary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
