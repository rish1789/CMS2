package com.cms.clinical.dto;

import com.cms.clinical.Prescription;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PrescriptionResponse(
        UUID id, UUID bookingId, UUID doctorProfileId, Instant createdAt, List<PrescriptionItemResponse> items) {

    public static PrescriptionResponse of(Prescription prescription) {
        return new PrescriptionResponse(
                prescription.getId(),
                prescription.getBooking().getId(),
                prescription.getDoctorProfile().getId(),
                prescription.getCreatedAt(),
                prescription.getItems().stream().map(PrescriptionItemResponse::of).toList());
    }
}
