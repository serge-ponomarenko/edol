package org.spon.edolhub.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

@Component
public class HubSessionExpiryFilter extends OncePerRequestFilter {

    private final Duration maximumSessionAge;

    HubSessionExpiryFilter(@Value("${edol-hub.session.maximum-age:8h}") Duration maximumSessionAge) {
        if (maximumSessionAge.isNegative() || maximumSessionAge.isZero()) {
            throw new IllegalArgumentException("edol-hub.session.maximum-age must be positive");
        }
        this.maximumSessionAge = maximumSessionAge;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(HubSessionAttributes.AUTHENTICATED_AT) instanceof Long authenticatedAt
                && Instant.now().isAfter(Instant.ofEpochMilli(authenticatedAt).plus(maximumSessionAge))) {
            session.invalidate();
            response.sendRedirect("/oauth2/authorization/edol-keycloak");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
