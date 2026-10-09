package org.spon.edolnotify.telegram.commands;

import io.github.natanimn.telebof.BotContext;
import io.github.natanimn.telebof.types.updates.Message;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.spon.edolnotify.telegram.TelegramMessageController;
import org.springframework.stereotype.Component;

@Component("metadata")
@RequiredArgsConstructor
public class MetadataCommand implements Command {

    private final TelegramMessageController telegramMessageController;

    @Override
    @SneakyThrows
    public void runCommand(BotContext ctx, Message message) {
        long chatId = message.getChat().getId();
        telegramMessageController.sendPrinterSelection(ctx, chatId, "metadata", "Select a printer for metadata download");
    }

}
