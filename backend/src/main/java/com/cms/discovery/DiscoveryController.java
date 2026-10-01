package com.cms.discovery;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 035: the public, unauthenticated discovery search endpoint (FR-001). */
@RestController
public class DiscoveryController {

    public static final String TOTAL_COUNT_HEADER = "X-Total-Count";

    private final DiscoverySearchService discoverySearchService;

    public DiscoveryController(DiscoverySearchService discoverySearchService) {
        this.discoverySearchService = discoverySearchService;
    }

    /** 072-discovery-pagination: the body stays a bare array; the total across pages is in {@code X-Total-Count}. */
    @GetMapping("/api/v1/discovery/search")
    public ResponseEntity<List<DiscoveryResult>> search(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "city", required = false) String city,
            @RequestParam(name = "specialization", required = false) String specialization,
            @RequestParam(name = "minExperienceYears", required = false) Integer minExperienceYears,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "direction", required = false) String direction,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        List<DiscoveryResult> results =
                discoverySearchService.search(q, city, specialization, minExperienceYears, sort, direction, page, size);
        long total = discoverySearchService.count(q, city, specialization, minExperienceYears);
        return ResponseEntity.ok().header(TOTAL_COUNT_HEADER, Long.toString(total)).body(results);
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
