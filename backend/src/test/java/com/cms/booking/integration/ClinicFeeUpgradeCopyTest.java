package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.booking.domain.AppointmentType;
import com.cms.booking.domain.Booking;
import com.cms.booking.domain.ClinicAppointmentTypePrice;
import com.cms.booking.domain.ClinicDoctorFee;
import com.cms.booking.domain.DoctorDefaultFee;
import com.cms.booking.repository.ClinicAppointmentTypePriceRepository;
import com.cms.booking.repository.ClinicDoctorFeeRepository;
import com.cms.booking.service.FeeResolutionService;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.patient.record.domain.Patient;
import com.cms.scheduling.domain.Slot;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 068-per-clinic-fees US3 (FR-008/FR-009, SC-003): V43 copies today's doctor-wide prices to every
 * clinic where the doctor is actively staffed, so nothing a patient pays changes on day one.
 * Follows QueueTokenMigrationTest: Flyway has already applied V43 to the empty schema at
 * startup, so the test seeds pre-068 rows and re-runs the migration's own SQL against them.
 */
class ClinicFeeUpgradeCopyTest extends AbstractSessionCancellationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ClinicDoctorFeeRepository clinicDoctorFeeRepository;

    @Autowired
    private ClinicAppointmentTypePriceRepository clinicAppointmentTypePriceRepository;

    @Autowired
    private FeeResolutionService feeResolutionService;

    @Test
    void todaysDoctorWidePricesAreCopiedToEveryActiveClinicAndResolveTheSame() throws Exception {
        Clinic clinicA = saveClinic();
        Clinic clinicB = saveClinic();
        Clinic resignedFrom = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinicA);
        linkDoctorToClinic(doctor, clinicB, true);
        linkDoctorToClinic(doctor, resignedFrom, false);
        doctorDefaultFeeRepository.save(new DoctorDefaultFee(doctor, new BigDecimal("500.00")));
        AppointmentType procedure = appointmentTypeRepository.save(new AppointmentType(doctor, "Procedure", new BigDecimal("800.00")));
        AppointmentType consultation = appointmentTypeRepository.save(new AppointmentType(doctor, "Consultation", null));

        DoctorProfile noDefaultDoctor = saveDoctorStaffedAt(clinicA);
        AppointmentType unpriced = appointmentTypeRepository.save(new AppointmentType(noDefaultDoctor, "Consultation", null));

        Slot slot = slotRepository.findBySession_Id(saveFixedTimeSessionWithSlots(clinicA, doctor).getId()).get(0);
        Patient patient = patientRepository.save(new Patient(clinicA, null, "Pre-068 Patient " + UUID.randomUUID(), null));
        Booking booking = bookingRepository.saveAndFlush(
                new Booking(slot, patient, procedure, new BigDecimal("800.00"), doctor.getAccount().getId()));

        jdbcTemplate.execute(new ClassPathResource("db/migration/V43__copy_doctor_fees_to_active_clinics.sql")
                .getContentAsString(StandardCharsets.UTF_8));

        for (Clinic active : new Clinic[] {clinicA, clinicB}) {
            assertThat(clinicDoctorFeeRepository.findByClinic_IdAndDoctorProfile_Id(active.getId(), doctor.getId()))
                    .get()
                    .satisfies(fee -> {
                        assertThat(fee.getAmount()).isEqualByComparingTo("500.00");
                        assertThat(fee.getUpdatedByAccountId()).isNull();
                    });
            assertThat(clinicAppointmentTypePriceRepository.findByClinic_IdAndAppointmentType_Id(active.getId(), procedure.getId()))
                    .get()
                    .extracting(ClinicAppointmentTypePrice::getAmount)
                    .satisfies(amount -> assertThat(amount).isEqualByComparingTo("800.00"));
            assertThat(clinicAppointmentTypePriceRepository.findByClinic_IdAndAppointmentType_Id(active.getId(), consultation.getId()))
                    .isEmpty();

            // SC-003: what resolves after the upgrade is what resolved before it.
            assertThat(feeResolutionService.resolve(active.getId(), doctor.getId(), procedure.getId()))
                    .isEqualByComparingTo("800.00");
            assertThat(feeResolutionService.resolve(active.getId(), doctor.getId(), consultation.getId()))
                    .isEqualByComparingTo("500.00");
        }

        assertThat(clinicDoctorFeeRepository.findByClinic_IdAndDoctorProfile_Id(resignedFrom.getId(), doctor.getId())).isEmpty();
        assertThat(clinicAppointmentTypePriceRepository.findByClinic_IdAndAppointmentType_Id(resignedFrom.getId(), procedure.getId()))
                .isEmpty();
        assertThat(clinicDoctorFeeRepository.findByClinic_IdAndDoctorProfile_Id(clinicA.getId(), noDefaultDoctor.getId())).isEmpty();
        assertThat(clinicAppointmentTypePriceRepository.findByClinic_IdAndAppointmentType_Id(clinicA.getId(), unpriced.getId()))
                .isEmpty();

        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getLockedFee()).isEqualByComparingTo("800.00");
        assertThat(clinicDoctorFeeRepository.findAll()).extracting(ClinicDoctorFee::getAmount).hasSize(2);
    }
}
