-- PMS schema (V2): applicability is split into "Goal Setting - who gets goals" and "Appraisal - who is rated".
-- Run once against a database created from V1. A database kept in sync by ddl-auto=update already has the
-- new columns; the data copy and the unique-key swap below still need running once.

alter table pms.pms_cycle_applicability add column if not exists appraisal_minimum_service_months integer;
-- Existing cycles had one minimum service for everything; carry it over to the appraisal half.
update pms.pms_cycle_applicability
   set appraisal_minimum_service_months = minimum_service_months
 where appraisal_minimum_service_months is null;

-- PROBATIONARY is a new employment type; the V1 check constraint only lists the original three.
alter table pms.pms_cycle_employment_type drop constraint if exists pms_cycle_employment_type_employment_type_check;
alter table pms.pms_cycle_employment_type
    add constraint pms_cycle_employment_type_employment_type_check
    check (employment_type in ('PERMANENT','CONTRACT','PROBATIONARY','TRAINEE'));

alter table pms.pms_cycle_employment_type drop constraint if exists uk_pms_cycle_employment_type_cycle_type;
alter table pms.pms_cycle_employment_type add column if not exists scope varchar(20) not null default 'GOAL_SETTING';
-- Existing employment types applied to both halves.
insert into pms.pms_cycle_employment_type (id, cycle_id, employment_type, scope, is_active, created_date, modified_date, created_by, modified_by)
select gen_random_uuid(), cycle_id, employment_type, 'APPRAISAL', is_active, created_date, modified_date, created_by, modified_by
  from pms.pms_cycle_employment_type
 where scope = 'GOAL_SETTING'
   and not exists (select 1 from pms.pms_cycle_employment_type x
                    where x.cycle_id = pms.pms_cycle_employment_type.cycle_id and x.scope = 'APPRAISAL');

alter table pms.pms_cycle_employment_type drop constraint if exists uk_pms_cycle_employment_type_cycle_scope_type;
alter table pms.pms_cycle_employment_type
    add constraint uk_pms_cycle_employment_type_cycle_scope_type unique (cycle_id, scope, employment_type);
