package org.spon.edolhub.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edolhub.model.entity.Printer;
import org.spon.edolhub.model.entity.PrinterStats;
import org.spon.edolhub.repository.PrinterStatsRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrinterStatsServiceTest {

    private static final UUID PRINTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

    @Mock
    private PrinterStatsRepository repository;

    @Mock
    private PrinterAccessService printerAccessService;

    @InjectMocks
    private PrinterStatsService service;

    @Test
    void returnsZeroValueStatsWithoutPersistingWhenTheProjectionHasNoStatsRow() {
        Printer printer = printer(PRINTER_ID);
        when(printerAccessService.getPrinter(PRINTER_ID)).thenReturn(printer);
        when(repository.findByPrinterId(PRINTER_ID)).thenReturn(Optional.empty());

        PrinterStats stats = service.getStats(PRINTER_ID);

        assertThat(stats.getPrinter()).isSameAs(printer);
        assertThat(stats.getTotalPrintSeconds()).isZero();
        assertThat(stats.getTotalJobs()).isZero();
        assertThat(stats.getTotalFilamentUsedGrams()).isZero();
        assertThat(stats.getTotalPrintHours()).isZero();
        assertThat(stats.getUpdatedAt()).isNull();
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any(PrinterStats.class));
    }

    @Test
    void initializesStatsExplicitlyForANewPrinterProjection() {
        Printer printer = printer(PRINTER_ID);
        when(repository.findByPrinterId(PRINTER_ID)).thenReturn(Optional.empty());

        service.initializeStats(printer);

        ArgumentCaptor<PrinterStats> captor = ArgumentCaptor.forClass(PrinterStats.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPrinter()).isSameAs(printer);
        assertThat(captor.getValue().getUpdatedAt()).isNotNull();
    }

    @Test
    void createsStatsOnlyForPrintJobAccounting() {
        Printer printer = printer(PRINTER_ID);
        when(printerAccessService.getPrinter(PRINTER_ID)).thenReturn(printer);
        when(repository.findByPrinterId(PRINTER_ID)).thenReturn(Optional.empty());
        when(repository.save(any(PrinterStats.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.addPrintJob(printer, 120L, 15L);

        ArgumentCaptor<PrinterStats> captor = ArgumentCaptor.forClass(PrinterStats.class);
        verify(repository, times(2)).save(captor.capture());
        PrinterStats stats = captor.getAllValues().getLast();
        assertThat(stats.getTotalPrintSeconds()).isEqualTo(120L);
        assertThat(stats.getTotalJobs()).isEqualTo(1L);
        assertThat(stats.getTotalFilamentUsedGrams()).isEqualTo(15L);
    }

    private Printer printer(UUID printerId) {
        Printer printer = new Printer();
        printer.setId(printerId);
        return printer;
    }
}
