package org.spon.edolnotify.telegram;

import io.github.natanimn.telebof.BotContext;
import io.github.natanimn.telebof.enums.ParseMode;
import io.github.natanimn.telebof.types.keyboard.InlineKeyboardButton;
import io.github.natanimn.telebof.types.keyboard.InlineKeyboardMarkup;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolnotify.service.PrinterService;
import org.spon.edolnotify.service.TelegramMessageFormatterService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;

@Component
@Slf4j
public class TelegramMessageController {

    private final TelegramMessageFormatterService telegramMessageFormatterService;
    private final PrinterService printerService;
    private final TelegramBotService telegramBotService;

    public TelegramMessageController(TelegramMessageFormatterService telegramMessageFormatterService,
                                     PrinterService printerService,
                                     @Lazy TelegramBotService telegramBotService) {
        this.telegramMessageFormatterService = telegramMessageFormatterService;
        this.printerService = printerService;
        this.telegramBotService = telegramBotService;
    }

    @Value("${telegram.admin-id}")
    private Long adminChatId;

    private BotContext getBotContext() {
        if (telegramBotService != null && telegramBotService.getBot() != null) {
            return telegramBotService.getBot().context;
        } else {
            log.error("Telegram bot is unavailable or disabled!");
            return null;
        }
    }

    public void sendStatusMessage() {
        if (!printerService.getState().isOnline()) {
            sendPrinterOfflineMessage();
        } else {
            if (getBotContext() != null) {
                sendStatusMessage(getBotContext(), adminChatId);
            }
        }
    }

    public void sendStatusMessage(BotContext context, long chatId) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        keyboard.addKeyboard(
                new InlineKeyboardButton("\uD83D\uDCC3 Status", "status"),
                new InlineKeyboardButton("⚙️ Controls", "controls")
        );

        Path latestStatusImagePath = printerService.getLatestStatusImagePath();
        if (latestStatusImagePath != null) {
            File latestStatusImage = latestStatusImagePath.toFile();

            context
                    .sendPhoto(chatId, latestStatusImage)
                    .caption(telegramMessageFormatterService.buildStatusMessage())
                    .parseMode(ParseMode.HTML)
                    .replyMarkup(keyboard)
                    .exec();
        } else {
            context
                    .sendMessage(chatId, telegramMessageFormatterService.buildStatusMessage())
                    .parseMode(ParseMode.HTML)
                    .replyMarkup(keyboard)
                    .exec();
        }

    }

    public void sendPrinterStartedMessage() {
        sendStatusMessage();
    }

    public void sendVideo(File video) {
        if (getBotContext() != null) {
            getBotContext()
                    .sendVideo(adminChatId, video)
                    .caption("🖨 <b>" + printerService.getState().getCurrentTask() + "</b>")
                    .parseMode(ParseMode.HTML)
                    .exec();
        }
    }

    public void sendControlsMessage(BotContext context, long chatId) {
        String userMessage = "⚙️ <b>Controls. Be careful.</b>";

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        keyboard.addKeyboard(
                new InlineKeyboardButton("⏸️ Pause", "pause"),
                new InlineKeyboardButton("▶️ Resume", "resume"),
                new InlineKeyboardButton("\uD83D\uDED1 Stop", "stpconfirm")
        );
        keyboard.addKeyboard(
                new InlineKeyboardButton("\uD83D\uDCC3 Status", "status")
        );

        context.sendMessage(chatId, userMessage)
                .parseMode(ParseMode.HTML)
                .replyMarkup(keyboard)
                .exec();
    }


    public void sendStopConfirmMessage(BotContext context, long chatId) {
        String userMessage = "\uD83D\uDC4B <b>⚙️ Are you sure to want to stop?</b>";

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        keyboard.addKeyboard(
                new InlineKeyboardButton("\uD83D\uDCC3 Status", "status"),
                new InlineKeyboardButton("\uD83D\uDED1 Stop", "stop")
        );

        context.sendMessage(chatId, userMessage)
                .parseMode(ParseMode.HTML)
                .replyMarkup(keyboard)
                .exec();
    }

    public void sendPrinterOnlineMessage() {
        if (getBotContext() == null)
            return;

        String userMessage = "\uD83D\uDFE2 Printer ONLINE!";

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        keyboard.addKeyboard(
                new InlineKeyboardButton("\uD83D\uDCC3 Status", "status")
        );

        getBotContext().sendMessage(adminChatId, userMessage)
                .parseMode(ParseMode.HTML)
                .replyMarkup(keyboard)
                .exec();
    }

    public void sendPrinterOfflineMessage() {
        if (getBotContext() == null)
            return;

        String userMessage = "\uD83D\uDD34 Printer OFFLINE!";

        getBotContext().sendMessage(adminChatId, userMessage)
                .parseMode(ParseMode.HTML)
                .exec();
    }

}
