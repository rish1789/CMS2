package com.cms.clinical.dto;

import com.cms.clinical.PrescriptionItem;
import java.util.UUID;

public record PrescriptionItemResponse(
        UUID id, String medicationName, String dosage, String frequency, String duration, String instructions) {

    public static PrescriptionItemResponse of(PrescriptionItem item) {
        return new PrescriptionItemResponse(
                item.getId(),
                item.getMedicationName(),
                item.getDosage(),
                item.getFrequency(),
                item.getDuration(),
                item.getInstructions());
    }
}
