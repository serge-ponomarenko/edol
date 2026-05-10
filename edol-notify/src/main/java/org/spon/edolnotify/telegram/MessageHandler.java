package org.spon.edolnotify.telegram;

import io.github.natanimn.telebof.BotContext;
import io.github.natanimn.telebof.types.updates.Message;

public interface MessageHandler {

    void handleMessage(BotContext ctx, Message message);

}
