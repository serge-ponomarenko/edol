package org.spon.edolnotify.service;

import lombok.RequiredArgsConstructor;
import org.spon.edolnotify.telegram.TelegramMessageController;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class TelegramMessageService implements MessageService {

    private final TelegramMessageController telegramMessageController;

    @Override
    public void sendStatusMessage() {
        telegramMessageController.sendStatusMessage();
    }

    @Override
    public void sendPrinterOnlineMessage() {
        telegramMessageController.sendPrinterOnlineMessage();
    }

    @Override
    public void sendPrinterOfflineMessage() {
        telegramMessageController.sendPrinterOfflineMessage();
    }

    @Override
    public void sendPrintStartedMessage() {
        telegramMessageController.sendPrinterStartedMessage();
    }

    @Override
    public void sendTimelapseVideoMessage(Path videoPath) {
        telegramMessageController.sendVideo(videoPath.toFile());
    }
}
