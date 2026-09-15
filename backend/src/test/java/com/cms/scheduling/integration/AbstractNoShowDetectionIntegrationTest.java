package com.cms.scheduling.integration;

import com.cms.booking.AppointmentType;
import com.cms.booking.AppointmentTypeRepository;
import com.cms.booking.Booking;
import com.cms.booking.BookingRepository;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.patient.record.Patient;
import com.cms.patient.record.PatientRepository;
import com.cms.scheduling.NoShowDetectionService;
import com.cms.scheduling.Schedule;
import com.cms.scheduling.ScheduleMode;
import com.cms.scheduling.Session;
import com.cms.scheduling.Slot;
import com.cms.scheduling.SlotStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 023: extends 018's Slot-generation fixture directly, adding a helper that constructs a
 * Session + Slot at an explicit LocalTime relative to {@code LocalTime.now()} - not via
 * {@code sessionGenerationService}'s fixed daily window, which can't reliably be "in the
 * past" relative to whenever the test suite happens to actually run.
 */
public abstract class AbstractNoShowDetectionIntegrationTest extends AbstractSlotGenerationIntegrationTest {

    @Autowired
    protected NoShowDetectionService noShowDetectionService;

    @Autowired
    protected AppointmentTypeRepository appointmentTypeRepository;

    @Autowired
    protected PatientRepository patientRepository;

    @Autowired
    protected BookingRepository bookingRepository;

    @AfterEach
    void cleanBookingRelatedRows() {
        bookingRepository.deleteAll();
        patientRepository.deleteAll();
        appointmentTypeRepository.deleteAll();
    }

    /** A Fixed-Time Session (today) + Slot at the given scheduled LocalTime, in the given status. */
    protected Slot saveFixedTimeSlotAt(Clinic clinic, DoctorProfile doctor, LocalTime scheduledTime, SlotStatus status) {
        Schedule schedule = saveFixedTimeSchedule(clinic, doctor);
        Session session = new Session(
                schedule, clinic, doctor, LocalDate.now(), ScheduleMode.FIXED_TIME, scheduledTime, scheduledTime.plusMinutes(15), 15);
        session = sessionRepository.save(session);
        Slot slot = new Slot(session, scheduledTime, scheduledTime.plusMinutes(15), false);
        slot.setStatus(status);
        return slotRepository.save(slot);
    }

    /** A Queue/Token Session (today) + Slot, in the given status - for asserting the sweep excludes Queue mode entirely. */
    protected Slot saveQueueSlot(Clinic clinic, DoctorProfile doctor, SlotStatus status) {
        Schedule schedule = saveQueueSchedule(clinic, doctor);
        Session session = new Session(
                schedule, clinic, doctor, LocalDate.now(), ScheduleMode.QUEUE, LocalTime.of(14, 0), LocalTime.of(17, 0), null);
        session = sessionRepository.save(session);
        Slot slot = new Slot(session, 1);
        slot.setStatus(status);
        return slotRepository.save(slot);
    }

    /** A Booking against the given Slot, under a freshly-created Patient, for asserting the sweep leaves it untouched. */
    protected Booking saveBookingFor(Clinic clinic, DoctorProfile doctor, Slot slot) {
        AppointmentType appointmentType = appointmentTypeRepository.save(new AppointmentType(doctor, "Consultation", new BigDecimal("300.00")));
        Patient patient = patientRepository.save(new Patient(clinic, null, "Test Patient " + UUID.randomUUID(), null));
        return bookingRepository.save(new Booking(slot, patient, appointmentType, new BigDecimal("300.00"), UUID.randomUUID()));
    }
}
