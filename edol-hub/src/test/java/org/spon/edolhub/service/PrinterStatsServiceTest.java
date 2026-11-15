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
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class PrinterStatsServiceTest {

    @Test
    void convenienceReadsEnterReadOnlyTransactionsWithoutCreatingStats() {
        Printer printer = printer(PRINTER_ID);
        when(printerAccessService.getDefaultPrinter()).thenReturn(printer);
        when(printerAccessService.getPrinter(PRINTER_ID)).thenReturn(printer);
        when(repository.findByPrinterId(PRINTER_ID)).thenReturn(Optional.empty());
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any(TransactionDefinition.class))).thenAnswer(invocation -> {
            TransactionDefinition definition = invocation.getArgument(0);
            assertThat(definition.isReadOnly()).isTrue();
            return new SimpleTransactionStatus();
        });
        ProxyFactory factory = new ProxyFactory(service);
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactions);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        factory.addAdvice(interceptor);
        PrinterStatsService proxy = (PrinterStatsService) factory.getProxy();

        assertThat(proxy.getStats().getTotalJobs()).isZero();
        assertThat(proxy.getTotalPrinterHours(PRINTER_ID)).isZero();
        assertThat(proxy.getTotalPrinterHoursDouble(PRINTER_ID)).isZero();
        assertThat(proxy.getFormattedPrinterTime(PRINTER_ID)).isEqualTo("0h 0m");

        verify(transactions, times(4)).getTransaction(any(TransactionDefinition.class));
        verify(repository, never()).save(any(PrinterStats.class));
    }

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
