package org.spon.edolnotify.telegram;


import io.github.natanimn.telebof.BotClient;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.event.EventListener;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.lang.annotation.Annotation;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class TelegramBotService {

    @Value("${telegram.bot-token}")
    private String botToken;

    @Value("${telegram.enable}")
    private boolean telegramServiceEnable;

    @Getter
    private BotClient bot;

    private final CommandHandler commandHandler;
    private final CallbackHandler callbackHandler;

    @EventListener(ApplicationReadyEvent.class)
    public void onAppReady() {
        if (!telegramServiceEnable) {
            return;
        }

        bot = new BotClient(botToken);
        registerHandlers();

        // Start polling in separate thread (Telebof does that internally)
        try {
            bot.startPolling();
        } catch (Exception e) {
            log.error("Telegram bot unavailable, will retry later", e);
        }
    }

    @Scheduled(fixedDelay = 60000)
    public void ensureBotRunning() {
        if (!telegramServiceEnable) {
            return;
        }

        if (bot != null) return;

        try {
            bot = new BotClient(botToken);
            registerHandlers();
            bot.startPolling();
            log.info("Telegram bot started");
        } catch (Exception e) {
            log.warn("Telegram still unavailable");
            bot = null;
        }
    }

    private void registerHandlers() {
        String[] commandNames = findAllComponentNames("commands");
        log.info("Found commands: {}", String.join(", ", commandNames));

        bot.onMessage(
                filter -> filter.commands(
                        commandNames
                ),
                commandHandler::handleMessage
        );

//        bot.onMessage(
//                Filter::text, (ctx, message) ->
//                        log.info("{} message was received from user_id {}, {}",
//                                message.getText(), message.getChat().getId(), message.getMessageThreadId())
//        );

        String[] callbackNames = findAllComponentNames("callbacks");
        String callbackNamesRegex = regexifyNames(callbackNames, "^", "");
        log.info("Found callbacks: {}", String.join(", ", callbackNames));

        bot.onCallback(filter -> filter.regex(
                        callbackNamesRegex
                ),
                callbackHandler::handleCallback
        );
    }

    public String[] findAllComponentNames(String packageName) {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        Class<Component> annotationType = Component.class;
        String fullPackageName = this.getClass().getPackageName() + "." + packageName;
        scanner.addIncludeFilter(new AnnotationTypeFilter(annotationType));

        Set<BeanDefinition> candidateComponents = scanner.findCandidateComponents(fullPackageName);
        String[] values = new String[candidateComponents.size()];
        int i = 0;
        for (var beanDef : candidateComponents) {
            try {
                Class<?> clazz = Class.forName(beanDef.getBeanClassName());
                Annotation annotation = clazz.getAnnotation(annotationType);
                Object value = annotationType.getMethod("value").invoke(annotation);
                values[i++] = String.valueOf(value);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return values;
    }

    private String regexifyNames(String[] callbacks, String prefix, String suffix) {
        String[] result = new String[callbacks.length];
        for (int i = 0; i < callbacks.length; i++) {
            result[i] = prefix + callbacks[i] + suffix;
        }
        return String.join("|", result);
    }

}
