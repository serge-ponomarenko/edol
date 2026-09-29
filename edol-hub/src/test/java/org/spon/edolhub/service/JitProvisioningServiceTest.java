package org.spon.edolhub.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.spon.edolhub.model.entity.TenantMembership;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JitProvisioningServiceTest {

    private static final OidcIdentity IDENTITY =
            new OidcIdentity("https://keycloak.example/realms/edol", "subject-1", "Owner", null);

    @Mock
    private TenantMembershipService membershipService;

    @Mock
    private JitProvisioningTransactionService transactionService;

    private JitProvisioningService provisioningService;

    @BeforeEach
    void setUp() {
        provisioningService = new JitProvisioningService(
                new IdentityContext(), new TenantContext(), membershipService, transactionService
        );
    }

    @Test
    void leavesAnUnopenedLegacyBootstrapUnclaimed() {
        when(membershipService.activeMemberships(IDENTITY)).thenReturn(List.of());
        when(transactionService.userExists(IDENTITY)).thenReturn(false);
        when(transactionService.legacyBootstrapState())
                .thenReturn(new JitProvisioningTransactionService.LegacyBootstrapState(true, false, false));

        assertThat(provisioningService.provisionOrLoad(IDENTITY)).isEmpty();

        verify(transactionService, never()).claimLegacyTenant(IDENTITY);
        verify(transactionService, never()).createPersonalTenant(eq(IDENTITY), any());
    }
}
