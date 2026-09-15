package com.cms.scheduling;

import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * 015: one concrete, bookable occurrence materialized from a {@link Schedule} on a
 * specific calendar date. {@code mode}/{@code startTime}/{@code endTime}/
 * {@code slotIntervalMinutes}/{@code clinic}/{@code doctorProfile} are snapshots taken at
 * generation time - never re-derived from {@link #schedule} at read time - so a later
 * Schedule edit (014, not yet built) can never retroactively change an already-generated
 * Session (spec FR-003).
 */
@Entity
@Table(name = "session")
public class Session {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    @ManyToOne(optional = false)
    @JoinColumn(name = "clinic_id", nullable = false)
    private Clinic clinic;

    @ManyToOne(optional = false)
    @JoinColumn(name = "doctor_profile_id", nullable = false)
    private DoctorProfile doctorProfile;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScheduleMode mode;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "slot_interval_minutes")
    private Integer slotIntervalMinutes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** 026-session-delay-tracking: null = no outstanding delay. Written only by SessionDelayService.recalculate. */
    @Column(name = "delay_minutes")
    private Integer delayMinutes;

    protected Session() {
        // JPA
    }

    public Session(
            Schedule schedule,
            Clinic clinic,
            DoctorProfile doctorProfile,
            LocalDate sessionDate,
            ScheduleMode mode,
            LocalTime startTime,
            LocalTime endTime,
            Integer slotIntervalMinutes) {
        this.schedule = schedule;
        this.clinic = clinic;
        this.doctorProfile = doctorProfile;
        this.sessionDate = sessionDate;
        this.mode = mode;
        this.startTime = startTime;
        this.endTime = endTime;
        this.slotIntervalMinutes = slotIntervalMinutes;
    }

    public UUID getId() {
        return id;
    }

    public Schedule getSchedule() {
        return schedule;
    }

    public Clinic getClinic() {
        return clinic;
    }

    public DoctorProfile getDoctorProfile() {
        return doctorProfile;
    }

    public LocalDate getSessionDate() {
        return sessionDate;
    }

    public ScheduleMode getMode() {
        return mode;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public Integer getSlotIntervalMinutes() {
        return slotIntervalMinutes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Integer getDelayMinutes() {
        return delayMinutes;
    }

    public void setDelayMinutes(Integer delayMinutes) {
        this.delayMinutes = delayMinutes;
    }
}
