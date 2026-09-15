package com.cms.clinical.dto;

import java.util.List;

public record CreatePrescriptionRequest(List<PrescriptionItemRequest> items) {}
