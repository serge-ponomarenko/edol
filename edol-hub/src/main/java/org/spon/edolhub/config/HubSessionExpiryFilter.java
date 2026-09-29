package org.spon.edolhub.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

@Component
public class HubSessionExpiryFilter extends OncePerRequestFilter {

    private static final Duration MAX_SESSION_AGE = Duration.ofHours(8);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(HubSessionAttributes.AUTHENTICATED_AT) instanceof Long authenticatedAt
                && Instant.now().isAfter(Instant.ofEpochMilli(authenticatedAt).plus(MAX_SESSION_AGE))) {
            session.invalidate();
            response.sendRedirect("/oauth2/authorization/edol-keycloak");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
