package org.spon.edolhub.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.stereotype.Service;

/**
 * Obtains an access token for the Hub machine identity. Browser access tokens
 * are intentionally never forwarded to Core.
 */
@Service
@ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "secure-multi-tenant")
public class CoreServiceAccessTokenProvider {

    static final String REGISTRATION_ID = "edol-hub-service";

    private static final UsernamePasswordAuthenticationToken SERVICE_PRINCIPAL =
            new UsernamePasswordAuthenticationToken(
                    REGISTRATION_ID,
                    "N/A",
                    AuthorityUtils.NO_AUTHORITIES
            );

    private final OAuth2AuthorizedClientManager authorizedClientManager;

    public CoreServiceAccessTokenProvider(OAuth2AuthorizedClientManager authorizedClientManager) {
        this.authorizedClientManager = authorizedClientManager;
    }

    public String accessTokenValue() {
        OAuth2AuthorizedClient client = authorizedClientManager.authorize(
                OAuth2AuthorizeRequest.withClientRegistrationId(REGISTRATION_ID)
                        .principal(SERVICE_PRINCIPAL)
                        .build()
        );
        if (client == null || client.getAccessToken() == null) {
            throw new IllegalStateException("Unable to obtain an EDOL Core service access token");
        }
        return client.getAccessToken().getTokenValue();
    }
}
