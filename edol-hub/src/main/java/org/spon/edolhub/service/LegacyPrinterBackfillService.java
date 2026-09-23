package org.spon.edolhub.service;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.model.dto.CorePrinterDto;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LegacyPrinterBackfillService {

    private static final String PRINTER_ID_PARAMETER = "printerId";
    private static final int LEGACY_SINGLETON_PRINTER_ID = 1;
    private static final String INVALID_MAINTENANCE_OWNERSHIP_QUERY = """
            select count(*)
            from hub.maintenance_definition definition
            left join hub.printers printer on printer.id = definition.printer_id
            where definition.printer_id is null or printer.id is null
            """;
    private static final String INVALID_STATISTICS_OWNERSHIP_QUERY = """
            select count(*)
            from hub.printer_stats statistics
            left join hub.printers printer on printer.id = statistics.printer_id
            where statistics.printer_id is null or printer.id is null
            """;
    private static final String INVALID_JOB_OWNERSHIP_QUERY = """
            select count(*)
            from hub.print_jobs job
            left join hub.printers printer on printer.id = job.printer_id_uuid
            where job.printer_id_uuid is null or printer.id is null
            """;

    private final JdbcClient jdbcClient;

    public void validateCoreCatalog(List<CorePrinterDto> corePrinters) {
        List<UUID> corePrinterIds = corePrinters.stream()
                .map(CorePrinterDto::printerId)
                .toList();

        if (corePrinterIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalStateException("EDOL Core catalog contains a printer without a UUID");
        }
        if (new HashSet<>(corePrinterIds).size() != corePrinterIds.size()) {
            throw new IllegalStateException("EDOL Core catalog contains duplicate printer UUIDs");
        }
    }

    @Transactional
    public void validateAndBackfill(List<CorePrinterDto> corePrinters) {
        validateCoreCatalog(corePrinters);

        Set<UUID> corePrinterIds = corePrinters.stream()
                .map(CorePrinterDto::printerId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<UUID> hubPrinterIds = new HashSet<>(jdbcClient.sql("select id from hub.printers")
                .query(UUID.class)
                .list());

        if (!hubPrinterIds.equals(corePrinterIds)) {
            throw new IllegalStateException("Hub printer projection does not match the EDOL Core UUID catalog");
        }

        assertNoMissingOrOrphanedOwnership(
                "maintenance definitions",
                INVALID_MAINTENANCE_OWNERSHIP_QUERY
        );
        assertNoMissingOrOrphanedOwnership(
                "printer statistics",
                INVALID_STATISTICS_OWNERSHIP_QUERY
        );
        assertNoDuplicatePrinterStatistics();

        List<Integer> legacyPrinterIds = jdbcClient.sql("""
                        select distinct printer_id
                        from hub.print_jobs
                        where printer_id_uuid is null
                          and printer_id is not null
                        order by printer_id
                        """)
                .query(Integer.class)
                .list();

        if (count("select count(*) from hub.print_jobs where printer_id_uuid is null and printer_id is null") > 0) {
            throw new IllegalStateException("Legacy print jobs are missing both UUID and legacy printer ownership");
        }

        if (!legacyPrinterIds.isEmpty() && !legacyPrinterIds.equals(List.of(LEGACY_SINGLETON_PRINTER_ID))) {
            throw new IllegalStateException(
                    "Unsupported legacy printer identifiers: " + legacyPrinterIds
            );
        }

        if (!legacyPrinterIds.isEmpty()) {
            if (corePrinterIds.size() != 1) {
                throw new IllegalStateException(
                        "Legacy print jobs require exactly one Hub/Core printer UUID mapping"
                );
            }
            UUID printerId = corePrinterIds.iterator().next();
            jdbcClient.sql("""
                            update hub.print_jobs
                            set printer_id_uuid = :printerId
                            where printer_id_uuid is null
                              and printer_id = :legacyPrinterId
                            """)
                    .param(PRINTER_ID_PARAMETER, printerId)
                    .param("legacyPrinterId", LEGACY_SINGLETON_PRINTER_ID)
                    .update();
        }

        assertNoMissingOrOrphanedOwnership("print jobs", INVALID_JOB_OWNERSHIP_QUERY);
    }

    private void assertNoMissingOrOrphanedOwnership(
            String description,
            String invalidOwnershipQuery
    ) {
        int invalidRows = count(invalidOwnershipQuery);
        if (invalidRows > 0) {
            throw new IllegalStateException("Missing or orphaned printer ownership in " + description);
        }
    }

    private void assertNoDuplicatePrinterStatistics() {
        int duplicateMappings = count("""
                select count(*)
                from (
                    select printer_id
                    from hub.printer_stats
                    where printer_id is not null
                    group by printer_id
                    having count(*) > 1
                ) duplicate_statistics
                """);
        if (duplicateMappings > 0) {
            throw new IllegalStateException("Multiple printer statistics rows map to the same printer");
        }
    }

    private int count(String sql) {
        Integer value = jdbcClient.sql(sql)
                .query(Integer.class)
                .single();
        return value == null ? 0 : value;
    }
}
