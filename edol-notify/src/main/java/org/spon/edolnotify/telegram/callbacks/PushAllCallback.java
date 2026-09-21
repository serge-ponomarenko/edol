package org.spon.edolnotify.telegram.callbacks;

import io.github.natanimn.telebof.BotContext;
import io.github.natanimn.telebof.types.updates.CallbackQuery;
import lombok.RequiredArgsConstructor;
import org.spon.edolnotify.service.PrinterService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PushAllCallback implements Callback {

    private final PrinterService printerService;

    @Override
    public String action() {
        return "pushall";
    }

    @Override
    public void handleCallback(BotContext context, CallbackQuery callback) {
        context.answerCallbackQuery(callback.getId()).exec();
        printerService.sendPushAllCommand(getPrinterId(callback));
    }
}
