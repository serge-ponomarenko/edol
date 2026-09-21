package org.spon.edolnotify.telegram;

import io.github.natanimn.telebof.BotContext;
import io.github.natanimn.telebof.enums.ParseMode;
import io.github.natanimn.telebof.types.keyboard.InlineKeyboardButton;
import io.github.natanimn.telebof.types.keyboard.InlineKeyboardMarkup;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.PrinterState;
import org.spon.edolnotify.model.PrinterSummary;
import org.spon.edolnotify.service.PrinterService;
import org.spon.edolnotify.service.TelegramMessageFormatterService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class TelegramMessageController {

    private final TelegramMessageFormatterService formatter;
    private final PrinterService printerService;
    private final TelegramBotService telegramBotService;

    public TelegramMessageController(TelegramMessageFormatterService formatter,
                                     PrinterService printerService,
                                     @Lazy TelegramBotService telegramBotService) {
        this.formatter = formatter;
        this.printerService = printerService;
        this.telegramBotService = telegramBotService;
    }

    @Value("${telegram.admin-id}")
    private Long adminChatId;

    public void sendPrinterSelection(BotContext context, long chatId, String action, String title) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        printerService.getPrinters().stream()
                .filter(PrinterSummary::enabled)
                .forEach(printer -> keyboard.addKeyboard(new InlineKeyboardButton(
                        printer.name(), action + "_" + printer.printerId()
                )));
        context.sendMessage(chatId, title)
                .parseMode(ParseMode.HTML)
                .replyMarkup(keyboard)
                .exec();
    }

    public void sendStatusMessage(UUID printerId) {
        PrinterState state = printerService.getState(printerId);
        if (state == null || !state.isOnline()) {
            sendPrinterOfflineMessage(printerId);
            return;
        }
        BotContext context = getBotContext();
        if (context != null) {
            sendStatusMessage(context, adminChatId, printerId);
        }
    }

    public void sendStatusMessage(BotContext context, long chatId, UUID printerId) {
        PrinterState state = printerService.getState(printerId);
        PrinterSummary printer = getPrinter(printerId);
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        keyboard.addKeyboard(
                new InlineKeyboardButton("📃 Status", callback("status", printerId)),
                new InlineKeyboardButton("⚙️ Controls", callback("controls", printerId))
        );
        keyboard.addKeyboard(new InlineKeyboardButton("🖨 Printers", "printers"));

        Path latestStatusImagePath = printerService.getLatestStatusImagePath(printerId);
        String caption = formatter.buildStatusMessage(state, printer.name());
        if (latestStatusImagePath != null) {
            context.sendPhoto(chatId, latestStatusImagePath.toFile())
                    .caption(caption)
                    .parseMode(ParseMode.HTML)
                    .replyMarkup(keyboard)
                    .exec();
            return;
        }
        context.sendMessage(chatId, caption)
                .parseMode(ParseMode.HTML)
                .replyMarkup(keyboard)
                .exec();
    }

    public void sendStatusPrinterSelection(BotContext context, long chatId) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        statusSelections(printerService.getPrinters(), printerService.getStates())
                .forEach(selection -> keyboard.addKeyboard(new InlineKeyboardButton(
                        statusSelectionLabel(selection), callback("status", selection.printer().printerId())
                )));
        context.sendMessage(chatId, "Select a printer")
                .parseMode(ParseMode.HTML)
                .replyMarkup(keyboard)
                .exec();
    }

    public void sendPrinterStartedMessage(UUID printerId) {
        sendStatusMessage(printerId);
    }

    public void sendVideo(UUID printerId, File video) {
        BotContext context = getBotContext();
        if (context != null) {
            context.sendVideo(adminChatId, video)
                    .caption("🖨 <b>" + getPrinter(printerId).name() + ": "
                            + printerService.getState(printerId).getCurrentTask() + "</b>")
                    .parseMode(ParseMode.HTML)
                    .exec();
        }
    }

    public void sendControlsMessage(BotContext context, long chatId, UUID printerId) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        keyboard.addKeyboard(
                new InlineKeyboardButton("⏸️ Pause", callback("pause", printerId)),
                new InlineKeyboardButton("▶️ Resume", callback("resume", printerId)),
                new InlineKeyboardButton("🛑 Stop", callback("stpconfirm", printerId))
        );
        keyboard.addKeyboard(new InlineKeyboardButton("📃 Status", callback("status", printerId)));
        context.sendMessage(chatId, "⚙️ <b>Controls. Be careful.</b>")
                .parseMode(ParseMode.HTML)
                .replyMarkup(keyboard)
                .exec();
    }

    public void sendStopConfirmMessage(BotContext context, long chatId, UUID printerId) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        keyboard.addKeyboard(
                new InlineKeyboardButton("📃 Status", callback("status", printerId)),
                new InlineKeyboardButton("🛑 Stop", callback("stop", printerId))
        );
        context.sendMessage(chatId, "👋 <b>Are you sure you want to stop this printer?</b>")
                .parseMode(ParseMode.HTML)
                .replyMarkup(keyboard)
                .exec();
    }

    public void sendPrinterOnlineMessage(UUID printerId) {
        sendAvailabilityMessage(printerId, "🟢 Printer ONLINE!");
    }

    public void sendPrinterOfflineMessage(UUID printerId) {
        sendAvailabilityMessage(printerId, "🔴 Printer OFFLINE!");
    }

    private void sendAvailabilityMessage(UUID printerId, String message) {
        BotContext context = getBotContext();
        if (context == null) {
            return;
        }
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        keyboard.addKeyboard(new InlineKeyboardButton("📃 Status", callback("status", printerId)));
        context.sendMessage(adminChatId, "<b>" + getPrinter(printerId).name() + "</b>\n" + message)
                .parseMode(ParseMode.HTML)
                .replyMarkup(keyboard)
                .exec();
    }

    private PrinterSummary getPrinter(UUID printerId) {
        return printerService.getPrinters().stream()
                .filter(printer -> printer.printerId().equals(printerId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Printer is unavailable: " + printerId));
    }

    private String callback(String action, UUID printerId) {
        return action + "_" + printerId;
    }

    private BotContext getBotContext() {
        if (telegramBotService != null && telegramBotService.getBot() != null) {
            return telegramBotService.getBot().context;
        }
        log.error("Telegram bot is unavailable or disabled");
        return null;
    }

    static List<PrinterSelection> statusSelections(List<PrinterSummary> printers, List<PrinterState> states) {
        Map<UUID, PrinterState> statesByPrinterId = states.stream()
                .filter(state -> state.getPrinterId() != null)
                .collect(Collectors.toMap(PrinterState::getPrinterId, Function.identity()));
        return printers.stream()
                .filter(PrinterSummary::enabled)
                .map(printer -> new PrinterSelection(printer, statesByPrinterId.get(printer.printerId())))
                .toList();
    }

    static String statusSelectionLabel(PrinterSelection selection) {
        PrinterState state = selection.state();
        if (state == null || !state.isOnline()) {
            return "🔴 " + selection.printer().name() + " — Offline";
        }
        if (state.isPrinting()) {
            return "🟢 " + selection.printer().name() + " — Printing";
        }
        return "🟢 " + selection.printer().name() + " — Ready";
    }

    record PrinterSelection(PrinterSummary printer, PrinterState state) {
    }
}
