package com.cms.booking.api;

import com.cms.booking.service.ScheduleDeletionService;


import com.cms.identity.account.config.SecurityConfig;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 055-schedule-break-window: same path as {@code ScheduleController}'s POST/GET/PATCH, a
 * separate class only because ScheduleService's own Javadoc documents it as never touching
 * Session/Slot - deleting a Schedule genuinely must (the hard FK Session.schedule_id, even
 * nullable, forces every referencing Session to be resolved first). Authorization mirrors
 * ScheduleService.requireAuthorized (ClinicAdmin or the doctor themselves), not the
 * Operations-or-ClinicAdmin gate booking-module session write-actions use - this is a
 * scheduling-resource endpoint, not a booking one.
 */
@RestController
public class ScheduleDeletionController {

    private final ScheduleDeletionService scheduleDeletionService;

    public ScheduleDeletionController(ScheduleDeletionService scheduleDeletionService) {
        this.scheduleDeletionService = scheduleDeletionService;
    }

    @DeleteMapping("/api/v1/clinics/{clinicId}/doctors/{doctorProfileId}/schedules/{scheduleId}")
    public void delete(
            @PathVariable UUID clinicId,
            @PathVariable UUID doctorProfileId,
            @PathVariable UUID scheduleId,
            Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        scheduleDeletionService.deleteSchedule(callerAccountId, clinicId, doctorProfileId, scheduleId);
    }
}
