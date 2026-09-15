package com.cms.booking.integration;

import com.cms.booking.AppointmentType;
import com.cms.booking.Booking;
import com.cms.booking.BookingRepository;
import com.cms.booking.RiskBasedBufferSlotCalculator;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.patient.record.Patient;
import com.cms.patient.record.PatientRepository;
import com.cms.scheduling.Schedule;
import com.cms.scheduling.ScheduleMode;
import com.cms.scheduling.ScheduleRepository;
import com.cms.scheduling.Session;
import com.cms.scheduling.SessionRepository;
import com.cms.scheduling.Slot;
import com.cms.scheduling.SlotRepository;
import com.cms.scheduling.SlotStatus;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;

/** 024: extends 017's booking fixture directly, adding Schedule/Session/Slot/Patient/Booking helpers for controllable trailing-window placement. */
public abstract class AbstractBufferSlotCalculatorIntegrationTest extends AbstractBookingIntegrationTest {

    @Autowired
    protected ScheduleRepository scheduleRepository;

    @Autowired
    protected SessionRepository sessionRepository;

    @Autowired
    protected SlotRepository slotRepository;

    @Autowired
    protected PatientRepository patientRepository;

    @Autowired
    protected BookingRepository bookingRepository;

    @Autowired
    protected RiskBasedBufferSlotCalculator riskBasedBufferSlotCalculator;

    @AfterEach
    void cleanBufferSlotRelatedRows() {
        bookingRepository.deleteAll();
        patientRepository.deleteAll();
        slotRepository.deleteAll();
        sessionRepository.deleteAll();
        scheduleRepository.deleteAll();
    }

    /** A Fixed-Time Session (its own fresh Schedule) at the given date/hours/interval - no Slots/Bookings yet. */
    protected Session saveFixedTimeSessionOn(
            Clinic clinic, DoctorProfile doctor, LocalDate sessionDate, LocalTime startTime, LocalTime endTime, int intervalMinutes) {
        Schedule schedule = scheduleRepository.save(new Schedule(
                doctor, clinic, EnumSet.allOf(DayOfWeek.class), startTime, endTime, ScheduleMode.FIXED_TIME, intervalMinutes));
        Session session = new Session(
                schedule, clinic, doctor, sessionDate, ScheduleMode.FIXED_TIME, startTime, endTime, intervalMinutes);
        return sessionRepository.save(session);
    }

    /** A Fixed-Time Session on the given date, 9-13, 15-min interval (16 slots if generated) - the common case. */
    protected Session saveFixedTimeSessionOn(Clinic clinic, DoctorProfile doctor, LocalDate sessionDate) {
        return saveFixedTimeSessionOn(clinic, doctor, sessionDate, LocalTime.of(9, 0), LocalTime.of(13, 0), 15);
    }

    /** One Booking (with a fresh Patient/AppointmentType) against a single new Slot within the given Session, at the given status. */
    protected Booking saveBookingOn(Clinic clinic, DoctorProfile doctor, Session session, SlotStatus status) {
        Slot slot = new Slot(session, session.getStartTime(), session.getStartTime().plusMinutes(session.getSlotIntervalMinutes()), false);
        slot.setStatus(status);
        slot = slotRepository.save(slot);

        AppointmentType appointmentType =
                appointmentTypeRepository.save(new AppointmentType(doctor, "Consultation", new BigDecimal("300.00")));
        Patient patient = patientRepository.save(new Patient(clinic, null, "Patient " + UUID.randomUUID(), null));
        return bookingRepository.save(new Booking(slot, patient, appointmentType, new BigDecimal("300.00"), UUID.randomUUID()));
    }

    /** N Bookings, each on its own fresh Session dated {@code sessionDate}, {@code noShowCount} of them NO_SHOW and the rest BOOKED. */
    protected void saveBookingHistory(Clinic clinic, DoctorProfile doctor, LocalDate sessionDate, int total, int noShowCount) {
        for (int i = 0; i < total; i++) {
            Session session = saveFixedTimeSessionOn(clinic, doctor, sessionDate);
            SlotStatus status = i < noShowCount ? SlotStatus.NO_SHOW : SlotStatus.BOOKED;
            saveBookingOn(clinic, doctor, session, status);
        }
    }
}
