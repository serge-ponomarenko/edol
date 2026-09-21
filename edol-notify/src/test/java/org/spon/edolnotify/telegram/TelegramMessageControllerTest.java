package org.spon.edolnotify.telegram;

import org.junit.jupiter.api.Test;
import org.spon.edol.model.PrinterState;
import org.spon.edolnotify.model.PrinterSummary;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramMessageControllerTest {

    private static final UUID PRINTING_PRINTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID READY_PRINTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID DISABLED_PRINTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000103");

    @Test
    void buildsEnabledPrinterSelectionsWithShortLiveStatuses() {
        PrinterState printing = state(PRINTING_PRINTER_ID, true, true);
        PrinterState ready = state(READY_PRINTER_ID, true, false);

        List<TelegramMessageController.PrinterSelection> selections = TelegramMessageController.statusSelections(
                List.of(
                        printer(PRINTING_PRINTER_ID, "Printing printer", true),
                        printer(READY_PRINTER_ID, "Ready printer", true),
                        printer(DISABLED_PRINTER_ID, "Disabled printer", false)
                ),
                List.of(printing, ready)
        );

        assertThat(selections)
                .extracting(TelegramMessageController::statusSelectionLabel)
                .containsExactly(
                        "🟢 Printing printer — Printing",
                        "🟢 Ready printer — Ready"
                );
    }

    @Test
    void labelsMissingOrOfflineStateAsOffline() {
        PrinterSummary printer = printer(PRINTING_PRINTER_ID, "Offline printer", true);

        assertThat(TelegramMessageController.statusSelectionLabel(
                new TelegramMessageController.PrinterSelection(printer, null)
        )).isEqualTo("🔴 Offline printer — Offline");
        assertThat(TelegramMessageController.statusSelectionLabel(
                new TelegramMessageController.PrinterSelection(printer, state(PRINTING_PRINTER_ID, false, false))
        )).isEqualTo("🔴 Offline printer — Offline");
    }

    private PrinterSummary printer(UUID printerId, String name, boolean enabled) {
        return new PrinterSummary(printerId, name, name, enabled);
    }

    private PrinterState state(UUID printerId, boolean online, boolean printing) {
        PrinterState state = new PrinterState();
        state.setPrinterId(printerId);
        state.setOnline(online);
        state.setPrinting(printing);
        return state;
    }
}
