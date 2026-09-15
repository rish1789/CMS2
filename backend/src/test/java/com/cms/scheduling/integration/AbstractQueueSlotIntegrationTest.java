package com.cms.scheduling.integration;

import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.scheduling.QueueSlotService;
import com.cms.scheduling.ScheduleMode;
import com.cms.scheduling.Session;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;

/** 019: extends 018's fixture directly, adding QueueSlotService and a helper to obtain a real, generated Queue/Token Session. */
public abstract class AbstractQueueSlotIntegrationTest extends AbstractSlotGenerationIntegrationTest {

    @Autowired
    protected QueueSlotService queueSlotService;

    /** A real, generated Queue/Token Session for a doctor staffed at the given clinic (via 011/013's own generation path). */
    protected Session saveQueueSession(Clinic clinic, DoctorProfile doctor) {
        var schedule = saveEveryDaySchedule(clinic, doctor, ScheduleMode.QUEUE, null);
        sessionGenerationService.generate(LocalDate.now());
        return sessionRepository.findBySchedule_Id(schedule.getId()).get(0);
    }
}
