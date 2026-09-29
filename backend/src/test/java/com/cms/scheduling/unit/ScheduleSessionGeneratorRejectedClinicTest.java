package com.cms.scheduling.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cms.identity.clinic.Clinic;
import com.cms.scheduling.domain.Schedule;
import com.cms.scheduling.repository.ScheduleRepository;
import com.cms.scheduling.repository.SessionRepository;
import com.cms.scheduling.service.ScheduleSessionGenerator;
import com.cms.scheduling.service.SlotGenerationService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 062-rejected-clinic-gating (FR-004, research.md Decision 2, tasks.md T019): the single
 * session-creation choke point skips a rejected clinic's schedules entirely - no session read, no
 * session or slot written.
 */
@ExtendWith(MockitoExtension.class)
class ScheduleSessionGeneratorRejectedClinicTest {

    @Mock ScheduleRepository scheduleRepository;
    @Mock SessionRepository sessionRepository;
    @Mock SlotGenerationService slotGenerationService;

    @Test
    void aRejectedClinicsScheduleGeneratesNothing() {
        Clinic clinic = new Clinic("Rejected Clinic", "1 Main St", null, null);
        clinic.reject(Clinic.RejectionReason.DUPLICATE_REGISTRATION, null, "super-admin");
        Schedule schedule = org.mockito.Mockito.mock(Schedule.class);
        UUID scheduleId = UUID.randomUUID();
        when(schedule.getClinic()).thenReturn(clinic);
        org.mockito.Mockito.lenient().when(schedule.getDaysOfWeek()).thenReturn(EnumSet.allOf(DayOfWeek.class));
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));

        int created = new ScheduleSessionGenerator(scheduleRepository, sessionRepository, slotGenerationService)
                .generateForSchedule(scheduleId, LocalDate.now());

        assertThat(created).isZero();
        verify(sessionRepository, never()).save(any());
        verifyNoInteractions(slotGenerationService);
    }
}
