package org.spon.edolhub.config.migration;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class GramsPrecisionMigration {

    private final JdbcTemplate jdbcTemplate;


    @PostConstruct
    public void migrateGramsToDouble() {
        migrateColumnToDouble(
                "PRINT_ALLOCATION_ITEM",
                "ALLOCATED_GRAMS"
        );

        migrateColumnToDouble(
                "PRINT_ALLOCATION_GROUP",
                "REQUESTED_GRAMS"
        );

        migrateColumnToDouble(
                "PRINT_ALLOCATION_GROUP",
                "ALLOCATED_GRAMS"
        );

        migrateColumnToDouble(
                "PRINT_ALLOCATION_GROUP",
                "MISSING_GRAMS"
        );

        migrateColumnToDouble(
                "FILAMENT_SPOOLS",
                "WEIGHT_TOTAL"
        );

        migrateColumnToDouble(
                "FILAMENT_SPOOLS",
                "WEIGHT_REMAINING"
        );
    }


    private void migrateColumnToDouble(
            String tableName,
            String columnName
    ) {
        String type = jdbcTemplate.queryForObject("""
                        SELECT DATA_TYPE
                        FROM INFORMATION_SCHEMA.COLUMNS
                        WHERE TABLE_NAME = ?
                          AND COLUMN_NAME = ?
                        """,
                String.class,
                tableName,
                columnName
        );

        if (type == null) {
            log.warn(
                    "Column not found: {}.{}",
                    tableName,
                    columnName
            );

            return;
        }

        if ("INTEGER".equalsIgnoreCase(type)
                || "INT".equalsIgnoreCase(type)
                || "BIGINT".equalsIgnoreCase(type)
                || "SMALLINT".equalsIgnoreCase(type)
        ) {

            log.info(
                    "Migrating {}.{} from {} to DOUBLE",
                    tableName,
                    columnName,
                    type
            );

            jdbcTemplate.execute("""
                    ALTER TABLE %s
                    ALTER COLUMN %s DOUBLE
                    """.formatted(
                    tableName,
                    columnName
            ));

        }
    }

}