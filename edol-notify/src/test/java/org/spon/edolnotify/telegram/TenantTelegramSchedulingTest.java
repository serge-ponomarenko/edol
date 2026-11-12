package org.spon.edolnotify.telegram;

import io.github.natanimn.telebof.BotContext;
import org.junit.jupiter.api.Test;
import org.spon.edolnotify.config.NotifySchedulingConfiguration;
import org.spon.edolnotify.service.HubTelegramSettingsClient;
import org.spon.edolnotify.service.NotifyTenantContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockPropertySource;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class TenantTelegramSchedulingTest {
    @Test
    void loadsConfiguredBotAutomaticallyWithoutManualRefreshOrTelegramTraffic() throws InterruptedException {
        UUID tenantId = UUID.randomUUID();
        HubTelegramSettingsClient hub = mock(HubTelegramSettingsClient.class);
        when(hub.tenants()).thenReturn(List.of(tenantId));
        when(hub.settings(tenantId)).thenReturn(new HubTelegramSettingsClient.Settings(true, 17L, "test-only-token"));
        NotifyTenantContext tenants = new NotifyTenantContext();
        BotContext bot = mock(BotContext.class);
        var polling = mock(ScheduledExecutorService.class);
        var registry = spy(new TenantTelegramBotRegistry(hub, tenants,
                mock(CommandHandler.class), mock(CallbackHandler.class), polling));
        CountDownLatch configured = new CountDownLatch(1);
        doAnswer(invocation -> {
            configured.countDown();
            return bot;
        }).when(registry).createContext(anyString(), any());

        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MockPropertySource()
                    .withProperty("edol-notify.telegram.settings-source", "hub")
                    .withProperty("edol-notify.telegram.refresh-ms", "20"));
            context.register(NotifySchedulingConfiguration.class);
            context.registerBean(TenantTelegramBotRegistry.class, () -> registry);
            context.refresh();

            assertThat(configured.await(2, TimeUnit.SECONDS)).isTrue();
            // Acquire the same monitor after context creation to observe the completed refresh.
            synchronized (registry) {
                assertThat(registry.configured(tenantId)).isTrue();
                try (var ignored = tenants.open(tenantId)) {
                    assertThat(registry.context()).isSameAs(bot);
                    assertThat(registry.recipients()).containsExactly(17L);
                }
            }
        }
    }
}
