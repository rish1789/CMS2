package com.cms.discovery;

import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * 035: originally a thin pass-through with all logic in the repository query.
 * patient-search-advanced-filtering extends it with the one piece of logic that belongs at
 * this layer, not the query - normalizing/validating caller input (blank-to-null, sort
 * allow-listing) before it ever reaches JPQL.
 */
@Service
public class DiscoverySearchService {

    private static final String DEFAULT_SORT_FIELD = "doctorName";

    private final DiscoveryResultRepository repository;

    public DiscoverySearchService(DiscoveryResultRepository repository) {
        this.repository = repository;
    }

    /**
     * A {@code null}, empty, or whitespace-only value for any filter is treated as "no
     * filter" (035 spec Edge Cases, extended to every new filter here) - normalized here so
     * the repository query only ever needs to check for {@code null}. An invalid/unrecognized
     * {@code sortField} or {@code sortDirection} falls back silently to the default rather
     * than throwing (unlike the authenticated admin queues) - this is a public, unauthenticated
     * search page, and a stray/stale sort param should never break someone's search.
     */
    public List<DiscoveryResult> search(
            String q, String city, String specialization, Integer minExperienceYears, String sortField, String sortDirection) {
        String searchPattern = normalizeToLikePattern(q);
        String normalizedCity = normalizeToLowercase(city);
        String normalizedSpecialization = normalizeToLowercase(specialization);
        return repository.search(
                searchPattern, normalizedCity, normalizedSpecialization, minExperienceYears, resolveSort(sortField, sortDirection));
    }

    public List<String> listCities() {
        return repository.findDistinctEligibleCities();
    }

    public List<String> listSpecializations() {
        return repository.findDistinctEligibleSpecializations();
    }

    private String normalize(String value) {
        String trimmed = value == null ? null : value.trim();
        return (trimmed == null || trimmed.isEmpty()) ? null : trimmed;
    }

    /** Pre-formats the LIKE pattern in Java - never via JPQL CONCAT on a nullable bound parameter. */
    private String normalizeToLikePattern(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : "%" + normalized.toLowerCase() + "%";
    }

    /** Pre-lowercases in Java - never via a bare JPQL {@code LOWER(:param)} on a nullable parameter (see the repository's own javadoc for why). */
    private String normalizeToLowercase(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toLowerCase();
    }

    private Sort resolveSort(String sortField, String sortDirection) {
        String field = sortField == null ? DEFAULT_SORT_FIELD : sortField;
        String property =
                switch (field) {
                    case "doctorName" -> "a.name";
                    case "clinicName" -> "c.name";
                    case "specialization" -> "dp.specialization";
                    case "experienceYears" -> "dp.experienceYears";
                    default -> "a.name";
                };
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDirection) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }
}
