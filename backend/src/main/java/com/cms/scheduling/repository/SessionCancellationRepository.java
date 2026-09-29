package com.cms.scheduling.repository;

import com.cms.scheduling.domain.SessionCancellation;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionCancellationRepository extends JpaRepository<SessionCancellation, UUID> {

    List<SessionCancellation> findBySession_Id(UUID sessionId);

    boolean existsBySession_IdAndFromTimeIsNull(UUID sessionId);

    boolean existsBySession_Id(UUID sessionId);

    long countBySession_Id(UUID sessionId);

    /** 065-phase1-stabilization: the session list's bulk "whole-cancelled" flag - one query per page. */
    @Query("SELECT c.session.id FROM SessionCancellation c WHERE c.fromTime IS NULL AND c.session.id IN :sessionIds")
    List<UUID> findWholeCancelledSessionIdsIn(@Param("sessionIds") Collection<UUID> sessionIds);
}
