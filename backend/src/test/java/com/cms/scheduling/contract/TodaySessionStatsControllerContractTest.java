package com.cms.scheduling.contract;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.account.config.SecurityConfig;
import com.cms.identity.account.config.StaffAuthenticationEntryPoint;
import com.cms.identity.account.config.StaffJwtService;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.identity.doctor.DoctorProfileRepository;
import com.cms.scheduling.api.TodaySessionStatsController;
import com.cms.scheduling.domain.SlotStatus;
import com.cms.scheduling.exception.ScheduleExceptionHandler;
import com.cms.scheduling.repository.SlotRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 051-staff-dashboard-enhancement T006 (contracts/today-session-stats.md): web-layer only
 * (mocked repositories), real JWT auth via a real token - mirrors
 * StaffOnboardingContractTest's own established shape for a staff-JWT-gated endpoint.
 *
 * <p>doctor-console-cross-doctor-leak fix: also covers the doctor self-scoping now applied here.
 */
@WebMvcTest(controllers = TodaySessionStatsController.class)
@Import({ScheduleExceptionHandler.class, SecurityConfig.class, StaffAuthenticationEntryPoint.class, StaffJwtService.class})
@TestPropertySource(properties = "staff.jwt.secret=test-only-contract-test-secret-at-least-64-characters-long-ok")
class TodaySessionStatsControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StaffJwtService staffJwtService;

    @MockBean
    private SlotRepository slotRepository;

    @MockBean
    private RoleAssignmentRepository roleAssignmentRepository;

    @MockBean
    private DoctorProfileRepository doctorProfileRepository;

    @Test
    void returnsCompletedAndNoShowCountsForAnActiveStaffMember() throws Exception {
        UUID clinicId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String token = staffJwtService.issueToken(accountId);

        RoleAssignment clinicAdminRole = Mockito.mock(RoleAssignment.class);
        when(clinicAdminRole.getRole()).thenReturn(RoleAssignment.Role.ClinicAdmin);
        when(roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(eq(accountId), eq(clinicId)))
                .thenReturn(List.of(clinicAdminRole));
        when(slotRepository.countStatusByClinicAndDate(eq(clinicId), any(), isNull()))
                .thenReturn(List.of(statusCount(SlotStatus.COMPLETED, 3), statusCount(SlotStatus.NO_SHOW, 1)));

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/sessions/today-stats", clinicId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedCount").value(3))
                .andExpect(jsonPath("$.noShowCount").value(1));
    }

    @Test
    void aDoctorOnlyCallerSeesOnlyTheirOwnCounts() throws Exception {
        UUID clinicId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID doctorProfileId = UUID.randomUUID();
        String token = staffJwtService.issueToken(accountId);

        RoleAssignment doctorRole = Mockito.mock(RoleAssignment.class);
        when(doctorRole.getRole()).thenReturn(RoleAssignment.Role.Doctor);
        when(roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(eq(accountId), eq(clinicId)))
                .thenReturn(List.of(doctorRole));
        DoctorProfile doctorProfile = Mockito.mock(DoctorProfile.class);
        when(doctorProfile.getId()).thenReturn(doctorProfileId);
        when(doctorProfileRepository.findByAccount_Id(accountId)).thenReturn(Optional.of(doctorProfile));
        when(slotRepository.countStatusByClinicAndDate(eq(clinicId), any(), eq(doctorProfileId)))
                .thenReturn(List.of(statusCount(SlotStatus.COMPLETED, 1)));

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/sessions/today-stats", clinicId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedCount").value(1))
                .andExpect(jsonPath("$.noShowCount").value(0));
    }

    @Test
    void rejectsACallerWithNoActiveRoleAtTheClinic() throws Exception {
        UUID clinicId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String token = staffJwtService.issueToken(accountId);

        when(roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(eq(accountId), eq(clinicId)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/sessions/today-stats", clinicId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void rejectsAMissingBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/clinics/{clinicId}/sessions/today-stats", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    private static SlotRepository.SlotStatusCount statusCount(SlotStatus status, long count) {
        return new SlotRepository.SlotStatusCount() {
            @Override
            public SlotStatus getStatus() {
                return status;
            }

            @Override
            public long getCount() {
                return count;
            }
        };
    }
}
