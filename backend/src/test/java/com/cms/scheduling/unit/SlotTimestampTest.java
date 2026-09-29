package com.cms.scheduling.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.scheduling.domain.ScheduleMode;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import com.cms.scheduling.repository.SlotRepository;
import com.cms.scheduling.service.SessionDelayService;
import com.cms.scheduling.service.SlotCompletionService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 063-front-desk-walk-in (FR-014, research.md Decision 2, tasks.md T003): sending a patient in and
 * completing their visit record when it happened - on every path that makes those transitions,
 * because the stamp lives on the transition itself - and an untimed walk-in slot (no scheduled
 * start) can be completed without the timed slot's "not yet started" check.
 */
class SlotTimestampTest {

    private static Session fixedTimeSession(LocalDate date) {
        Session session = mock(Session.class);
        lenient().when(session.getMode()).thenReturn(ScheduleMode.FIXED_TIME);
        lenient().when(session.getSessionDate()).thenReturn(date);
        lenient().when(session.getId()).thenReturn(UUID.randomUUID());
        return session;
    }

    @Test
    void enteringAppearedStampsTheSendInTime() {
        Slot slot = new Slot(fixedTimeSession(LocalDate.now()), LocalTime.of(9, 0), LocalTime.of(9, 15));
        slot.setStatus(SlotStatus.BOOKED);
        Instant before = Instant.now();

        slot.setStatus(SlotStatus.APPEARED);

        assertThat(slot.getAppearedAt()).isNotNull().isAfterOrEqualTo(before);
        assertThat(slot.getCompletedAt()).isNull();
    }

    @Test
    void appearedFromNoShowAlsoStampsTheSendInTime() {
        Slot slot = new Slot(fixedTimeSession(LocalDate.now()), LocalTime.of(9, 0), LocalTime.of(9, 15));
        slot.setStatus(SlotStatus.NO_SHOW);

        slot.setStatus(SlotStatus.APPEARED);

        assertThat(slot.getAppearedAt()).isNotNull();
    }

    @Test
    void enteringCompletedStampsTheFinishTime() {
        Slot slot = new Slot(fixedTimeSession(LocalDate.now()), LocalTime.of(9, 0), LocalTime.of(9, 15));
        slot.setStatus(SlotStatus.APPEARED);

        slot.setStatus(SlotStatus.COMPLETED);

        assertThat(slot.getCompletedAt()).isNotNull().isAfterOrEqualTo(slot.getAppearedAt());
    }

    @Test
    void otherTransitionsDoNotStampAnything() {
        Slot slot = new Slot(fixedTimeSession(LocalDate.now()), LocalTime.of(9, 0), LocalTime.of(9, 15));

        slot.setStatus(SlotStatus.BOOKED);
        slot.setStatus(SlotStatus.OPEN);

        assertThat(slot.getAppearedAt()).isNull();
        assertThat(slot.getCompletedAt()).isNull();
    }

    @Test
    void anUntimedWalkInSlotIsUntimedAndATimedOneIsNot() {
        assertThat(new Slot(fixedTimeSession(LocalDate.now()), 1).isUntimed()).isTrue();
        assertThat(new Slot(fixedTimeSession(LocalDate.now()), LocalTime.of(9, 0), LocalTime.of(9, 15)).isUntimed())
                .isFalse();
    }

    @Test
    void staffCanCompleteAnUntimedWalkInWithoutTheNotYetStartedCheck() {
        UUID clinicId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Clinic clinic = mock(Clinic.class);
        when(clinic.getId()).thenReturn(clinicId);
        // A session dated tomorrow: a timed slot here would be refused as "not yet started".
        Session session = fixedTimeSession(LocalDate.now().plusDays(1));
        when(session.getClinic()).thenReturn(clinic);
        DoctorProfile treatingDoctor = mock(DoctorProfile.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        lenient().when(treatingDoctor.getAccount().getId()).thenReturn(UUID.randomUUID()); // not the caller
        lenient().when(session.getDoctorProfile()).thenReturn(treatingDoctor);
        Slot walkIn = new Slot(session, 1);
        ReflectionTestUtils.setField(walkIn, "id", UUID.randomUUID());
        walkIn.setStatus(SlotStatus.APPEARED);
        SlotRepository slotRepository = mock(SlotRepository.class);
        when(slotRepository.findById(walkIn.getId())).thenReturn(Optional.of(walkIn));
        RoleAssignmentRepository roles = mock(RoleAssignmentRepository.class);
        lenient().when(roles.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(callerId, clinicId, RoleAssignment.Role.ClinicAdmin))
                .thenReturn(true);

        Slot completed = new SlotCompletionService(slotRepository, roles, mock(SessionDelayService.class))
                .completeSlot(callerId, clinicId, walkIn.getId());

        assertThat(completed.getStatus()).isEqualTo(SlotStatus.COMPLETED);
        assertThat(completed.getCompletedAt()).isNotNull();
    }
}
