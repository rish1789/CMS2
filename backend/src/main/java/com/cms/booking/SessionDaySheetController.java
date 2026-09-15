package com.cms.booking;

import com.cms.booking.dto.SessionDaySheetResponse;
import com.cms.booking.dto.SessionDaySheetResponse.BookingDetail;
import com.cms.booking.dto.SessionDaySheetResponse.SlotDetail;
import com.cms.identity.account.RoleAssignment;
import com.cms.identity.account.RoleAssignmentRepository;
import com.cms.identity.account.SecurityConfig;
import com.cms.identity.doctor.DoctorProfileRepository;
import com.cms.scheduling.Session;
import com.cms.scheduling.SessionNotFoundException;
import com.cms.scheduling.SessionRepository;
import com.cms.scheduling.SlotRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 041-staff-console-pickers FR-004/FR-005: the day sheet's per-session slot+booking detail.
 * Lives in {@code com.cms.booking} (research.md R1) since it composes {@code Slot} (scheduling)
 * with {@code Booking}/{@code Patient} (booking/patient.record) - a module that already
 * legitimately depends on all three, unlike {@code scheduling}, which must never depend on
 * {@code booking} (022's own established rule).
 *
 * <p>Doctor self-scoping follow-up: a caller whose *only* active role at this clinic is Doctor
 * gets a 404 (mirroring the existing clinic-mismatch 404 immediately below, not a 403 - "not
 * yours to view" is treated identically to "doesn't exist" for this caller, per this
 * controller's own established info-hiding shape) for any session belonging to a different
 * doctor. A caller who also holds ClinicAdmin or Operations at this clinic is unaffected.
 */
@RestController
@RequestMapping("/api/v1/clinics/{clinicId}/sessions/{sessionId}/day-sheet")
public class SessionDaySheetController {

    private final SessionRepository sessionRepository;
    private final SlotRepository slotRepository;
    private final BookingRepository bookingRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final DoctorProfileRepository doctorProfileRepository;

    public SessionDaySheetController(
            SessionRepository sessionRepository,
            SlotRepository slotRepository,
            BookingRepository bookingRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            DoctorProfileRepository doctorProfileRepository) {
        this.sessionRepository = sessionRepository;
        this.slotRepository = slotRepository;
        this.bookingRepository = bookingRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.doctorProfileRepository = doctorProfileRepository;
    }

    @GetMapping
    public SessionDaySheetResponse daySheet(
            @PathVariable UUID clinicId, @PathVariable UUID sessionId, Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        List<RoleAssignment> roles =
                roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId);
        if (roles.isEmpty()) {
            throw new NotStaffedAtClinicException();
        }
        boolean doctorOnly = roles.stream().noneMatch(ra -> ra.getRole() != RoleAssignment.Role.Doctor);

        Session session = sessionRepository.findById(sessionId).orElseThrow(() -> new SessionNotFoundException(sessionId));
        if (!session.getClinic().getId().equals(clinicId)) {
            throw new SessionNotFoundException(sessionId);
        }
        if (doctorOnly) {
            UUID myDoctorProfileId = doctorProfileRepository
                    .findByAccount_Id(callerAccountId)
                    .map(dp -> dp.getId())
                    .orElse(null);
            if (!session.getDoctorProfile().getId().equals(myDoctorProfileId)) {
                throw new SessionNotFoundException(sessionId);
            }
        }

        Map<UUID, BookingDetail> bookingsBySlotId =
                bookingRepository.findBySlot_Session_IdAndStatus(sessionId, BookingStatus.ACTIVE).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                booking -> booking.getSlot().getId(), BookingDetail::from));

        var slots = slotRepository.findBySession_Id(sessionId).stream()
                .map((Function<com.cms.scheduling.Slot, SlotDetail>)
                        slot -> SlotDetail.of(slot, bookingsBySlotId.get(slot.getId())))
                .toList();

        return SessionDaySheetResponse.of(session, slots);
    }
}
