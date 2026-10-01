package com.cms.discovery.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.clinic.Clinic;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 072-discovery-pagination (live-audit finding 6): every match is reachable across pages, ties on
 * the sort field do not cause omissions or duplicates in an unchanged dataset, and the total is
 * readable by a browser. Synthetic data only: 25 eligible doctors who all share one name, so the
 * default name sort is all ties and only the unique tie-break orders them.
 */
class DiscoverySearchPagingTest extends AbstractDiscoveryIntegrationTest {

    private static final int MATCHES = 25;

    @BeforeEach
    void twentyFiveDoctorsWithTheSameName() {
        Clinic pune = saveClinic("Pune Paging Clinic", "1 Paging Road", "Pune", true);
        Clinic noida = saveClinic("Noida Paging Clinic", "2 Paging Road", "Noida", true);
        for (int i = 0; i < MATCHES; i++) {
            var doctor = saveDoctorProfile("Dr. Same Name", "LIC-PAGE-" + i, "General Medicine", true, true);
            linkDoctorToClinic(doctor, i < 15 ? pune : noida, true);
        }
    }

    private List<String> pageKeys(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        List<String> doctors = JsonPath.read(body, "$[*].doctorProfileId");
        List<String> clinics = JsonPath.read(body, "$[*].clinicId");
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < doctors.size(); i++) {
            keys.add(doctors.get(i) + "/" + clinics.get(i));
        }
        return keys;
    }

    @Test
    void everyMatchIsOnExactlyOnePageAndTheTotalIsReported() throws Exception {
        MvcResult first = mockMvc.perform(get("/api/v1/discovery/search").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", String.valueOf(MATCHES)))
                .andExpect(jsonPath("$.length()").value(20))
                .andReturn();
        MvcResult second = mockMvc.perform(get("/api/v1/discovery/search").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", String.valueOf(MATCHES)))
                .andExpect(jsonPath("$.length()").value(5))
                .andReturn();

        List<String> all = new ArrayList<>(pageKeys(first));
        all.addAll(pageKeys(second));
        Set<String> distinct = new HashSet<>(all);
        assertThat(all).hasSize(MATCHES);
        assertThat(distinct).hasSize(MATCHES);
    }

    @Test
    void theSameRequestReturnsTheSameOrder() throws Exception {
        List<String> once = pageKeys(mockMvc.perform(get("/api/v1/discovery/search").param("size", "7").param("page", "1"))
                .andReturn());
        List<String> again = pageKeys(mockMvc.perform(get("/api/v1/discovery/search").param("size", "7").param("page", "1"))
                .andReturn());
        assertThat(once).hasSize(7).isEqualTo(again);
    }

    @Test
    void aPagePastTheEndIsEmptyButStillReportsTheTotal() throws Exception {
        mockMvc.perform(get("/api/v1/discovery/search").param("page", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0))
                .andExpect(header().string("X-Total-Count", String.valueOf(MATCHES)));
    }

    @Test
    void theTotalCountsOnlyFilteredMatches() throws Exception {
        mockMvc.perform(get("/api/v1/discovery/search").param("city", "Noida"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(header().string("X-Total-Count", "10"));
        mockMvc.perform(get("/api/v1/discovery/search").param("q", "no such doctor"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "0"));
    }

    @Test
    void anAllowedBrowserOriginCanReadTheTotal() throws Exception {
        mockMvc.perform(get("/api/v1/discovery/search").header(HttpHeaders.ORIGIN, "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, Matchers.containsString("X-Total-Count")));
    }
}
