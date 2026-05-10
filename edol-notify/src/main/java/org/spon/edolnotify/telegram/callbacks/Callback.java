package org.spon.edolnotify.telegram.callbacks;

import io.github.natanimn.telebof.BotContext;
import io.github.natanimn.telebof.types.updates.CallbackQuery;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public interface Callback {

    void handleCallback(BotContext context, CallbackQuery callback);

    @NotNull
    default List<String> getArguments(CallbackQuery callback) {
        return Arrays.stream(callback.getData().split("_")).skip(1L).toList();
    }

}
