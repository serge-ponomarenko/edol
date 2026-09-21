package org.spon.edolnotify.telegram.callbacks;

import io.github.natanimn.telebof.BotContext;
import io.github.natanimn.telebof.types.updates.CallbackQuery;
import org.junit.jupiter.api.Test;
import org.spon.edolnotify.telegram.TelegramMessageController;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrinterSelectionCallbackTest {

    @Test
    void opensTheStatusPrinterSelectorInTheCallbackChat() {
        TelegramMessageController controller = mock(TelegramMessageController.class);
        PrinterSelectionCallback callback = new PrinterSelectionCallback(controller);
        BotContext context = mock(BotContext.class, RETURNS_DEEP_STUBS);
        CallbackQuery query = mock(CallbackQuery.class, RETURNS_DEEP_STUBS);
        when(query.getId()).thenReturn("callback-id");
        when(query.getMessage().getChat().getId()).thenReturn(123L);
        when(query.getMessage().getMessageId()).thenReturn(456);

        callback.handleCallback(context, query);

        verify(controller).sendStatusPrinterSelection(context, 123L);
    }
}
