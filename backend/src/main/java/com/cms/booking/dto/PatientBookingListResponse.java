package com.cms.booking.dto;

import java.util.List;

public record PatientBookingListResponse(
        List<PatientBookingSummaryResponse> bookings, int page, int pageSize, long totalCount) {}
