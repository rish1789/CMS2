package com.cms.identity.account.config;

import com.cms.identity.account.exception.StaffClinicNotActiveException;
import com.cms.identity.account.service.RejectedClinicAccessGate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 062-rejected-clinic-gating (FR-007, research.md Decision 6): the single choke point that keeps
 * Doctor/Operations staff out of a rejected clinic on every {@code /api/v1/clinics/{clinicId}/**}
 * request - including with a token issued before the rejection, which a sign-in-only check would
 * miss. Acts only on staff-authenticated requests that carry a {@code clinicId} path variable;
 * everything else passes straight through untouched.
 */
public class RejectedClinicAccessInterceptor implements HandlerInterceptor {

    private final RejectedClinicAccessGate gate;

    public RejectedClinicAccessInterceptor(RejectedClinicAccessGate gate) {
        this.gate = gate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        UUID accountId = staffAccountId();
        UUID clinicId = clinicIdOf(request);
        if (accountId == null || clinicId == null || gate.allows(accountId, clinicId)) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"CLINIC_NOT_ACTIVE\",\"message\":\""
                + StaffClinicNotActiveException.MESSAGE + "\",\"failedRules\":null,\"field\":null}");
        return false;
    }

    /** The staff realm's principal is the Account id with ROLE_STAFF (StaffJwtAuthenticationFilter); anything else is not ours to gate. */
    private static UUID staffAccountId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof UUID accountId
                && token.getAuthorities().stream().anyMatch(a -> "ROLE_STAFF".equals(a.getAuthority()))) {
            return accountId;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static UUID clinicIdOf(HttpServletRequest request) {
        Object vars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(vars instanceof Map<?, ?> map) || !(map.get("clinicId") instanceof String raw)) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null; // a malformed id is the controller's own 400 to give, not this gate's
        }
    }
}
