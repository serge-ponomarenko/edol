package org.spon.edolnotify.telegram.commands;

import io.github.natanimn.telebof.BotContext;
import io.github.natanimn.telebof.types.updates.Message;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public interface Command {

    void runCommand(BotContext ctx, Message message);

    @NotNull
    default List<String> getArguments(Message message) {
        return Arrays.stream(message.getText().split(" ")).skip(1L).toList();
    }

}
