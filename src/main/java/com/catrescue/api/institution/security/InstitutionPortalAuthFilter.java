package com.catrescue.api.institution.security;

import com.catrescue.api.config.InstitutionProperties;
import com.catrescue.api.institution.persistence.OrganizationEntity;
import com.catrescue.api.institution.repository.OrganizationJpaRepository;
import com.catrescue.api.institution.support.InstitutionTokenHasher;
import com.catrescue.api.institution.web.InstitutionRequestAttributes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
public class InstitutionPortalAuthFilter extends OncePerRequestFilter {

    private final InstitutionProperties institutionProperties;
    private final OrganizationJpaRepository organizationJpaRepository;

    public InstitutionPortalAuthFilter(
            InstitutionProperties institutionProperties,
            OrganizationJpaRepository organizationJpaRepository
    ) {
        this.institutionProperties = institutionProperties;
        this.organizationJpaRepository = organizationJpaRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/v1/institution/portal/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = extractToken(request);
        if (token == null || token.isBlank()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"missing_institution_token\"}");
            return;
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot >= token.length() - 1) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"invalid_institution_token_format\"}");
            return;
        }
        String publicId = token.substring(0, dot).trim();
        Optional<OrganizationEntity> orgOpt = organizationJpaRepository.findByApiPublicId(publicId);
        if (orgOpt.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"unknown_institution\"}");
            return;
        }
        OrganizationEntity org = orgOpt.get();
        String pepper = institutionProperties.getTokenPepper() != null ? institutionProperties.getTokenPepper() : "";
        String expected = InstitutionTokenHasher.sha256Hex(pepper + token);
        if (!InstitutionTokenHasher.constantTimeEquals(expected, org.getApiSecretHash())) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"invalid_institution_token\"}");
            return;
        }
        if (org.getSubscriptionExpiresAt() != null && Instant.now().isAfter(org.getSubscriptionExpiresAt())) {
            response.setStatus(402);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"institution_subscription_expired\"}");
            return;
        }
        request.setAttribute(InstitutionRequestAttributes.ORGANIZATION, org);
        filterChain.doFilter(request, response);
    }

    private static String extractToken(HttpServletRequest request) {
        String direct = request.getHeader("X-Institution-Token");
        if (direct != null && !direct.isBlank()) {
            return direct.trim();
        }
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return auth.substring(7).trim();
        }
        return null;
    }
}
