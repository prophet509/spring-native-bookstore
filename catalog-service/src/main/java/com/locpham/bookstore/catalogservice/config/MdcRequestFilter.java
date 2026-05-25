package com.locpham.bookstore.catalogservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

public class MdcRequestFilter extends OncePerRequestFilter {

    private static final String USER_ID_KEY = "userId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                Jwt jwt = jwtAuth.getToken();
                String userId = jwt.getClaimAsString("preferred_username");
                if (userId == null) {
                    userId = jwt.getSubject();
                }
                if (userId != null) {
                    MDC.put(USER_ID_KEY, userId);
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(USER_ID_KEY);
        }
    }
}
