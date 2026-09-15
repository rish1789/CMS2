package com.cms.booking;

import com.cms.scheduling.BufferSlotCalculator;
import com.cms.scheduling.ColdStartBufferSlotCalculator;
import com.cms.scheduling.Session;
import com.cms.scheduling.SlotStatus;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 024: the real, data-driven {@link BufferSlotCalculator} - lives in {@code com.cms.booking}
 * (not {@code com.cms.scheduling}, despite implementing a {@code com.cms.scheduling}
 * interface) specifically to avoid a circular package dependency, since its query needs
 * {@link BookingRepository} (research.md). {@code @Primary} so {@code SlotGenerationService}'s
 * existing constructor-injection of {@code BufferSlotCalculator} resolves to this bean
 * automatically - no change needed there.
 */
@Component
@Primary
public class RiskBasedBufferSlotCalculator implements BufferSlotCalculator {

    private static final int TRAILING_WINDOW_DAYS = 90;
    private static final int MINIMUM_SAMPLE_SIZE = 5;
    private static final double MAX_BUFFER_PERCENTAGE = 0.20;
    private static final int ABSOLUTE_CAP = 3;

    private final ColdStartBufferSlotCalculator coldStartCalculator;
    private final BookingRepository bookingRepository;

    public RiskBasedBufferSlotCalculator(
            ColdStartBufferSlotCalculator coldStartCalculator, BookingRepository bookingRepository) {
        this.coldStartCalculator = coldStartCalculator;
        this.bookingRepository = bookingRepository;
    }

    @Override
    public int calculateBufferSlotCount(Session session) {
        LocalDate windowEnd = session.getSessionDate();
        LocalDate windowStart = windowEnd.minusDays(TRAILING_WINDOW_DAYS);

        List<Booking> bookings = bookingRepository.findByDoctorAndFixedTimeWindow(
                session.getDoctorProfile().getId(), windowStart, windowEnd);

        // FR-001: below the sample floor, defer entirely to 018's already-correct flat default.
        if (bookings.size() < MINIMUM_SAMPLE_SIZE) {
            return coldStartCalculator.calculateBufferSlotCount(session);
        }

        long noShowCount =
                bookings.stream().filter(b -> b.getSlot().getStatus() == SlotStatus.NO_SHOW).count();
        double noShowRate = (double) noShowCount / bookings.size();

        // Clarifications: the rate becomes the buffer percentage directly, capped at 20%.
        double bufferPercentage = Math.min(noShowRate, MAX_BUFFER_PERCENTAGE);

        int totalSlots = computeSessionTotalSlots(session);
        long rawBufferCount = Math.round(bufferPercentage * totalSlots);

        return (int) Math.min(rawBufferCount, ABSOLUTE_CAP);
    }

    /** Independently recomputes 018's own SlotGenerationService.computeSlotStartTimes()'s slot count without needing that list itself. */
    private int computeSessionTotalSlots(Session session) {
        long totalMinutes = Duration.between(session.getStartTime(), session.getEndTime()).toMinutes();
        return (int) (totalMinutes / session.getSlotIntervalMinutes());
    }
}
