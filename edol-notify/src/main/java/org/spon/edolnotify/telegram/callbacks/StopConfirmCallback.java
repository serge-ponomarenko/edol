package org.spon.edolnotify.telegram.callbacks;

import io.github.natanimn.telebof.BotContext;
import io.github.natanimn.telebof.types.updates.CallbackQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolnotify.telegram.TelegramMessageController;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Slf4j
@Component("stpconfirm")
public class StopConfirmCallback implements Callback {

    private final TelegramMessageController telegramMessageController;

    @Override
    public void handleCallback(BotContext context, CallbackQuery callback) {
        context.answerCallbackQuery(callback.getId()).exec();
        long chatId = callback.getMessage().getChat().getId();
        context.editMessageReplyMarkup(chatId, callback.getMessage().getMessageId())
                .replyMarkup(null).exec();

        telegramMessageController.sendStopConfirmMessage(context, chatId);
    }
}
