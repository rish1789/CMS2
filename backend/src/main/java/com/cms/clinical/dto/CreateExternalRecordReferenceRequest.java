package com.cms.clinical.dto;

import java.time.LocalDate;

public record CreateExternalRecordReferenceRequest(
        String recordType, String sourceProvider, LocalDate recordDate, String summary) {}
