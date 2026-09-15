package com.cms.clinical.dto;

public record PrescriptionItemRequest(
        String medicationName, String dosage, String frequency, String duration, String instructions) {}
