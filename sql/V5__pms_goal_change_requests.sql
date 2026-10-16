-- PMS schema (V5): the first target a manager assigned is kept, and every employee change request is logged.
-- ddl-auto=update creates these on startup; this is for databases managed by hand.
alter table pms.goal_assignment_target add column if not exists original_target numeric(14,2);

-- Targets that were already sent: what the employee saw is the original.
update pms.goal_assignment_target
   set original_target = coalesce(sent_target, target)
 where original_target is null and (sent_target is not null or target is not null)
   and assignment_id in (select id from pms.goal_assignment where status <> 'DRAFT');

create table if not exists pms.goal_change_request (
    id uuid primary key,
    assignment_id uuid not null references pms.goal_assignment (id),
    kpi_id uuid not null references pms.kpi_master (id),
    revision_no integer not null,
    requested_by varchar(64) not null,
    requested_on timestamp(6) not null,
    target_at_request numeric(14,2),
    proposed_target numeric(14,2),
    reason text not null,
    status varchar(20) not null check (status in ('REQUESTED','RESPONDED')),
    decision varchar(20) check (decision in ('REVISED','UNCHANGED')),
    final_target numeric(14,2),
    manager_remarks text,
    responded_by varchar(64),
    responded_on timestamp(6),
    acknowledged_on timestamp(6),
    is_active boolean default true not null,
    created_date timestamp(6),
    modified_date timestamp(6),
    created_by varchar(255),
    modified_by varchar(255),
    constraint uk_goal_change_request_no unique (assignment_id, kpi_id, revision_no)
);
create index if not exists idx_goal_change_request_assignment on pms.goal_change_request (assignment_id);
