package org.spon.edolnotify.telegram.commands;

import io.github.natanimn.telebof.BotContext;
import io.github.natanimn.telebof.enums.ParseMode;
import io.github.natanimn.telebof.types.updates.Message;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.spon.edolnotify.telegram.TelegramMessageController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("start")
@RequiredArgsConstructor
public class StartCommand implements Command {

    @Value("${telegram.admin-id}")
    private Long adminId;

    private final TelegramMessageController telegramMessageController;

    @Override
    @SneakyThrows
    public void runCommand(BotContext ctx, Message message) {
        long chatId = message.getChat().getId();
        if (chatId != adminId) return;

        String userMessage = "\uD83D\uDC4B <b>Hi!</b>\n\n" +
                "Available commands:\n" +
                "/log 10 - show last 10 log lines\n" +
                "/metadata - download metadata\n" +
                "/pushall - retrieve all info (Caution!!! As a rule of thumb, refrain from executing this command at intervals less than 5 minutes on the P1P, as it may cause lag due to its hardware limitations.)\n";

        ctx.sendMessage(chatId, userMessage)
                .parseMode(ParseMode.HTML)
                .exec();
        telegramMessageController.sendPrinterSelection(ctx, chatId, "status", "Select a printer");
    }

}
