package org.spon.edolnotify.telegram;


import io.github.natanimn.telebof.BotContext;
import io.github.natanimn.telebof.types.updates.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolnotify.telegram.commands.Command;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class CommandHandler implements MessageHandler {

    private final Map<String, Command> commandMap;

    @Override
    public void handleMessage(BotContext ctx, Message message) {
        String commandText = message.getText().split(" ")[0].substring(1);
        Command command = commandMap.get(commandText);
        log.info("{} command was received from user_id {}. Mapped Command: {}",
                commandText, message.getChat().getId(), command);
        command.runCommand(ctx, message);
    }


}
