package org.spon.edolnotify.service;

import java.nio.file.Path;

public interface MessageService {

    void sendStatusMessage();

    void sendPrinterOnlineMessage();

    void sendPrinterOfflineMessage();

    void sendPrintStartedMessage();

    void sendTimelapseVideoMessage(Path videoPath);
}
