package org.spon.edolnotify.telegram;

import io.github.natanimn.telebof.BotContext;
import io.github.natanimn.telebof.types.updates.CallbackQuery;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolnotify.telegram.callbacks.Callback;
import org.spon.edolnotify.service.NotifyRecipientResolver;
import org.spon.edolnotify.service.NotifyTenantContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CallbackHandler {

    private final Map<String, Callback> callbacksByAction;
    private final NotifyRecipientResolver recipientResolver;

    public CallbackHandler(List<Callback> callbacks, NotifyRecipientResolver recipientResolver) {
        callbacksByAction = callbacks.stream()
                .collect(Collectors.toUnmodifiableMap(Callback::action, Function.identity()));
        this.recipientResolver = recipientResolver;
    }

    public void handleCallback(BotContext context, CallbackQuery callbackQuery) {
        String callbackName = callbackQuery.getData().split("_")[0];
        Callback callback = callbacksByAction.get(callbackName);
        if (callback == null) {
            throw new IllegalArgumentException("Unsupported callback action: " + callbackName);
        }
        log.info("{} callback has been received from user_id {}. Mapped Callback: {}",
                callbackName, callbackQuery.getMessage().getChat().getId(), callback);
        try (NotifyTenantContext.Scope ignored = recipientResolver.openForChat(callbackQuery.getMessage().getChat().getId())) {
            callback.handleCallback(context, callbackQuery);
        }
    }

}
