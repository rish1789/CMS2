package com.cms.identity.admin;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Super Admin console redesign: the auto-deletion half of the reject/restore/delete lifecycle -
 * mirrors {@code RetentionPurgeService} (034)'s service/trigger split and Clock-injected
 * testability exactly. A clinic or doctor profile sitting in Rejected past the retention window
 * is permanently deleted using the same gate as a manual delete (ClinicVerificationService /
 * DoctorVerificationService's {@code deleteGuarded}) - real attached activity is skipped, not
 * force-deleted, even once it's past the window.
 */
@Service
public class RejectedRecordPurgeService {

    private final int retentionDays;
    private final ClinicVerificationService clinicVerificationService;
    private final DoctorVerificationService doctorVerificationService;
    private final Clock clock;

    // Explicit @Autowired: this class has a second (package-private, test-only) constructor
    // below, and Spring's implicit single-constructor autowiring only applies when there is
    // exactly one constructor - see RetentionPurgeService's identical precedent for why.
    @Autowired
    public RejectedRecordPurgeService(
            @Value("${admin.rejection-retention-days:30}") int retentionDays,
            ClinicVerificationService clinicVerificationService,
            DoctorVerificationService doctorVerificationService) {
        this(retentionDays, clinicVerificationService, doctorVerificationService, Clock.systemUTC());
    }

    RejectedRecordPurgeService(
            int retentionDays,
            ClinicVerificationService clinicVerificationService,
            DoctorVerificationService doctorVerificationService,
            Clock clock) {
        this.retentionDays = retentionDays;
        this.clinicVerificationService = clinicVerificationService;
        this.doctorVerificationService = doctorVerificationService;
        this.clock = clock;
    }

    /** Runs the full sweep and returns how many clinics and doctor profiles were actually purged. */
    public PurgeResult purge() {
        Instant cutoff = Instant.now(clock).minus(retentionDays, ChronoUnit.DAYS);
        int clinicsPurged = clinicVerificationService.purgeExpiredRejections(cutoff);
        int doctorsPurged = doctorVerificationService.purgeExpiredRejections(cutoff);
        return new PurgeResult(clinicsPurged, doctorsPurged);
    }

    public record PurgeResult(int clinicsPurged, int doctorsPurged) {}
}
