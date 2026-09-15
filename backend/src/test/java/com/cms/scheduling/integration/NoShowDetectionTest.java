package com.cms.scheduling.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.scheduling.SlotStatus;
import java.math.BigDecimal;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/** 023 FR-001..FR-004/FR-006, spec US1/US2: the automatic no-show detection sweep. */
class NoShowDetectionTest extends AbstractNoShowDetectionIntegrationTest {

    @Test
    void aBookedSlotPastItsGracePeriodIsMarkedNoShow() {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var slot = saveFixedTimeSlotAt(clinic, doctor, LocalTime.now().minusMinutes(20), SlotStatus.BOOKED);

        int marked = noShowDetectionService.detectAndMarkNoShows();

        assertThat(marked).isEqualTo(1);
        assertThat(slotRepository.findById(slot.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.NO_SHOW);
    }

    @Test
    void aBookedSlotWithinTheGracePeriodRemainsBooked() {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var slot = saveFixedTimeSlotAt(clinic, doctor, LocalTime.now().minusMinutes(5), SlotStatus.BOOKED);

        int marked = noShowDetectionService.detectAndMarkNoShows();

        assertThat(marked).isEqualTo(0);
        assertThat(slotRepository.findById(slot.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.BOOKED);
    }

    @Test
    void aHeldSlotPastItsGracePeriodIsNeverMarkedNoShow() {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var slot = saveFixedTimeSlotAt(clinic, doctor, LocalTime.now().minusMinutes(30), SlotStatus.BOOKED);
        slot.setOnHold(true);
        slotRepository.save(slot);

        int marked = noShowDetectionService.detectAndMarkNoShows();

        assertThat(marked).isEqualTo(0);
        assertThat(slotRepository.findById(slot.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.BOOKED);
    }

    @Test
    void repeatSweepsNeverReprocessAnAlreadyNoShowOrAnOpenSlot() {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var noShowSlot = saveFixedTimeSlotAt(clinic, doctor, LocalTime.now().minusMinutes(20), SlotStatus.BOOKED);
        var openSlot = saveFixedTimeSlotAt(clinic, doctor, LocalTime.now().minusMinutes(20), SlotStatus.OPEN);

        assertThat(noShowDetectionService.detectAndMarkNoShows()).isEqualTo(1);
        assertThat(noShowDetectionService.detectAndMarkNoShows()).isEqualTo(0); // nothing left to mark

        assertThat(slotRepository.findById(noShowSlot.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.NO_SHOW);
        assertThat(slotRepository.findById(openSlot.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.OPEN);
    }

    @Test
    void aBookedQueueSlotIsUntouchedRegardlessOfElapsedTime() {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var slot = saveQueueSlot(clinic, doctor, SlotStatus.BOOKED);

        int marked = noShowDetectionService.detectAndMarkNoShows();

        assertThat(marked).isEqualTo(0);
        assertThat(slotRepository.findById(slot.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.BOOKED);
    }

    @Test
    void markingASlotNoShowLeavesItsBookingCompletelyUnchanged() {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var slot = saveFixedTimeSlotAt(clinic, doctor, LocalTime.now().minusMinutes(20), SlotStatus.BOOKED);
        var booking = saveBookingFor(clinic, doctor, slot);

        noShowDetectionService.detectAndMarkNoShows();

        var reloaded = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(reloaded.getLockedFee()).isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(reloaded.getPaymentStatus()).isEqualTo(booking.getPaymentStatus());
        assertThat(reloaded.getPatient().getId()).isEqualTo(booking.getPatient().getId());
    }
}
