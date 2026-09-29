package com.cms.clinical.repository;

import com.cms.clinical.domain.Prescription;


import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {

    List<Prescription> findByBooking_Id(UUID bookingId);

    /** 059-patient-clinical-record-access (data-model.md): mirrors {@code ConsultationNoteRepository.findBookingIdsWithNoteForPatient}'s shape exactly, for Prescription. */
    @Query("SELECT DISTINCT p.booking.id FROM Prescription p "
            + "WHERE p.booking.id IN :bookingIds AND p.booking.patient.patientAccount.id = :patientAccountId")
    Set<UUID> findBookingIdsWithPrescriptionForPatient(
            @Param("bookingIds") Collection<UUID> bookingIds, @Param("patientAccountId") UUID patientAccountId);
}
