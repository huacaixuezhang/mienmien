package com.mienmien.business.management.infrastructure.web;

import com.mienmien.business.management.application.security.BusinessRequestActor;
import com.mienmien.business.management.domain.repository.BusinessSessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * B 端 API 会话校验：除注册、登录外需携带 {@code Authorization: Bearer <session_token>}。
 */
public class BusinessSessionAuthFilter extends OncePerRequestFilter {
    private static final String PREFIX = "/api/v1/business";

    private final BusinessSessionRepository businessSessionRepository;

    public BusinessSessionAuthFilter(BusinessSessionRepository businessSessionRepository) {
        this.businessSessionRepository = businessSessionRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null || !uri.startsWith(PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        String uri = request.getRequestURI();
        if (isPermitAll(uri)) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = extractBearerToken(request);
        if (token == null || token.isBlank()) {
            writeUnauthorized(response);
            return;
        }
        var userId = businessSessionRepository.findUserIdByValidToken(token);
        if (userId.isEmpty()) {
            writeUnauthorized(response);
            return;
        }
        BusinessRequestActor.setUserId(userId.get());
        try {
            filterChain.doFilter(request, response);
        } finally {
            BusinessRequestActor.clear();
        }
    }

    private static boolean isPermitAll(String uri) {
        return uri.endsWith("/auth/register") || uri.endsWith("/auth/login");
    }

    private static String extractBearerToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null) {
            return null;
        }
        String trimmed = auth.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return trimmed.substring(7).trim();
        }
        return null;
    }

    private static void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        byte[] body = "{\"code\":\"BUS-4010\",\"message\":\"未登录或会话已失效\"}".getBytes(StandardCharsets.UTF_8);
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }
}
