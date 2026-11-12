package org.spon.edolnotify.telegram;


import io.github.natanimn.telebof.BotContext;
import io.github.natanimn.telebof.types.updates.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolnotify.telegram.commands.Command;
import org.spon.edolnotify.service.NotifyRecipientResolver;
import org.spon.edolnotify.service.NotifyTenantContext;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class CommandHandler implements MessageHandler {

    private final Map<String, Command> commandMap;
    private final NotifyRecipientResolver recipientResolver;

    @Override
    public void handleMessage(BotContext ctx, Message message) {
        String text = message.getText();
        if (text == null || !text.startsWith("/")) {
            return;
        }
        String commandText = text.split("\\s+")[0].substring(1).split("@", 2)[0];
        Command command = commandMap.get(commandText);
        if (command == null) {
            return;
        }
        log.info("{} command was received from user_id {}. Mapped Command: {}",
                commandText, message.getChat().getId(), command);
        try (NotifyTenantContext.Scope ignored = recipientResolver.openForChat(ctx, message.getChat().getId())) {
            command.runCommand(ctx, message);
        }
    }


}
