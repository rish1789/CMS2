package com.cms.clinical.repository;

import com.cms.clinical.domain.ExternalRecordReference;


import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExternalRecordReferenceRepository extends JpaRepository<ExternalRecordReference, UUID> {

    List<ExternalRecordReference> findByBooking_Id(UUID bookingId);

    /** 059-patient-clinical-record-access (data-model.md): mirrors {@code ConsultationNoteRepository.findBookingIdsWithNoteForPatient}'s shape exactly, for ExternalRecordReference. */
    @Query("SELECT DISTINCT r.booking.id FROM ExternalRecordReference r "
            + "WHERE r.booking.id IN :bookingIds AND r.booking.patient.patientAccount.id = :patientAccountId")
    Set<UUID> findBookingIdsWithReferenceForPatient(
            @Param("bookingIds") Collection<UUID> bookingIds, @Param("patientAccountId") UUID patientAccountId);
}
