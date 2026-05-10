package org.spon.edolnotify.telegram.commands;

import io.github.natanimn.telebof.BotContext;
import io.github.natanimn.telebof.enums.ParseMode;
import io.github.natanimn.telebof.types.keyboard.InlineKeyboardButton;
import io.github.natanimn.telebof.types.keyboard.InlineKeyboardMarkup;
import io.github.natanimn.telebof.types.updates.Message;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("start")
@RequiredArgsConstructor
public class StartCommand implements Command {

    @Value("${telegram.admin-id}")
    private Long adminId;

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

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        keyboard.addKeyboard(
                new InlineKeyboardButton("\uD83D\uDCC3 Status", "status")
        );

        ctx.sendMessage(chatId, userMessage)
                .parseMode(ParseMode.HTML)
                .replyMarkup(keyboard)
                .exec();
    }

}
