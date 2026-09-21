package org.spon.edolnotify.telegram.callbacks;

import io.github.natanimn.telebof.BotContext;
import io.github.natanimn.telebof.types.updates.CallbackQuery;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public interface Callback {

    String action();

    void handleCallback(BotContext context, CallbackQuery callback);

    @NotNull
    default List<String> getArguments(CallbackQuery callback) {
        return Arrays.stream(callback.getData().split("_")).skip(1L).toList();
    }

    default UUID getPrinterId(CallbackQuery callback) {
        return UUID.fromString(getArguments(callback).getFirst());
    }

}
