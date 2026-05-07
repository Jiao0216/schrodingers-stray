package com.catrescue.api.institution.security;

import com.catrescue.api.config.InstitutionProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 14)
public class InstitutionAdminAuthFilter extends OncePerRequestFilter {

    public static final String ADMIN_HEADER = "X-Cat-Rescue-Institution-Admin";

    private final InstitutionProperties institutionProperties;

    public InstitutionAdminAuthFilter(InstitutionProperties institutionProperties) {
        this.institutionProperties = institutionProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/v1/institution/admin/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String expected = institutionProperties.getAdminToken() == null ? "" : institutionProperties.getAdminToken().trim();
        if (expected.isBlank()) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"institution_admin_not_configured\"}");
            return;
        }
        String submitted = request.getHeader(ADMIN_HEADER);
        if (submitted == null || !expected.equals(submitted.trim())) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"institution_admin_auth_failed\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
