package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cms.booking.domain.Booking;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.scheduling.domain.Session;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 063-front-desk-walk-in (data-model.md, tasks.md T002; Constitution I migration invariant): the
 * V39 check constraint {@code ck_booking_visit_reason_other_detail} rejects an OTHER visit reason
 * without free-text detail at the database level, while every other shape is accepted.
 */
class VisitReasonConstraintTest extends AbstractSessionCancellationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Booking aBooking() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        return bookSlot(clinic, doctor, slotRepository.findBySession_Id(session.getId()).get(0));
    }

    private void setReason(Booking booking, String reason, String detail) {
        jdbcTemplate.update(
                "UPDATE booking SET visit_reason = ?, visit_reason_detail = ? WHERE id = ?", reason, detail, booking.getId());
    }

    @Test
    void otherWithoutDetailIsRejectedByTheDatabase() {
        Booking booking = aBooking();

        assertThatThrownBy(() -> setReason(booking, "OTHER", null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> setReason(booking, "OTHER", "   ")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void otherWithDetailAndListedReasonsWithoutDetailAreAccepted() {
        Booking booking = aBooking();

        assertThatCode(() -> setReason(booking, "OTHER", "Dizziness since morning")).doesNotThrowAnyException();
        assertThatCode(() -> setReason(booking, "PAIN", null)).doesNotThrowAnyException();
        assertThatCode(() -> setReason(booking, null, null)).doesNotThrowAnyException();
    }
}
