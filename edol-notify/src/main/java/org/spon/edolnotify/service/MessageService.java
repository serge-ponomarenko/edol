package org.spon.edolnotify.service;

import java.nio.file.Path;
import java.util.UUID;

public interface MessageService {

    void sendStatusMessage(UUID printerId);

    void sendPrinterOnlineMessage(UUID printerId);

    void sendPrinterOfflineMessage(UUID printerId);

    void sendPrintStartedMessage(UUID printerId);

    void sendTimelapseVideoMessage(UUID printerId, Path videoPath);
}
