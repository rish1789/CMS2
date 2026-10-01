package com.cms.discovery.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cms.discovery.DiscoveryResultRepository;
import com.cms.discovery.DiscoverySearchService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 047-backend-hardening FR-001/SC-001: proves the public, unauthenticated discovery search
 * endpoint can never return an unbounded result set, regardless of what a caller requests.
 * Pure Mockito - no Spring context, no Docker - runs in this sandbox unlike the module's
 * existing (Testcontainers-backed) integration/ suite.
 */
@ExtendWith(MockitoExtension.class)
class DiscoverySearchServiceTest {

    @Mock
    private DiscoveryResultRepository repository;

    @Test
    void requestedSizeAboveMaximumIsClampedToMax() {
        DiscoverySearchService service = new DiscoverySearchService(repository);
        when(repository.search(any(), any(), any(), any(), any())).thenReturn(List.of());

        service.search(null, null, null, null, null, null, 0, 200);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).search(eq(null), eq(null), eq(null), eq(null), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(DiscoverySearchService.MAX_PAGE_SIZE);
    }

    @Test
    void absentSizeDefaultsToTheDefaultPageSize() {
        DiscoverySearchService service = new DiscoverySearchService(repository);
        when(repository.search(any(), any(), any(), any(), any())).thenReturn(List.of());

        service.search(null, null, null, null, null, null, null, null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).search(eq(null), eq(null), eq(null), eq(null), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(DiscoverySearchService.DEFAULT_PAGE_SIZE);
        assertThat(captor.getValue().getPageNumber()).isEqualTo(0);
    }

    @Test
    void aReasonableRequestedSizeIsHonoredUnchanged() {
        DiscoverySearchService service = new DiscoverySearchService(repository);
        when(repository.search(any(), any(), any(), any(), any())).thenReturn(List.of());

        service.search(null, null, null, null, null, null, 2, 10);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).search(eq(null), eq(null), eq(null), eq(null), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
    }

    /**
     * 072-discovery-pagination FR-001: the chosen sort alone leaves ties (same name, one doctor at
     * several clinics) in no defined order under LIMIT/OFFSET, so pages could skip or repeat rows.
     * Every sort ends with the unique (doctor profile, clinic) key.
     */
    @Test
    void everySortEndsWithTheUniqueDoctorAndClinicTieBreak() {
        when(repository.search(any(), any(), any(), any(), any())).thenReturn(List.of());
        DiscoverySearchService service = new DiscoverySearchService(repository);

        service.search(null, null, null, null, "experienceYears", "desc", null, null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).search(eq(null), eq(null), eq(null), eq(null), captor.capture());
        assertThat(captor.getValue().getSort())
                .containsExactly(
                        Sort.Order.desc("dp.experienceYears"), Sort.Order.asc("dp.id"), Sort.Order.asc("c.id"));
    }
}
