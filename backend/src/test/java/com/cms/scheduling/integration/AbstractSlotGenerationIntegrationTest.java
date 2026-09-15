package com.cms.scheduling.integration;

import com.cms.scheduling.SlotRepository;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;

/** 018: extends 015's fixture directly, adding Slot cleanup (must run before the parent's Session cleanup - FK). */
public abstract class AbstractSlotGenerationIntegrationTest extends AbstractSessionGenerationIntegrationTest {

    @Autowired
    protected SlotRepository slotRepository;

    @AfterEach
    void cleanSlots() {
        slotRepository.deleteAll();
    }
}
