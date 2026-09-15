package com.cms.clinical;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultationNoteRepository extends JpaRepository<ConsultationNote, UUID> {

    Optional<ConsultationNote> findByBooking_Id(UUID bookingId);
}
