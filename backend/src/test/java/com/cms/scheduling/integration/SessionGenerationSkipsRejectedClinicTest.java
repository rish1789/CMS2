package com.cms.scheduling.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.identity.clinic.Clinic;
import com.cms.scheduling.domain.ScheduleMode;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * 062-rejected-clinic-gating (FR-004/FR-006, SC-002, tasks.md T020): a generation run creates no
 * sessions for a rejected clinic while another clinic's schedule in the very same run is
 * generated exactly as before.
 */
class SessionGenerationSkipsRejectedClinicTest extends AbstractSessionGenerationIntegrationTest {

    @Test
    void theRunSkipsTheRejectedClinicOnly() {
        Clinic rejected = saveClinic();
        rejected.reject(Clinic.RejectionReason.DUPLICATE_REGISTRATION, null, SUPER_ADMIN_USERNAME);
        rejected = clinicRepository.save(rejected);
        Clinic pending = saveClinic();

        var rejectedSchedule = saveEveryDaySchedule(rejected, saveDoctorStaffedAt(rejected), ScheduleMode.FIXED_TIME, 15);
        var pendingSchedule = saveEveryDaySchedule(pending, saveDoctorStaffedAt(pending), ScheduleMode.FIXED_TIME, 15);

        sessionGenerationService.generate(LocalDate.now());

        assertThat(sessionRepository.findBySchedule_Id(rejectedSchedule.getId())).isEmpty();
        assertThat(sessionRepository.findBySchedule_Id(pendingSchedule.getId())).isNotEmpty();
    }
}
