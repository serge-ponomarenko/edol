package org.spon.edolnotify.telegram;

import io.github.natanimn.telebof.BotContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edol.deployment.DeploymentMode;
import org.spon.edol.model.PrinterState;
import org.spon.edolnotify.config.NotifyRecipientProperties;
import org.spon.edolnotify.model.PrinterSummary;
import org.spon.edolnotify.service.NotifyRecipientResolver;
import org.spon.edolnotify.service.NotifyTenantContext;
import org.spon.edolnotify.service.PrinterService;
import org.spon.edolnotify.service.TelegramMessageFormatterService;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramMessageControllerTest {

    private static final UUID PRINTING_PRINTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID READY_PRINTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID DISABLED_PRINTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000103");

    @Mock
    private PrinterService printerService;

    @Mock
    private TelegramBotService telegramBotService;

    @Mock
    private TelegramMessageFormatterService formatter;

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

    @Test
    void sendsAvailabilityOnlyToTheActiveTenantRecipients() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        NotifyTenantContext tenantContext = new NotifyTenantContext();
        NotifyRecipientResolver recipientResolver = new NotifyRecipientResolver(
                DeploymentMode.SECURE_MULTI_TENANT,
                new NotifyRecipientProperties(List.of(
                        new NotifyRecipientProperties.TenantRecipient(tenantA, List.of(101L, 102L)),
                        new NotifyRecipientProperties.TenantRecipient(tenantB, List.of(201L))
                )),
                tenantContext,
                0
        );
        ReflectionTestUtils.invokeMethod(recipientResolver, "validateMappings");
        BotContext context = mock(BotContext.class, RETURNS_DEEP_STUBS);
        when(telegramBotService.getContext()).thenReturn(context);
        when(printerService.getPrinters()).thenReturn(List.of(printer(PRINTING_PRINTER_ID, "Printer A", true)));
        TelegramMessageController controller = new TelegramMessageController(
                formatter,
                printerService,
                telegramBotService,
                recipientResolver
        );

        try (NotifyTenantContext.Scope ignored = recipientResolver.openForEvent(tenantA)) {
            controller.sendPrinterOnlineMessage(PRINTING_PRINTER_ID);
        }

        verify(context).sendMessage(eq(101L), anyString());
        verify(context).sendMessage(eq(102L), anyString());
        verify(context, never()).sendMessage(eq(201L), anyString());
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
