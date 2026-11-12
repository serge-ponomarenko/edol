package org.spon.edolnotify.telegram;

import io.github.natanimn.telebof.BotContext;
import io.github.natanimn.telebof.log.BotLog;
import io.github.natanimn.telebof.requests.AbstractBaseRequest;
import io.github.natanimn.telebof.requests.Api;
import io.github.natanimn.telebof.states.StateMemoryStorage;
import jakarta.annotation.PreDestroy;
import org.spon.edolnotify.service.HubTelegramSettingsClient;
import org.spon.edolnotify.service.NotifyTenantContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.function.BooleanSupplier;

/** Owns secure-mode polling without Telebof's process-wide System.exit lifecycle. */
@Component
@Slf4j
@ConditionalOnProperty(name = "edol-notify.telegram.settings-source", havingValue = "hub")
public class TenantTelegramBotRegistry {
    private final HubTelegramSettingsClient settings;
    private final NotifyTenantContext tenants;
    private final CommandHandler commands;
    private final CallbackHandler callbacks;
    private final ScheduledExecutorService polling;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private volatile boolean closed;
    @Value("${telegram.enable:true}")
    private boolean enabled = true;

    @Autowired
    public TenantTelegramBotRegistry(HubTelegramSettingsClient settings, NotifyTenantContext tenants,
            @Lazy CommandHandler commands, @Lazy CallbackHandler callbacks) {
        this(settings, tenants, commands, callbacks, Executors.newScheduledThreadPool(4,
                Thread.ofPlatform().daemon().name("tenant-telegram-", 0).factory()));
    }

    TenantTelegramBotRegistry(HubTelegramSettingsClient settings, NotifyTenantContext tenants,
            CommandHandler commands, CallbackHandler callbacks, ScheduledExecutorService polling) {
        this.settings = settings;
        this.tenants = tenants;
        this.commands = commands;
        this.callbacks = callbacks;
        this.polling = polling;
        // Telebof CONFIG logging includes token-bearing URLs and raw Telegram responses.
        BotLog.setLogLevel(Level.OFF);
    }

    @Scheduled(fixedDelayString = "${edol-notify.telegram.refresh-ms:15000}")
    public synchronized void refresh() {
        if (closed) {
            return;
        }
        if (!enabled) {
            sessions.keySet().stream().toList().forEach(this::remove);
            return;
        }
        try {
            List<UUID> configured = settings.tenants();
            var retained = new HashSet<>(configured);
            sessions.keySet().stream().filter(id -> !retained.contains(id)).toList().forEach(this::remove);
            for (UUID tenantId : configured) {
                refreshTenant(tenantId);
            }
        } catch (RuntimeException exception) {
            sessions.keySet().stream().toList().forEach(this::remove);
            log.warn("Hub Telegram directory unavailable; tenant bots suspended");
        }
    }

    private void refreshTenant(UUID tenantId) {
        try {
            HubTelegramSettingsClient.Settings current = settings.settings(tenantId);
            Session previous = sessions.get(tenantId);
            if (!current.enabled()) {
                remove(tenantId);
            } else if (previous == null || !previous.configuration.equals(current)) {
                remove(tenantId);
                Session session = new Session(tenantId, current);
                sessions.put(tenantId, session);
                session.task = polling.scheduleWithFixedDelay(session::poll, 0, 2, TimeUnit.SECONDS);
            }
        } catch (RuntimeException exception) {
            remove(tenantId);
            log.warn("Hub Telegram configuration unavailable for tenant {}; bot suspended", tenantId);
        }
    }

    public boolean configured(UUID tenantId) {
        return sessions.containsKey(tenantId);
    }

    public BotContext context() {
        Session session = sessions.get(tenants.currentTenantId());
        return session == null ? null : session.context;
    }

    public List<Long> recipients() {
        Session session = sessions.get(tenants.currentTenantId());
        return session == null ? List.of() : List.of(session.configuration.chatId());
    }

    public void requireChat(BotContext context, long chatId) {
        Session session = sessions.get(tenants.currentTenantId());
        if (session == null || session.context != context || session.configuration.chatId() != chatId) {
            throw new IllegalArgumentException("Telegram chat is not authorized for this bot");
        }
    }

    private void remove(UUID tenantId) {
        Session session = sessions.remove(tenantId);
        if (session != null) {
            session.active = false;
            if (session.task != null) {
                session.task.cancel(true);
            }
        }
    }

    BotContext createContext(String token, BooleanSupplier active) {
        return new BotContext(new GuardedApi(token, active), new StateMemoryStorage());
    }

    @PreDestroy
    public synchronized void close() {
        closed = true;
        sessions.keySet().stream().toList().forEach(this::remove);
        polling.shutdownNow();
    }

    private final class Session {
        private final UUID tenantId;
        private final HubTelegramSettingsClient.Settings configuration;
        private final BotContext context;
        private volatile boolean active = true;
        private ScheduledFuture<?> task;
        private Integer offset;

        private Session(UUID tenantId, HubTelegramSettingsClient.Settings configuration) {
            this.tenantId = tenantId;
            this.configuration = configuration;
            context = createContext(configuration.botToken(), () -> active);
        }

        private void poll() {
            if (!active) {
                return;
            }
            try (NotifyTenantContext.Scope ignored = tenants.open(tenantId)) {
                if (offset == null) {
                    // Discard historical commands when enabling/replacing a bot, without sending a message.
                    var latest = context.getUpdates().timeout(0).limit(1).offset(-1).exec();
                    offset = latest.isEmpty() ? 0 : latest.getLast().getUpdateId() + 1;
                    return;
                }
                var updates = context.getUpdates().timeout(0).limit(20).offset(offset).exec();
                for (var update : updates) {
                    offset = update.getUpdateId() + 1;
                    if (!active) {
                        return;
                    }
                    if (update.getMessage() != null) {
                        commands.handleMessage(context, update.getMessage());
                    } else if (update.getCallbackQuery() != null) {
                        callbacks.handleCallback(context, update.getCallbackQuery());
                    }
                }
            } catch (RuntimeException exception) {
                // Never log exceptions, URLs, response bodies or credentials from Telegram.
                log.warn("Telegram polling or command rejected for tenant {}", tenantId);
            }
        }
    }

    private static final class GuardedApi extends Api {
        private final BooleanSupplier active;

        private GuardedApi(String token, BooleanSupplier active) {
            super(token, false, null, null);
            this.active = active;
        }

        @Override
        public synchronized <T, R> R makeRequest(AbstractBaseRequest<T, R> request) {
            if (!active.getAsBoolean()) {
                throw new IllegalStateException("Telegram bot is disabled");
            }
            try {
                // Api has a mutable Request.Builder; serialize requests for each bot.
                return super.makeRequest(request);
            } catch (RuntimeException exception) {
                throw new IllegalStateException("Telegram request failed");
            }
        }
    }
}
