-- HOD approval history: one row per step of a goal sheet or target revision, plus per-KPI HOD decisions.
-- ddl-auto=update creates all of this on startup; run it by hand on databases that need it.

alter table pms.target_revision_row add column if not exists decision varchar(20);
alter table pms.target_revision_row add column if not exists hod_remarks varchar(500);

create table if not exists pms.goal_sheet_history (
    id uuid primary key,
    assignment_id uuid not null references pms.goal_assignment (id),
    request_type varchar(20) not null,
    request_ref uuid,
    action varchar(30) not null,
    actor_user_id varchar(64) not null,
    actor_name varchar(200),
    actor_role varchar(20) not null,
    kpi_id uuid,
    kpi_name varchar(255),
    unit varchar(50),
    target_before numeric(14,2),
    target_after numeric(14,2),
    more_changes integer not null default 0,
    summary varchar(500),
    remarks varchar(500),
    occurred_on timestamp(6) not null,
    created_by varchar(255),
    modified_by varchar(255),
    created_date timestamp(6),
    modified_date timestamp(6),
    is_active boolean not null default true
);
create index if not exists idx_goal_sheet_history_assignment on pms.goal_sheet_history (assignment_id);
create index if not exists idx_goal_sheet_history_actor on pms.goal_sheet_history (actor_role, action);
