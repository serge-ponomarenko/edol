package org.spon.edolhub.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.spon.edolhub.model.entity.TenantMembership;
import org.spon.edolhub.service.JitProvisioningService;
import org.spon.edolhub.service.OidcIdentity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OidcLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JitProvisioningService provisioningService;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        if (!(authentication instanceof OAuth2AuthenticationToken token)
                || !(token.getPrincipal() instanceof org.springframework.security.oauth2.core.oidc.user.OidcUser user)) {
            throw new ServletException("EDOL Hub requires an OIDC authentication");
        }

        List<TenantMembership> memberships = provisioningService.provisionOrLoad(OidcIdentity.from(user));
        if (memberships.size() == 1) {
            request.getSession().setAttribute(HubSessionAttributes.ACTIVE_TENANT_ID, memberships.getFirst().getTenant().getId());
        } else {
            request.getSession().removeAttribute(HubSessionAttributes.ACTIVE_TENANT_ID);
        }
        request.getSession().setAttribute(HubSessionAttributes.AUTHENTICATED_AT, Instant.now().toEpochMilli());
        response.sendRedirect(memberships.size() > 1 ? "/tenants/select" : "/");
    }
}
