package org.spon.edolhub.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/** Safe dependency diagnostics; never include remote response bodies or credentials. */
public final class ManagementFailureMessage {
    private ManagementFailureMessage() { }

    public static String describe(RuntimeException error, String dependency) {
        if (error instanceof OAuth2AuthorizationException) {
            return dependency + " service login failed. Check the service client credentials and requested scopes.";
        }
        if (error instanceof RestClientResponseException response) {
            return switch (response.getStatusCode().value()) {
                case 400, 422 -> dependency + " rejected the settings. Check the entered values.";
                case 401 -> dependency + " rejected service authentication. Check token issuer and audience.";
                case 403 -> dependency + " denied access. Check service scopes and tenant authorization.";
                case 404 -> dependency + " resource is unavailable in the current tenant.";
                case 409 -> dependency + " reported a conflict. Refresh the page before retrying.";
                default -> dependency + " is temporarily unavailable. Retry after checking the service status.";
            };
        }
        if (error instanceof AccessDeniedException) {
            return "Owner permission is required for this operation.";
        }
        if (error instanceof ResourceAccessException) {
            return dependency + " cannot be reached. Check its service address and port.";
        }
        return dependency + " operation failed. Check the service status before retrying.";
    }

    public static int status(RuntimeException error) {
        return error instanceof RestClientResponseException response ? response.getStatusCode().value() : 0;
    }
}
