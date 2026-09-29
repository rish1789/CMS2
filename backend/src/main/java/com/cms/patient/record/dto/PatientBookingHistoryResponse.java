package com.cms.patient.record.dto;

import com.cms.booking.domain.Booking;
import java.util.List;
import org.springframework.data.domain.Page;

public record PatientBookingHistoryResponse(
        List<PatientBookingSummaryResponse> bookings, int page, int pageSize, long totalCount) {

    public static PatientBookingHistoryResponse from(Page<Booking> bookingPage) {
        return new PatientBookingHistoryResponse(
                bookingPage.getContent().stream().map(PatientBookingSummaryResponse::from).toList(),
                bookingPage.getNumber(),
                bookingPage.getSize(),
                bookingPage.getTotalElements());
    }
}
