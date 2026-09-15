package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 020 FR-006, spec US1 AC3: an already-BOOKED Slot is rejected, race-safely under concurrency. */
class StaffBookingAlreadyBookedTest extends AbstractStaffBookingIntegrationTest {

    private ResultActions book(String clinicId, String slotId, String token, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/clinics/{clinicId}/slots/{slotId}/book", clinicId, slotId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void bookingAnAlreadyBookedSlotIsRejected() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        var slot = anOpenSlotOf(session);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        var patientA = saveExistingPatient(clinic);
        var patientB = saveExistingPatient(clinic);
        String token = clinicAdminToken(clinic);

        book(clinic.getId().toString(), slot.getId().toString(), token,
                        """
                        { "patientId": "%s", "appointmentTypeId": "%s" }
                        """.formatted(patientA.getId(), appointmentType.getId()))
                .andExpect(status().isCreated());

        book(clinic.getId().toString(), slot.getId().toString(), token,
                        """
                        { "patientId": "%s", "appointmentTypeId": "%s" }
                        """.formatted(patientB.getId(), appointmentType.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SLOT_ALREADY_BOOKED"));

        assertThat(bookingRepository.findBySlot_Id(slot.getId())).isPresent();
    }

    @Test
    void concurrentBookingAttemptsAgainstOneSlotOnlyOneSucceeds() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        var slot = anOpenSlotOf(session);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        var callerAccountId = clinicAdminAccountId(clinic);

        ExecutorService executor = Executors.newFixedThreadPool(10);
        try {
            List<Callable<Boolean>> attempts = java.util.stream.IntStream.range(0, 10)
                    .<Callable<Boolean>>mapToObj(i -> () -> {
                        var patient = saveExistingPatient(clinic);
                        try {
                            staffBookingService.bookSlot(
                                    callerAccountId,
                                    clinic.getId(),
                                    slot.getId(),
                                    new com.cms.booking.StaffBookingService.BookSlotInput(
                                            patient.getId(), null, null, appointmentType.getId()));
                            return true;
                        } catch (com.cms.booking.SlotAlreadyBookedException e) {
                            return false;
                        }
                    })
                    .toList();

            List<Future<Boolean>> futures = executor.invokeAll(attempts);
            long successCount = 0;
            for (Future<Boolean> f : futures) {
                if (f.get()) {
                    successCount++;
                }
            }

            assertThat(successCount).isEqualTo(1);
        } finally {
            executor.shutdown();
        }
    }

    private java.util.UUID clinicAdminAccountId(com.cms.identity.clinic.Clinic clinic) {
        String unique = java.util.UUID.randomUUID().toString();
        var admin = accountRepository.save(new com.cms.identity.account.Account(
                "Admin X", "adminx-" + unique + "@example.com",
                passwordEncoder.encode("Str0ng!Pass"), "CAX-" + unique, null));
        roleAssignmentRepository.save(new com.cms.identity.account.RoleAssignment(
                admin, clinic, com.cms.identity.account.RoleAssignment.Role.ClinicAdmin));
        return admin.getId();
    }
}
