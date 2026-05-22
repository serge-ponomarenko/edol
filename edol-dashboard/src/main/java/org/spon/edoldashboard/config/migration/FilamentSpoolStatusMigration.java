package org.spon.edoldashboard.config.migration;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FilamentSpoolStatusMigration {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrateStatuses() {

        String type = jdbcTemplate.queryForObject("""
                        SELECT DATA_TYPE
                        FROM INFORMATION_SCHEMA.COLUMNS
                        WHERE TABLE_NAME = 'FILAMENT_SPOOLS'
                          AND COLUMN_NAME = 'STATUS'
                        """,
                String.class
        );

        if ("ENUM".equalsIgnoreCase(type)) {

            // ENUM -> VARCHAR
            jdbcTemplate.execute("""
                    ALTER TABLE filament_spools
                    ALTER COLUMN status VARCHAR(32)
                    """);

        }

        // NEW -> SEALED
        jdbcTemplate.update("""
                UPDATE filament_spools
                SET status = 'SEALED'
                WHERE status = 'NEW'
                """);

    }

}