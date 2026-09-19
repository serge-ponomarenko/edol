-- EDOL Hub
-- Multi-Tenant / Multi-Printer foundation
--
-- Ownership roots:
--   Tenant -> Printer / Filament / Vendor / MaterialType
-- Child entities derive tenant ownership through their FK chain.
--
-- NOTE:
-- This migration intentionally does NOT add authentication/user tables yet.
-- Core remains tenant-unaware at this stage.

create table tenants
(
    id   uuid not null
        constraint pk_tenants
            primary key,
    name varchar(255) not null
);

create table printers
(
    id       uuid not null
        constraint pk_printers
            primary key,
    tenant_id uuid not null
        constraint fk_printers_tenant
            references tenants,
    name     varchar(255)
);

create index idx_printers_tenant
    on printers (tenant_id);

-- Tenant ownership for existing reference/domain entities.
alter table material_types
    add column tenant_id uuid;

alter table vendors
    add column tenant_id uuid;

alter table filaments
    add column tenant_id uuid;

-- Existing installations need a tenant before the new NOT NULL constraints
-- can be applied. The migration deliberately creates a single tenant for
-- existing data and assigns all existing tenant-owned records to it.
insert into tenants (id, name)
values ('00000000-0000-0000-0000-000000000001', 'Default Tenant');

update material_types
set tenant_id = '00000000-0000-0000-0000-000000000001'
where tenant_id is null;

update vendors
set tenant_id = '00000000-0000-0000-0000-000000000001'
where tenant_id is null;

update filaments
set tenant_id = '00000000-0000-0000-0000-000000000001'
where tenant_id is null;

alter table material_types
    alter column tenant_id set not null;

alter table vendors
    alter column tenant_id set not null;

alter table filaments
    alter column tenant_id set not null;

alter table material_types
    add constraint fk_material_types_tenant
        foreign key (tenant_id) references tenants;

alter table vendors
    add constraint fk_vendors_tenant
        foreign key (tenant_id) references tenants;

alter table filaments
    add constraint fk_filaments_tenant
        foreign key (tenant_id) references tenants;

create index idx_material_types_tenant
    on material_types (tenant_id);

create index idx_vendors_tenant
    on vendors (tenant_id);

create index idx_filaments_tenant
    on filaments (tenant_id);

-- PrintJob becomes printer-scoped.
-- The current column is a legacy integer and cannot be migrated to UUID
-- without a source mapping from Core's old integer printer identifiers.
--
-- IMPORTANT: leave the old column untouched until the application/Core
-- mapping required for the UUID migration is available.
--
-- Target state:
--   print_jobs.printer_id uuid NOT NULL -> printers.id
--
-- This migration therefore creates the Printer ownership model first,
-- without guessing a mapping for existing print jobs.

alter table maintenance_definition
    add column printer_id uuid;

alter table printer_stats
    add column printer_id uuid;

-- Existing maintenance definitions/stats cannot be assigned to a printer
-- safely because the current schema contains no printer relationship.
-- Keep these columns nullable for this migration; application/backfill
-- must establish the relationship before making them NOT NULL.

alter table maintenance_definition
    add constraint fk_maintenance_definition_printer
        foreign key (printer_id) references printers;

alter table printer_stats
    add constraint fk_printer_stats_printer
        foreign key (printer_id) references printers;

create index idx_maintenance_definition_printer
    on maintenance_definition (printer_id);

create index idx_printer_stats_printer
    on printer_stats (printer_id);

-- Existing uniqueness constraints on tenant-owned reference data are
-- currently global. They must become tenant-scoped.
alter table material_types
    drop constraint uk_material_types_name;

alter table material_types
    add constraint uk_material_types_tenant_name
        unique (tenant_id, name);

alter table vendors
    drop constraint uk_vendors_name;

alter table vendors
    add constraint uk_vendors_tenant_name
        unique (tenant_id, name);
