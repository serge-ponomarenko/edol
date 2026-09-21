package org.spon.edolnotify.telegram.callbacks;

import io.github.natanimn.telebof.BotContext;
import io.github.natanimn.telebof.types.updates.CallbackQuery;
import lombok.RequiredArgsConstructor;
import org.spon.edolnotify.telegram.TelegramMessageController;
import org.springframework.stereotype.Component;

@Component("printers")
@RequiredArgsConstructor
public class PrinterSelectionCallback implements Callback {

    private final TelegramMessageController telegramMessageController;

    @Override
    public String action() {
        return "printers";
    }

    @Override
    public void handleCallback(BotContext context, CallbackQuery callback) {
        context.answerCallbackQuery(callback.getId()).exec();
        long chatId = callback.getMessage().getChat().getId();
        context.editMessageReplyMarkup(chatId, callback.getMessage().getMessageId())
                .replyMarkup(null).exec();

        telegramMessageController.sendStatusPrinterSelection(context, chatId);
    }
}
