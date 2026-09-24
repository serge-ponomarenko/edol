----------------------------------------------------------------------------
-- Print Jobs: migrate technical ID from BIGINT to UUID v7
-- Preserve existing BIGINT IDs as public_id.
----------------------------------------------------------------------------

CREATE EXTENSION IF NOT EXISTS pgcrypto;


----------------------------------------------------------------------------
-- Temporary UUID v7 generator for existing rows.
-- PostgreSQL 17 has no native uuidv7().
----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION uuidv7_migration()
    RETURNS uuid
    LANGUAGE plpgsql
    VOLATILE
AS
$$
DECLARE
    timestamp_ms bigint;
    random_bytes bytea;
    random_hex   text;
    uuid_hex     text;
BEGIN
    timestamp_ms :=
            FLOOR(
                    EXTRACT(EPOCH FROM CLOCK_TIMESTAMP()) * 1000
            )::bigint;

    random_bytes := gen_random_bytes(10);
    random_hex := ENCODE(random_bytes, 'hex');

    uuid_hex :=
            LPAD(TO_HEX(timestamp_ms), 12, '0')
                || '7'
                || SUBSTR(random_hex, 1, 1)
                || SUBSTR(random_hex, 2, 2)
                || LPAD(
                    TO_HEX(
                            (GET_BYTE(random_bytes, 2) & 63) | 128
                    ),
                    2,
                    '0'
                   )
                || SUBSTR(random_hex, 7, 14);

    RETURN uuid_hex::uuid;
END;
$$;


----------------------------------------------------------------------------
-- 1. Add new columns to print_jobs
----------------------------------------------------------------------------

ALTER TABLE print_jobs
    ADD COLUMN new_id uuid;

ALTER TABLE print_jobs
    ADD COLUMN public_id bigint;


----------------------------------------------------------------------------
-- 2. Preserve existing numeric IDs
----------------------------------------------------------------------------

UPDATE print_jobs
SET public_id = id;


----------------------------------------------------------------------------
-- 3. Generate UUID v7 for existing jobs
----------------------------------------------------------------------------

UPDATE print_jobs
SET new_id = uuidv7_migration();


----------------------------------------------------------------------------
-- 4. Validate generated values
----------------------------------------------------------------------------

ALTER TABLE print_jobs
    ALTER COLUMN new_id SET NOT NULL;

ALTER TABLE print_jobs
    ALTER COLUMN public_id SET NOT NULL;


----------------------------------------------------------------------------
-- 5. Add public ID sequence
----------------------------------------------------------------------------

CREATE SEQUENCE print_jobs_public_id_seq;

SELECT SETVAL(
               'print_jobs_public_id_seq',
               COALESCE(
                       (SELECT MAX(public_id) FROM print_jobs),
                       1
               ),
               EXISTS (SELECT 1 FROM print_jobs)
       );

ALTER TABLE print_jobs
    ALTER COLUMN public_id
        SET DEFAULT NEXTVAL('print_jobs_public_id_seq');

ALTER SEQUENCE print_jobs_public_id_seq
    OWNED BY print_jobs.public_id;


ALTER TABLE print_jobs
    ADD CONSTRAINT uk_print_jobs_public_id
        UNIQUE (public_id);


----------------------------------------------------------------------------
-- 6. Prepare UUID FK columns
----------------------------------------------------------------------------

ALTER TABLE job_spool_usage
    ADD COLUMN new_print_job_id uuid;

ALTER TABLE print_allocation_preview
    ADD COLUMN new_print_job_id uuid;


----------------------------------------------------------------------------
-- 7. Populate UUID FK values using the preserved public ID
----------------------------------------------------------------------------

UPDATE job_spool_usage jsu
SET new_print_job_id = pj.new_id
FROM print_jobs pj
WHERE jsu.print_job_id = pj.public_id;


UPDATE print_allocation_preview pap
SET new_print_job_id = pj.new_id
FROM print_jobs pj
WHERE pap.print_job_id = pj.public_id;


----------------------------------------------------------------------------
-- 8. Validate FK migration
----------------------------------------------------------------------------

DO
$$
    BEGIN

        IF EXISTS (SELECT 1
                   FROM job_spool_usage
                   WHERE new_print_job_id IS NULL) THEN
            RAISE EXCEPTION
                'UUID migration failed: job_spool_usage contains unmapped print_job_id';
        END IF;

        IF EXISTS (SELECT 1
                   FROM print_allocation_preview
                   WHERE new_print_job_id IS NULL) THEN
            RAISE EXCEPTION
                'UUID migration failed: print_allocation_preview contains unmapped print_job_id';
        END IF;

    END;
$$;


----------------------------------------------------------------------------
-- 9. Remove old FK constraints and indexes
----------------------------------------------------------------------------

ALTER TABLE job_spool_usage
    DROP CONSTRAINT fk_job_spool_usage_print_job;

ALTER TABLE print_allocation_preview
    DROP CONSTRAINT fk_print_allocation_preview_print_job;


DROP INDEX idx_job_spool_usage_print_job;


----------------------------------------------------------------------------
-- 10. Remove old PK
----------------------------------------------------------------------------

ALTER TABLE print_jobs
    DROP CONSTRAINT pk_print_jobs;


----------------------------------------------------------------------------
-- 11. Replace print_jobs.id
----------------------------------------------------------------------------

ALTER TABLE print_jobs
    DROP COLUMN id;

ALTER TABLE print_jobs
    RENAME COLUMN new_id TO id;


ALTER TABLE print_jobs
    ADD CONSTRAINT pk_print_jobs
        PRIMARY KEY (id);


----------------------------------------------------------------------------
-- 12. Replace job_spool_usage.print_job_id
----------------------------------------------------------------------------

ALTER TABLE job_spool_usage
    DROP COLUMN print_job_id;

ALTER TABLE job_spool_usage
    RENAME COLUMN new_print_job_id TO print_job_id;


ALTER TABLE job_spool_usage
    ADD CONSTRAINT fk_job_spool_usage_print_job
        FOREIGN KEY (print_job_id)
            REFERENCES print_jobs (id);


CREATE INDEX idx_job_spool_usage_print_job
    ON job_spool_usage (print_job_id);


----------------------------------------------------------------------------
-- 13. Replace print_allocation_preview.print_job_id
----------------------------------------------------------------------------

ALTER TABLE print_allocation_preview
    DROP COLUMN print_job_id;

ALTER TABLE print_allocation_preview
    RENAME COLUMN new_print_job_id TO print_job_id;


ALTER TABLE print_allocation_preview
    ADD CONSTRAINT fk_print_allocation_preview_print_job
        FOREIGN KEY (print_job_id)
            REFERENCES print_jobs (id);


ALTER TABLE print_allocation_preview
    ADD CONSTRAINT uk_print_allocation_preview_print_job
        UNIQUE (print_job_id);


----------------------------------------------------------------------------
-- 14. Remove temporary migration function
----------------------------------------------------------------------------

DROP FUNCTION uuidv7_migration();
