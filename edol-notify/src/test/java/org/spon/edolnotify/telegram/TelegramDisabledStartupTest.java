package org.spon.edolnotify.telegram;

import org.junit.jupiter.api.Test;
import org.spon.edol.deployment.DeploymentMode;
import org.spon.edolnotify.config.NotifyRecipientProperties;
import org.spon.edolnotify.service.NotifyRecipientResolver;
import org.spon.edolnotify.service.NotifyTenantContext;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockPropertySource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class TelegramDisabledStartupTest {
    @Test
    void disabledTelegramStartsWithProductionYamlAndNoOperationalTelegramConfiguration() throws IOException {
        try (var context = context(false)) {
            context.registerBean(NotifyRecipientResolver.class);
            context.refresh();
            var bot = context.getBean(TelegramBotService.class);
            bot.onAppReady();
            bot.ensureBotRunning();
            assertThat(bot.getBot()).isNull();
        }
    }

    @Test
    void enabledTelegramRejectsAMissingTokenBeforeAnyPolling() throws IOException {
        try (var context = context(true)) {
            assertThatThrownBy(context::refresh).hasRootCauseMessage("Enabled Telegram requires a bot token");
        }
    }

    private AnnotationConfigApplicationContext context(boolean enabled) throws IOException {
        var context = new AnnotationConfigApplicationContext();
        var sources = new YamlPropertySourceLoader().load("production", new ClassPathResource("application.yaml"));
        sources.forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
        context.getEnvironment().getPropertySources().addFirst(new MockPropertySource()
                .withProperty("telegram.enable", enabled).withProperty("TELEGRAM_BOT_TOKEN", ""));
        context.registerBean(DeploymentMode.class, () -> DeploymentMode.SECURE_MULTI_TENANT);
        context.registerBean(NotifyRecipientProperties.class, () -> new NotifyRecipientProperties(List.of()));
        context.registerBean(NotifyTenantContext.class);
        context.registerBean(CommandHandler.class, () -> mock(CommandHandler.class));
        context.registerBean(CallbackHandler.class, () -> mock(CallbackHandler.class));
        context.registerBean(TelegramBotService.class);
        return context;
    }
}
