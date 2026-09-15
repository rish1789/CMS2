package com.cms.waitlist.dto;

import com.cms.waitlist.WaitlistEntry;
import com.cms.waitlist.WaitlistEntryStatus;
import java.time.Instant;
import java.util.UUID;

public record WaitlistEntryResponse(
        UUID id,
        UUID clinicId,
        UUID doctorProfileId,
        String specialization,
        WaitlistEntryStatus status,
        Instant joinedAt,
        // _diagnostics [MEDIUM] - [WAITLIST_CLAIM] - [NO_EXPIRY_VISIBILITY]: previously never
        // serialized to any patient-facing client, even though WaitlistEntry.offer() always
        // stamps a real 30-minute deadline - a patient with an OFFERED entry had zero visibility
        // into how much of the window remains.
        Instant offeredAt,
        Instant offerExpiresAt,
        // _diagnostics [HIGH] - [WAITLIST_CLAIM] - [RAW_ID_ENTRY]: the *matched* doctor for an
        // OFFERED entry - distinct from doctorProfileId above, which is the originally-requested
        // doctor and is null when the patient joined for "any doctor with a specialization".
        // ClaimOfferCard needs this to fetch that doctor's appointment types instead of asking
        // the patient to type an Appointment Type ID.
        UUID offeredDoctorProfileId) {

    public static WaitlistEntryResponse of(WaitlistEntry entry) {
        return new WaitlistEntryResponse(
                entry.getId(),
                entry.getClinic().getId(),
                entry.getDoctorProfile() != null ? entry.getDoctorProfile().getId() : null,
                entry.getSpecialization(),
                entry.getStatus(),
                entry.getJoinedAt(),
                entry.getOfferedAt(),
                entry.getOfferExpiresAt(),
                entry.getOfferedSlot() != null
                        ? entry.getOfferedSlot().getSession().getDoctorProfile().getId()
                        : null);
    }
}
