package com.cms.booking.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cms.booking.exception.PatientPhoneAlreadyRegisteredException;
import com.cms.booking.service.WalkInPatientRegistrar;
import com.cms.identity.clinic.Clinic;
import com.cms.patient.record.domain.Patient;
import com.cms.patient.record.repository.PatientRepository;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 074-duplicate-patient-phone FR-001/FR-002: the one place staff paths create an unlinked patient.
 * Pure Mockito - no Spring, no Docker.
 */
class WalkInPatientRegistrarTest {

    private final PatientRepository repository = mock(PatientRepository.class);
    private final WalkInPatientRegistrar registrar = new WalkInPatientRegistrar(repository);
    private final UUID clinicId = UUID.randomUUID();
    private Clinic clinic;

    @BeforeEach
    void aClinic() {
        clinic = mock(Clinic.class);
        when(clinic.getId()).thenReturn(clinicId);
    }

    private static DataIntegrityViolationException violationOf(String constraint) {
        return new DataIntegrityViolationException(
                "could not execute statement",
                new ConstraintViolationException("duplicate key", new SQLException("duplicate key"), constraint));
    }

    @Test
    void anExistingUnlinkedPatientWithThePhoneIsNamedAndNothingIsInserted() {
        Patient existing = mock(Patient.class);
        UUID existingId = UUID.randomUUID();
        when(existing.getId()).thenReturn(existingId);
        when(existing.getName()).thenReturn("Asha Rao");
        when(repository.findByClinic_IdAndPhoneAndPatientAccountIsNull(clinicId, "9876543210"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> registrar.register(clinic, "New Person", "9876543210", null))
                .isInstanceOfSatisfying(PatientPhoneAlreadyRegisteredException.class, e -> {
                    assertThat(e.existingPatientId()).isEqualTo(existingId);
                    assertThat(e.existingPatientName()).isEqualTo("Asha Rao");
                });
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void losingTheRaceOnTheUnlinkedPhoneIndexIsTheSameConflictWithoutAName() {
        when(repository.findByClinic_IdAndPhoneAndPatientAccountIsNull(clinicId, "9876543210"))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenThrow(violationOf("uq_patient_clinic_phone_unlinked"));

        assertThatThrownBy(() -> registrar.register(clinic, "New Person", "9876543210", null))
                .isInstanceOfSatisfying(PatientPhoneAlreadyRegisteredException.class, e -> {
                    assertThat(e.existingPatientId()).isNull();
                    assertThat(e.existingPatientName()).isNull();
                });
        // No re-query after the failed INSERT: the transaction is already aborted.
        verify(repository, org.mockito.Mockito.times(1)).findByClinic_IdAndPhoneAndPatientAccountIsNull(any(), any());
    }

    @Test
    void anyOtherIntegrityViolationIsRethrownUnchanged() {
        when(repository.findByClinic_IdAndPhoneAndPatientAccountIsNull(clinicId, "9876543210"))
                .thenReturn(Optional.empty());
        DataIntegrityViolationException other = violationOf("some_other_constraint");
        when(repository.saveAndFlush(any())).thenThrow(other);

        assertThatThrownBy(() -> registrar.register(clinic, "New Person", "9876543210", null)).isSameAs(other);
    }

    @Test
    void aPatientWithoutAPhoneIsCreatedWithoutAPhoneCheck() {
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Patient created = registrar.register(clinic, "No Phone", null, "nophone@example.com");

        assertThat(created.getName()).isEqualTo("No Phone");
        verify(repository, never()).findByClinic_IdAndPhoneAndPatientAccountIsNull(any(), any());
    }
}
