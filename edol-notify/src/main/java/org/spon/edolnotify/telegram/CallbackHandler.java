package org.spon.edolnotify.telegram;

import io.github.natanimn.telebof.BotContext;
import io.github.natanimn.telebof.types.updates.CallbackQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolnotify.telegram.callbacks.Callback;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class CallbackHandler {

    private final Map<String, Callback> callbackMap;

    public void handleCallback(BotContext context, CallbackQuery callbackQuery) {
        String callbackName = callbackQuery.getData().split("_")[0];
        Callback callback = callbackMap.get(callbackName);
        log.info("{} callback has been received from user_id {}. Mapped Callback: {}",
                callbackName, callbackQuery.getMessage().getChat().getId(), callback);
        callback.handleCallback(context, callbackQuery);
    }

}
