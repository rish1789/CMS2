package com.cms.clinical;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {

    List<Prescription> findByBooking_Id(UUID bookingId);
}
