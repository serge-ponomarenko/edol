package org.spon.edolnotify.service;

import lombok.RequiredArgsConstructor;
import org.spon.edolnotify.telegram.TelegramMessageController;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TelegramMessageService implements MessageService {

    private final TelegramMessageController telegramMessageController;

    @Override
    public void sendStatusMessage(UUID printerId) {
        telegramMessageController.sendStatusMessage(printerId);
    }

    @Override
    public void sendPrinterOnlineMessage(UUID printerId) {
        telegramMessageController.sendPrinterOnlineMessage(printerId);
    }

    @Override
    public void sendPrinterOfflineMessage(UUID printerId) {
        telegramMessageController.sendPrinterOfflineMessage(printerId);
    }

    @Override
    public void sendPrintStartedMessage(UUID printerId) {
        telegramMessageController.sendPrinterStartedMessage(printerId);
    }

    @Override
    public void sendTimelapseVideoMessage(UUID printerId, Path videoPath) {
        telegramMessageController.sendVideo(printerId, videoPath.toFile());
    }
}
