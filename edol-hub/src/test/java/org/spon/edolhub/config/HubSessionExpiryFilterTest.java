package org.spon.edolhub.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

class HubSessionExpiryFilterTest {

    private final HubSessionExpiryFilter filter = new HubSessionExpiryFilter();

    @Test
    void expiresAnOverEightHourSessionAndRestartsLogin() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HubSessionAttributes.AUTHENTICATED_AT,
                Instant.now().minus(Duration.ofHours(8)).minusSeconds(1).toEpochMilli());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(session.isInvalid()).isTrue();
        assertThat(response.getRedirectedUrl()).isEqualTo("/oauth2/authorization/edol-keycloak");
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void keepsARecentSessionAvailableToTheSecurityChain() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HubSessionAttributes.AUTHENTICATED_AT, Instant.now().toEpochMilli());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(session.isInvalid()).isFalse();
        verify(chain).doFilter(request, response);
    }
}
