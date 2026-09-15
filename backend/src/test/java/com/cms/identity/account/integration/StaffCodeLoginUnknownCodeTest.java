package com.cms.identity.account.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.staff.integration.AbstractStaffIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** T003: unrecognized staff code -> 401, same shape as unknown email (FR-004). */
class StaffCodeLoginUnknownCodeTest extends AbstractStaffIntegrationTest {

    @Test
    void unknownStaffCodeRejectedWithSameShapeAsUnknownEmail() throws Exception {
        mockMvc.perform(post("/api/v1/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "identifier": "DR-0000", "password": "Incorrect1!" }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
    }

    @Test
    void identifierThatMatchesNeitherEmailNorStaffCodeFormatRejectedSameShape() throws Exception {
        mockMvc.perform(post("/api/v1/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "identifier": "not-a-real-identifier", "password": "Incorrect1!" }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
    }
}
