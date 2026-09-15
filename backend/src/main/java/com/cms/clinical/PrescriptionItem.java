package com.cms.clinical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** 035: one medication line entry belonging to exactly one {@link Prescription} - no independent lifecycle of its own. */
@Entity
@Table(name = "prescription_item")
public class PrescriptionItem {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "prescription_id", nullable = false)
    private Prescription prescription;

    @Column(name = "medication_name", nullable = false)
    private String medicationName;

    @Column(nullable = false)
    private String dosage;

    @Column(nullable = false)
    private String frequency;

    @Column(nullable = false)
    private String duration;

    @Column
    private String instructions;

    protected PrescriptionItem() {
        // JPA
    }

    public PrescriptionItem(
            Prescription prescription, String medicationName, String dosage, String frequency, String duration, String instructions) {
        this.prescription = prescription;
        this.medicationName = medicationName;
        this.dosage = dosage;
        this.frequency = frequency;
        this.duration = duration;
        this.instructions = instructions;
    }

    public UUID getId() {
        return id;
    }

    public Prescription getPrescription() {
        return prescription;
    }

    public String getMedicationName() {
        return medicationName;
    }

    public String getDosage() {
        return dosage;
    }

    public String getFrequency() {
        return frequency;
    }

    public String getDuration() {
        return duration;
    }

    public String getInstructions() {
        return instructions;
    }
}
