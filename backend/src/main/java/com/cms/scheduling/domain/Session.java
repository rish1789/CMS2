package com.cms.scheduling.domain;



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

    /** 055-schedule-break-window: nullable - {@link #detachSchedule} orphans a real-activity Session instead of blocking its whole Schedule's deletion. */
    @ManyToOne(optional = true)
    @JoinColumn(name = "schedule_id")
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

    /**
     * 055-schedule-break-window: snapshot of the generating Schedule's break window (both
     * null, or both set), taken at generation time like every other Schedule-derived field
     * on this class - never re-derived from {@link #schedule} at read time.
     */
    @Column(name = "break_start_time")
    private LocalTime breakStartTime;

    @Column(name = "break_end_time")
    private LocalTime breakEndTime;

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

    /** 055-schedule-break-window: called only by ScheduleDeletionService, for a Session with real Booking/waitlist history whose Schedule is being deleted. */
    public void detachSchedule() {
        this.schedule = null;
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

    public LocalTime getBreakStartTime() {
        return breakStartTime;
    }

    /** 055-schedule-break-window: set once, by ScheduleSessionGenerator, before this Session is first persisted. */
    public void setBreakStartTime(LocalTime breakStartTime) {
        this.breakStartTime = breakStartTime;
    }

    public LocalTime getBreakEndTime() {
        return breakEndTime;
    }

    /** 055-schedule-break-window: set once, by ScheduleSessionGenerator, before this Session is first persisted. */
    public void setBreakEndTime(LocalTime breakEndTime) {
        this.breakEndTime = breakEndTime;
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
