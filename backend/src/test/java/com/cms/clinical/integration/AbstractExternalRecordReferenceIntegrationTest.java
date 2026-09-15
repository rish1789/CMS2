package com.cms.clinical.integration;

import com.cms.clinical.ExternalRecordReferenceRepository;
import com.cms.clinical.ExternalRecordReferenceService;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;

/** 036: extends 035's fixture (mirrors this codebase's established fixture-inheritance pattern) - reuses its `operationsToken` helper unchanged. */
public abstract class AbstractExternalRecordReferenceIntegrationTest extends AbstractPrescriptionIntegrationTest {

    @Autowired
    protected ExternalRecordReferenceRepository externalRecordReferenceRepository;

    @Autowired
    protected ExternalRecordReferenceService externalRecordReferenceService;

    /** Runs before the superclass's own cleanup (JUnit 5's subclass-before-superclass @AfterEach order) - references booking, which the superclass deletes. */
    @AfterEach
    void cleanExternalRecordReferences() {
        externalRecordReferenceRepository.deleteAll();
    }
}
