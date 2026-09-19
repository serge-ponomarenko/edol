-- Expand phase for the legacy printer identifier migration.
--
-- Core owns printer creation and UUID generation. Hub deliberately keeps the
-- legacy integer column until the application has synchronized the Core
-- printer catalog and backfilled existing operational data.

alter table tenants
    add column is_default boolean not null default false;

update tenants
set is_default = true
where (select count(*) from tenants) = 1;

create unique index uk_tenants_single_default
    on tenants (is_default)
    where is_default;

alter table printers
    add column display_id varchar(100),
    add column enabled boolean not null default false,
    add column available_in_core boolean not null default false,
    add column last_synced_at timestamp with time zone;

create unique index uk_printers_display_id
    on printers (display_id)
    where display_id is not null;

alter table print_jobs
    add column printer_id_uuid uuid;

alter table print_jobs
    alter column printer_id drop not null;

alter table print_jobs
    add constraint fk_print_jobs_printer_uuid
        foreign key (printer_id_uuid)
            references printers;

create index idx_print_jobs_printer_uuid
    on print_jobs (printer_id_uuid);

create unique index uk_print_jobs_printer_session
    on print_jobs (printer_id_uuid, session_id)
    where printer_id_uuid is not null
      and session_id is not null;

create unique index uk_printer_stats_printer
    on printer_stats (printer_id)
    where printer_id is not null;
