package org.spon.edolhub.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edolhub.model.entity.Tenant;
import org.spon.edolhub.model.entity.TenantMembership;
import org.spon.edolhub.model.entity.User;
import org.spon.edolhub.repository.TenantMembershipRepository;
import org.spon.edolhub.repository.TenantRepository;
import org.spon.edolhub.repository.UserRepository;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JitProvisioningTransactionServiceTest {

    private static final OidcIdentity IDENTITY =
            new OidcIdentity("https://keycloak.example/realms/edol", "subject-1", "Owner", null);

    @Mock
    private UserRepository userRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantMembershipRepository membershipRepository;

    @Mock
    private JdbcClient jdbcClient;

    @Test
    void savesMembershipWithTheManagedTenantReturnedByTheRepository() {
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000751");
        User persistedUser = new User();
        persistedUser.setId(UUID.fromString("00000000-0000-0000-0000-000000000752"));
        Tenant persistedTenant = new Tenant();
        persistedTenant.setId(tenantId);
        ArgumentCaptor<TenantMembership> membershipCaptor = ArgumentCaptor.forClass(TenantMembership.class);
        JitProvisioningTransactionService service = new JitProvisioningTransactionService(
                userRepository, tenantRepository, membershipRepository, jdbcClient
        );

        when(userRepository.findByIssuerAndSubject(IDENTITY.issuer(), IDENTITY.subject())).thenReturn(Optional.empty());
        when(userRepository.save(org.mockito.ArgumentMatchers.any(User.class))).thenReturn(persistedUser);
        when(tenantRepository.save(org.mockito.ArgumentMatchers.any(Tenant.class))).thenReturn(persistedTenant);

        service.createPersonalTenant(IDENTITY, tenantId);

        verify(membershipRepository).save(membershipCaptor.capture());
        assertThat(membershipCaptor.getValue().getTenant()).isSameAs(persistedTenant);
        assertThat(membershipCaptor.getValue().getUser()).isSameAs(persistedUser);
        InOrder order = inOrder(userRepository, tenantRepository, membershipRepository);
        order.verify(userRepository).save(org.mockito.ArgumentMatchers.any(User.class));
        order.verify(tenantRepository).save(org.mockito.ArgumentMatchers.any(Tenant.class));
        order.verify(membershipRepository).save(org.mockito.ArgumentMatchers.any(TenantMembership.class));
    }
}
