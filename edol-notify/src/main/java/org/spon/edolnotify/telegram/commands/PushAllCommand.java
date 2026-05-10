package org.spon.edolnotify.telegram.commands;

import io.github.natanimn.telebof.BotContext;
import io.github.natanimn.telebof.enums.ParseMode;
import io.github.natanimn.telebof.types.updates.Message;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.spon.edolnotify.service.PrinterService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("pushall")
@RequiredArgsConstructor
public class PushAllCommand implements Command {

    @Value("${telegram.admin-id}")
    private Long adminId;

    private final PrinterService printerService;

    @Override
    @SneakyThrows
    public void runCommand(BotContext ctx, Message message) {
        long chatId = message.getChat().getId();
        if (chatId != adminId) return;

        String userMessage = "Requesting ALL information...";

        ctx.sendMessage(chatId, userMessage)
                .parseMode(ParseMode.HTML)
                .exec();

        printerService.sendPushAllCommand();
    }

}
