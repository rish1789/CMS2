package com.cms.discovery;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 035: the public, unauthenticated discovery search endpoint (FR-001). */
@RestController
public class DiscoveryController {

    private final DiscoverySearchService discoverySearchService;

    public DiscoveryController(DiscoverySearchService discoverySearchService) {
        this.discoverySearchService = discoverySearchService;
    }

    @GetMapping("/api/v1/discovery/search")
    public List<DiscoveryResult> search(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "city", required = false) String city,
            @RequestParam(name = "specialization", required = false) String specialization,
            @RequestParam(name = "minExperienceYears", required = false) Integer minExperienceYears,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "direction", required = false) String direction) {
        return discoverySearchService.search(q, city, specialization, minExperienceYears, sort, direction);
    }

    /** patient-search-advanced-filtering: drives the City filter dropdown. */
    @GetMapping("/api/v1/discovery/cities")
    public List<String> cities() {
        return discoverySearchService.listCities();
    }

    /** patient-search-advanced-filtering: drives the Specialization filter dropdown. */
    @GetMapping("/api/v1/discovery/specializations")
    public List<String> specializations() {
        return discoverySearchService.listSpecializations();
    }
}
