package com.cms.clinical.dto;

import com.cms.clinical.ConsultationNote;
import java.time.Instant;
import java.util.UUID;

public record ConsultationNoteResponse(UUID id, UUID bookingId, UUID doctorProfileId, String content, Instant createdAt) {

    public static ConsultationNoteResponse of(ConsultationNote note) {
        return new ConsultationNoteResponse(
                note.getId(),
                note.getBooking().getId(),
                note.getDoctorProfile().getId(),
                note.getContent(),
                note.getCreatedAt());
    }
}
