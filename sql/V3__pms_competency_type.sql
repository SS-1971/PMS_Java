-- PMS schema (V3): a competency now carries a type, HR or MANAGER (who rates it).
-- A database kept in sync by ddl-auto=update already has the column; the data fix below still needs running once.

alter table pms.competency_master add column if not exists competency_type varchar(20) not null default 'MANAGER';

-- Until now an "HR..." category meant an HR competency.
update pms.competency_master
   set competency_type = 'HR'
 where lower(category) like 'hr%' and competency_type = 'MANAGER';

alter table pms.competency_master drop constraint if exists competency_master_competency_type_check;
alter table pms.competency_master
    add constraint competency_master_competency_type_check check (competency_type in ('HR','MANAGER'));

-- The standard HR-rated competencies (category is "Behavioural", so the rule above does not catch them).
update pms.competency_master
   set competency_type = 'HR'
 where lower(name) in ('attendance and punctuality', 'discipline', 'honesty and integrity',
                       'co-employee relations at work (teamwork)');
