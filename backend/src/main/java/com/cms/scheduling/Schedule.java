package com.cms.scheduling;

import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * 013: the recurring weekly pattern - only the input a future nightly Session-generation
 * job (011) will consume. Creating one never itself creates a Session/Slot (FR-011).
 */
@Entity
@Table(name = "schedule")
public class Schedule {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "doctor_profile_id", nullable = false)
    private DoctorProfile doctorProfile;

    @ManyToOne(optional = false)
    @JoinColumn(name = "clinic_id", nullable = false)
    private Clinic clinic;

    /**
     * EAGER (not the JPA default LAZY): {@code ScheduleService.list()}'s {@code
     * @Transactional(readOnly = true)} boundary closes before {@code ScheduleController}
     * maps to {@code ScheduleResponse} (open-in-view: false, no fallback session) - a lazy
     * collection here throws {@code LazyInitializationException} on every {@code GET
     * .../schedules} call. {@code create()}/{@code edit()} are unaffected either way (both
     * always hold a plain, already-populated {@code HashSet} by the time a response is
     * built - a freshly-constructed entity's field, or the setter's replacement value -
     * never Hibernate's lazy proxy) - EAGER here is for a tiny (<= 7-element) Set, not a
     * real fetch-cost concern.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "schedule_day", joinColumns = @JoinColumn(name = "schedule_id"))
    @Column(name = "day_of_week", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<DayOfWeek> daysOfWeek = new HashSet<>();

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScheduleMode mode;

    @Column(name = "slot_interval_minutes")
    private Integer slotIntervalMinutes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Schedule() {
        // JPA
    }

    public Schedule(
            DoctorProfile doctorProfile,
            Clinic clinic,
            Set<DayOfWeek> daysOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            ScheduleMode mode,
            Integer slotIntervalMinutes) {
        this.doctorProfile = doctorProfile;
        this.clinic = clinic;
        this.daysOfWeek = new HashSet<>(daysOfWeek);
        this.startTime = startTime;
        this.endTime = endTime;
        this.mode = mode;
        this.slotIntervalMinutes = slotIntervalMinutes;
    }

    public UUID getId() {
        return id;
    }

    public DoctorProfile getDoctorProfile() {
        return doctorProfile;
    }

    public Clinic getClinic() {
        return clinic;
    }

    public Set<DayOfWeek> getDaysOfWeek() {
        return daysOfWeek;
    }

    /** 016-schedule-edit-non-retroactivity: edited by ScheduleService.edit(); never touches any Session already generated. */
    public void setDaysOfWeek(Set<DayOfWeek> daysOfWeek) {
        this.daysOfWeek = new HashSet<>(daysOfWeek);
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    /** 016-schedule-edit-non-retroactivity: edited by ScheduleService.edit(). */
    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    /** 016-schedule-edit-non-retroactivity: edited by ScheduleService.edit(). */
    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public ScheduleMode getMode() {
        return mode;
    }

    /** 016-schedule-edit-non-retroactivity: edited by ScheduleService.edit(). */
    public void setMode(ScheduleMode mode) {
        this.mode = mode;
    }

    public Integer getSlotIntervalMinutes() {
        return slotIntervalMinutes;
    }

    /** 016-schedule-edit-non-retroactivity: edited by ScheduleService.edit(). */
    public void setSlotIntervalMinutes(Integer slotIntervalMinutes) {
        this.slotIntervalMinutes = slotIntervalMinutes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
