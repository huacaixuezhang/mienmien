package com.mienmien.business.management.infrastructure.web;

import com.mienmien.business.management.application.security.BusinessRequestActor;
import com.mienmien.business.management.domain.repository.BusinessSessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 从 Authorization: Bearer &lt;sessionToken&gt; 解析登录用户，写入 {@link BusinessRequestActor}。
 */
@Component
@Order(20)
public class BusinessSessionAuthFilter extends OncePerRequestFilter {
    private final BusinessSessionRepository businessSessionRepository;

    public BusinessSessionAuthFilter(BusinessSessionRepository businessSessionRepository) {
        this.businessSessionRepository = businessSessionRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String uri = request.getRequestURI();
            if (uri != null && uri.startsWith("/actuator")) {
                filterChain.doFilter(request, response);
                return;
            }
            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                filterChain.doFilter(request, response);
                return;
            }
            if (uri != null && uri.startsWith("/api/v1/business/auth/")) {
                filterChain.doFilter(request, response);
                return;
            }
            if (uri != null && uri.startsWith("/api/v1/business/crypto/")) {
                filterChain.doFilter(request, response);
                return;
            }
            String token = extractBearer(request.getHeader("Authorization"));
            if (token != null && !token.isBlank()) {
                businessSessionRepository.findUserIdByValidToken(token).ifPresent(BusinessRequestActor::setUserId);
            }
            filterChain.doFilter(request, response);
        } finally {
            BusinessRequestActor.clear();
        }
    }

    private static String extractBearer(String authorization) {
        if (authorization == null) {
            return null;
        }
        String trimmed = authorization.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return trimmed.substring(7).trim();
        }
        return null;
    }
}
