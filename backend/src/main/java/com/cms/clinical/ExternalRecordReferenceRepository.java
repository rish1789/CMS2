package com.cms.clinical;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalRecordReferenceRepository extends JpaRepository<ExternalRecordReference, UUID> {

    List<ExternalRecordReference> findByBooking_Id(UUID bookingId);
}
