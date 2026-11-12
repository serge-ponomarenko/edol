package org.spon.edolnotify.telegram;

import io.github.natanimn.telebof.BotContext;
import io.github.natanimn.telebof.types.updates.Message;
import io.github.natanimn.telebof.types.updates.Update;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.spon.edolnotify.service.HubTelegramSettingsClient;
import org.spon.edolnotify.service.NotifyTenantContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TenantTelegramBotRegistryTest {
    private final HubTelegramSettingsClient hub = mock(HubTelegramSettingsClient.class);
    private final NotifyTenantContext tenants = new NotifyTenantContext();
    private final CommandHandler commands = mock(CommandHandler.class);
    private final CallbackHandler callbacks = mock(CallbackHandler.class);
    private final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    private final ScheduledFuture<?> future = mock(ScheduledFuture.class);
    private final TenantTelegramBotRegistry registry = spy(new TenantTelegramBotRegistry(hub, tenants, commands, callbacks, scheduler));
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();

    @AfterEach
    void close() {
        registry.close();
    }

    @Test
    void isolatesBotsEvenWhenTenantsUseTheSameChatAndRejectsAnOldContext() {
        BotContext firstBot = prepare(first, "first-test-token", 11L);
        BotContext secondBot = prepare(second, "second-test-token", 11L);
        when(hub.tenants()).thenReturn(List.of(first, second));
        registry.refresh();
        try (var ignored = tenants.open(first)) {
            assertThat(registry.context()).isSameAs(firstBot);
            assertThat(registry.recipients()).containsExactly(11L);
            registry.requireChat(firstBot, 11L);
            assertThatThrownBy(() -> registry.requireChat(secondBot, 11L)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> registry.requireChat(firstBot, 12L)).isInstanceOf(IllegalArgumentException.class);
        }
        when(hub.settings(first)).thenReturn(new HubTelegramSettingsClient.Settings(true, 11L, "replacement-test-token"));
        BotContext replacement = mock(BotContext.class);
        doReturn(replacement).when(registry).createContext(eq("replacement-test-token"), any());
        registry.refresh();
        try (var ignored = tenants.open(first)) {
            assertThat(registry.context()).isSameAs(replacement);
            assertThatThrownBy(() -> registry.requireChat(firstBot, 11L)).isInstanceOf(IllegalArgumentException.class);
        }
        verify(future).cancel(true);
    }

    @Test
    void suspendsOnlyFailedTenantAndSuspendsAllOnDirectoryFailure() {
        prepare(first, "first-test-token", 11L);
        prepare(second, "second-test-token", 22L);
        when(hub.tenants()).thenReturn(List.of(first, second));
        registry.refresh();
        when(hub.settings(first)).thenThrow(new IllegalStateException("test transport failure"));
        registry.refresh();
        assertThat(registry.configured(first)).isFalse();
        assertThat(registry.configured(second)).isTrue();
        when(hub.tenants()).thenThrow(new IllegalStateException("test directory failure"));
        registry.refresh();
        assertThat(registry.configured(second)).isFalse();
        assertThatThrownBy(tenants::currentTenantId).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void masterDisableAndEmptyDirectoryNeverCreateBotsOrPoll() {
        ReflectionTestUtils.setField(registry, "enabled", false);
        registry.refresh();
        verifyNoInteractions(hub, scheduler);
        ReflectionTestUtils.setField(registry, "enabled", true);
        when(hub.tenants()).thenReturn(List.of());
        registry.refresh();
        verifyNoInteractions(scheduler);
    }

    @Test
    void ownedPollingDiscardsOldCommandsAndRunsNewOnesInsideTheBoundTenant() {
        BotContext context = mock(BotContext.class, RETURNS_DEEP_STUBS);
        when(hub.tenants()).thenReturn(List.of(first));
        when(hub.settings(first)).thenReturn(new HubTelegramSettingsClient.Settings(true, 11L, "test-token"));
        doReturn(context).when(registry).createContext(anyString(), any());
        doReturn(future).when(scheduler).scheduleWithFixedDelay(any(Runnable.class), eq(0L), eq(2L), eq(TimeUnit.SECONDS));
        Update historical = mock(Update.class);
        when(historical.getUpdateId()).thenReturn(5);
        Update current = mock(Update.class);
        Message message = mock(Message.class);
        when(current.getUpdateId()).thenReturn(6);
        when(current.getMessage()).thenReturn(message);
        when(context.getUpdates().timeout(0).limit(1).offset(-1).exec()).thenReturn(List.of(historical));
        when(context.getUpdates().timeout(0).limit(20).offset(6).exec()).thenReturn(List.of(current));
        doAnswer(invocation -> {
            assertThat(tenants.currentTenantId()).isEqualTo(first);
            return null;
        }).when(commands).handleMessage(context, message);
        registry.refresh();
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleWithFixedDelay(task.capture(), eq(0L), eq(2L), eq(TimeUnit.SECONDS));
        task.getValue().run();
        verifyNoInteractions(commands);
        task.getValue().run();
        verify(commands).handleMessage(context, message);
        when(hub.tenants()).thenReturn(List.of());
        registry.refresh();
        task.getValue().run();
        verify(commands, times(1)).handleMessage(context, message);
        assertThatThrownBy(tenants::currentTenantId).isInstanceOf(IllegalStateException.class);
    }

    private BotContext prepare(UUID tenant, String token, long chat) {
        BotContext context = mock(BotContext.class);
        when(hub.settings(tenant)).thenReturn(new HubTelegramSettingsClient.Settings(true, chat, token));
        doReturn(context).when(registry).createContext(eq(token), any());
        doReturn(future).when(scheduler).scheduleWithFixedDelay(any(Runnable.class), eq(0L), eq(2L), eq(TimeUnit.SECONDS));
        return context;
    }
}
