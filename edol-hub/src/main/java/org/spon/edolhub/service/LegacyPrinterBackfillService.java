package org.spon.edolhub.service;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.model.entity.Printer;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LegacyPrinterBackfillService {

    private static final String PRINTER_ID_PARAMETER = "printerId";

    private final JdbcClient jdbcClient;

    public boolean hasPendingBackfill() {
        Integer pendingRows = jdbcClient.sql("""
                        select
                            (select count(*) from hub.print_jobs where printer_id_uuid is null and printer_id is not null)
                          + (select count(*) from hub.maintenance_definition where printer_id is null)
                          + (select count(*) from hub.printer_stats where printer_id is null)
                        """)
                .query(Integer.class)
                .single();
        return pendingRows != null && pendingRows > 0;
    }

    @Transactional
    public void backfill(Printer defaultPrinter) {
        List<Integer> legacyPrinterIds = jdbcClient.sql("""
                        select distinct printer_id
                        from hub.print_jobs
                        where printer_id_uuid is null
                          and printer_id is not null
                        order by printer_id
                        """)
                .query(Integer.class)
                .list();

        if (!legacyPrinterIds.isEmpty() && !legacyPrinterIds.equals(List.of(1))) {
            throw new IllegalStateException(
                    "Unsupported legacy printer identifiers: " + legacyPrinterIds
            );
        }

        jdbcClient.sql("""
                        update hub.print_jobs
                        set printer_id_uuid = :printerId
                        where printer_id_uuid is null
                          and printer_id = 1
                        """)
                .param(PRINTER_ID_PARAMETER, defaultPrinter.getId())
                .update();

        jdbcClient.sql("""
                        update hub.maintenance_definition
                        set printer_id = :printerId
                        where printer_id is null
                        """)
                .param(PRINTER_ID_PARAMETER, defaultPrinter.getId())
                .update();

        jdbcClient.sql("""
                        update hub.printer_stats
                        set printer_id = :printerId
                        where printer_id is null
                        """)
                .param(PRINTER_ID_PARAMETER, defaultPrinter.getId())
                .update();
    }
}
