package org.spon.edolhub.model.dto;

import org.junit.jupiter.api.Test;
import org.spon.edolhub.model.entity.Printer;
import org.spon.edolhub.model.entity.PrinterStats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class PrinterStatsDtoTest {

    @Test
    void doesNotDereferenceTheLazyPrinterAssociation() {
        Printer printer = mock(Printer.class);
        PrinterStats stats = new PrinterStats();
        stats.setPrinter(printer);
        stats.setTotalPrintSeconds(3600L);

        PrinterStatsDto dto = PrinterStatsDto.from(stats);

        assertThat(dto.totalPrintSeconds()).isEqualTo(3600L);
        verifyNoInteractions(printer);
    }
}
