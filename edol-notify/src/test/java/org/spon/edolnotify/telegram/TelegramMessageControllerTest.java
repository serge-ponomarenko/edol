package org.spon.edolnotify.telegram;

import io.github.natanimn.telebof.BotContext;
import io.github.natanimn.telebof.requests.send.SendPhoto;
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
import java.io.File;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class TelegramMessageControllerTest {

    @Test
    void sendsTextWhenSnapshotIsUnavailable() {
        BotContext context = mock(BotContext.class, RETURNS_DEEP_STUBS);
        PrinterState current = state(PRINTING_PRINTER_ID, true, true);
        when(printerService.getState(PRINTING_PRINTER_ID)).thenReturn(current);
        when(printerService.getPrinters()).thenReturn(List.of(printer(PRINTING_PRINTER_ID, "Printer A", true)));
        when(formatter.buildStatusMessage(current, "Printer A")).thenReturn("Printer status");
        TelegramMessageController controller = new TelegramMessageController(
                formatter, printerService, telegramBotService, mock(NotifyRecipientResolver.class));

        controller.sendStatusMessage(context, 101L, PRINTING_PRINTER_ID);

        verify(context).sendMessage(101L, "Printer status");
        verify(context, never()).sendPhoto(eq(101L), any(File.class));
    }

    @Test
    void sendsDownloadedSnapshotAndRemovesTemporaryFile() {
        BotContext context = mock(BotContext.class, RETURNS_DEEP_STUBS);
        PrinterState current = state(PRINTING_PRINTER_ID, true, true);
        byte[] image = {(byte) 0xff, (byte) 0xd8, 1, 2, (byte) 0xff, (byte) 0xd9};
        AtomicReference<File> temporary = new AtomicReference<>();
        SendPhoto photoRequest = mock(SendPhoto.class, RETURNS_DEEP_STUBS);
        doAnswer(invocation -> {
            File file = invocation.getArgument(1);
            temporary.set(file);
            var posix = Files.getFileAttributeView(file.toPath(), PosixFileAttributeView.class);
            if (posix != null) {
                assertThat(posix.readAttributes().permissions())
                        .isEqualTo(PosixFilePermissions.fromString("rw-------"));
            } else {
                var acl = Files.getFileAttributeView(file.toPath(), AclFileAttributeView.class);
                assertThat(acl.getAcl()).hasSize(1);
                assertThat(acl.getAcl().getFirst().type()).isEqualTo(AclEntryType.ALLOW);
                var processUser = FileSystems.getDefault().getUserPrincipalLookupService()
                        .lookupPrincipalByName(System.getProperty("user.name"));
                assertThat(acl.getAcl().getFirst().principal()).isEqualTo(processUser);
            }
            assertThat(Files.readAllBytes(file.toPath())).containsExactly(image);
            return photoRequest;
        }).when(context).sendPhoto(eq(101L), any(File.class));
        when(printerService.getState(PRINTING_PRINTER_ID)).thenReturn(current);
        when(printerService.getPrinters()).thenReturn(List.of(printer(PRINTING_PRINTER_ID, "Printer A", true)));
        when(printerService.getStatusImage(PRINTING_PRINTER_ID)).thenReturn(image);
        when(formatter.buildStatusMessage(current, "Printer A")).thenReturn("Printer status");
        TelegramMessageController controller = new TelegramMessageController(
                formatter, printerService, telegramBotService, mock(NotifyRecipientResolver.class));

        controller.sendStatusMessage(context, 101L, PRINTING_PRINTER_ID);

        verify(context).sendPhoto(101L, temporary.get());
        assertThat(temporary.get()).doesNotExist();
        verify(context, never()).sendMessage(eq(101L), anyString());
    }

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
