package com.cms.clinical.repository;

import com.cms.clinical.domain.ConsultationNote;


import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConsultationNoteRepository extends JpaRepository<ConsultationNote, UUID> {

    Optional<ConsultationNote> findByBooking_Id(UUID bookingId);

    /**
     * 059-patient-clinical-record-access (data-model.md): of the given booking ids, which have a
     * consultation note AND belong to this patient account - scoped in the query itself, not just
     * at a booking-lookup step, so another patient's booking ids are silently excluded rather than
     * separately flagged (research.md Decision 3).
     */
    @Query("SELECT DISTINCT n.booking.id FROM ConsultationNote n "
            + "WHERE n.booking.id IN :bookingIds AND n.booking.patient.patientAccount.id = :patientAccountId")
    Set<UUID> findBookingIdsWithNoteForPatient(
            @Param("bookingIds") Collection<UUID> bookingIds, @Param("patientAccountId") UUID patientAccountId);
}
