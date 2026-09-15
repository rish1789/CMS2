package com.cms.clinical.dto;

import com.cms.clinical.ExternalRecordReference;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ExternalRecordReferenceResponse(
        UUID id,
        UUID bookingId,
        UUID doctorProfileId,
        String recordType,
        String sourceProvider,
        LocalDate recordDate,
        String summary,
        Instant createdAt) {

    public static ExternalRecordReferenceResponse of(ExternalRecordReference reference) {
        return new ExternalRecordReferenceResponse(
                reference.getId(),
                reference.getBooking().getId(),
                reference.getDoctorProfile().getId(),
                reference.getRecordType(),
                reference.getSourceProvider(),
                reference.getRecordDate(),
                reference.getSummary(),
                reference.getCreatedAt());
    }
}
