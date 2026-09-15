package com.cms.scheduling;

import java.time.LocalDate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Sits behind {@code SuperAdminSecurityConfig}'s existing {@code /api/v1/admin/**} chain - no security-config change needed. */
@RestController
@RequestMapping("/api/v1/admin/sessions")
public class SessionGenerationController {

    private final SessionGenerationService sessionGenerationService;

    public SessionGenerationController(SessionGenerationService sessionGenerationService) {
        this.sessionGenerationService = sessionGenerationService;
    }

    @PostMapping("/generate")
    public GenerateSessionsResponse generate() {
        LocalDate runDate = LocalDate.now();
        int sessionsCreated = sessionGenerationService.generate(runDate);
        return new GenerateSessionsResponse(runDate, sessionsCreated);
    }

    public record GenerateSessionsResponse(LocalDate runDate, int sessionsCreated) {}
}
