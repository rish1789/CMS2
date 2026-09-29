package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.scheduling.domain.Schedule;
import com.cms.scheduling.domain.ScheduleMode;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * 055-schedule-break-window: proves the merge-two-schedules-into-one path actually works end to
 * end - deleting the now-redundant Schedule succeeds even when one of its already-generated
 * Sessions has real Booking history, by detaching (not blocking on) that one Session, exactly
 * as {@code ScheduleDeletionService}'s own Javadoc describes.
 */
class ScheduleDeletionTest extends AbstractSessionCancellationIntegrationTest {

    private Schedule saveEverydayFixedTimeSchedule(Clinic clinic, DoctorProfile doctor) {
        return scheduleRepository.save(new Schedule(
                doctor, clinic, EnumSet.allOf(DayOfWeek.class), LocalTime.of(9, 0), LocalTime.of(13, 0), ScheduleMode.FIXED_TIME, 15));
    }

    @Test
    void deletesAScheduleAndEveryZeroActivitySessionItGenerated() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Schedule schedule = saveEverydayFixedTimeSchedule(clinic, doctor);
        sessionGenerationService.generate(LocalDate.now());
        List<Session> sessions = sessionRepository.findBySchedule_Id(schedule.getId());
        assertThat(sessions).isNotEmpty();
        String token = clinicAdminToken(clinic);

        mockMvc.perform(delete(
                        "/api/v1/clinics/{clinicId}/doctors/{doctorProfileId}/schedules/{scheduleId}",
                        clinic.getId(), doctor.getId(), schedule.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(scheduleRepository.findById(schedule.getId())).isEmpty();
        for (Session session : sessions) {
            assertThat(sessionRepository.findById(session.getId())).isEmpty();
            assertThat(slotRepository.findBySession_Id(session.getId())).isEmpty();
        }
    }

    @Test
    void detachesARealActivitySessionInsteadOfBlockingTheDeletion() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Schedule schedule = saveEverydayFixedTimeSchedule(clinic, doctor);
        sessionGenerationService.generate(LocalDate.now());
        List<Session> sessions = sessionRepository.findBySchedule_Id(schedule.getId());
        Session bookedSession = sessions.get(0);
        Slot slot = slotRepository.findBySession_Id(bookedSession.getId()).get(0);
        bookSlot(clinic, doctor, slot);
        String token = clinicAdminToken(clinic);

        mockMvc.perform(delete(
                        "/api/v1/clinics/{clinicId}/doctors/{doctorProfileId}/schedules/{scheduleId}",
                        clinic.getId(), doctor.getId(), schedule.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(scheduleRepository.findById(schedule.getId())).isEmpty();
        Session survived = sessionRepository.findById(bookedSession.getId()).orElseThrow();
        assertThat(survived.getSchedule()).isNull();
        assertThat(slotRepository.findBySession_Id(bookedSession.getId())).isNotEmpty();
        assertThat(bookingRepository.countBySlot_Session_Id(bookedSession.getId())).isEqualTo(1);
    }

    @Test
    void theDoctorThemselvesCanDeleteTheirOwnSchedule() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Schedule schedule = saveEverydayFixedTimeSchedule(clinic, doctor);
        String token = doctorToken(doctor);

        mockMvc.perform(delete(
                        "/api/v1/clinics/{clinicId}/doctors/{doctorProfileId}/schedules/{scheduleId}",
                        clinic.getId(), doctor.getId(), schedule.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(scheduleRepository.findById(schedule.getId())).isEmpty();
    }

    @Test
    void unrelatedStaffAreForbidden() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Schedule schedule = saveEverydayFixedTimeSchedule(clinic, doctor);
        String token = unrelatedStaffToken();

        mockMvc.perform(delete(
                        "/api/v1/clinics/{clinicId}/doctors/{doctorProfileId}/schedules/{scheduleId}",
                        clinic.getId(), doctor.getId(), schedule.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        assertThat(scheduleRepository.findById(schedule.getId())).isPresent();
    }

    @Test
    void anUnknownScheduleIsNotFound() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        String token = clinicAdminToken(clinic);

        mockMvc.perform(delete(
                        "/api/v1/clinics/{clinicId}/doctors/{doctorProfileId}/schedules/{scheduleId}",
                        clinic.getId(), doctor.getId(), java.util.UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("SCHEDULE_NOT_FOUND"));
    }
}
