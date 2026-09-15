package com.cms.scheduling;

import com.cms.identity.account.SecurityConfig;
import com.cms.scheduling.dto.CreateScheduleRequest;
import com.cms.scheduling.dto.ScheduleResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/clinics/{clinicId}/doctors/{doctorProfileId}/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @PostMapping
    public ResponseEntity<ScheduleResponse> create(
            @PathVariable UUID clinicId,
            @PathVariable UUID doctorProfileId,
            @RequestBody CreateScheduleRequest request,
            Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        Schedule schedule = scheduleService.create(callerAccountId, clinicId, doctorProfileId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ScheduleResponse.of(schedule));
    }

    @GetMapping
    public List<ScheduleResponse> list(
            @PathVariable UUID clinicId, @PathVariable UUID doctorProfileId, Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        return scheduleService.list(callerAccountId, clinicId, doctorProfileId).stream()
                .map(ScheduleResponse::of)
                .toList();
    }

    @PatchMapping("/{scheduleId}")
    public ScheduleResponse edit(
            @PathVariable UUID clinicId,
            @PathVariable UUID doctorProfileId,
            @PathVariable UUID scheduleId,
            @RequestBody CreateScheduleRequest request,
            Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        Schedule schedule = scheduleService.edit(callerAccountId, clinicId, doctorProfileId, scheduleId, request);
        return ScheduleResponse.of(schedule);
    }
}
