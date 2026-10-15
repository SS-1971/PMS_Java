-- Cycle screens aligned to the mockups: GP notification switch, chosen approval workflow, and the
-- Approval Workflow master. ddl-auto=update creates all of this on startup; run by hand where needed.
alter table pms.pms_cycle add column if not exists notify_gp boolean not null default false;
alter table pms.pms_cycle add column if not exists approval_workflow_id uuid;

create table if not exists pms.approval_workflow (
    id uuid primary key,
    organisation_id varchar(64) not null,
    name varchar(200) not null,
    description varchar(500),
    steps text not null,
    status varchar(20) not null,
    created_by varchar(255),
    modified_by varchar(255),
    created_date timestamp(6),
    modified_date timestamp(6),
    is_active boolean not null default true
);
create index if not exists idx_approval_workflow_org on pms.approval_workflow (organisation_id);

-- ddl-auto does not change existing CHECK constraints, so the ones that list enum values must be recreated.
-- Stage list of the timeline (adds HR_COMPETENCY_RATING and APPROVAL_PUBLISH; the old two stay so old cycles load).
alter table pms.pms_cycle_stage drop constraint if exists pms_cycle_stage_stage_check;
alter table pms.pms_cycle_stage add constraint pms_cycle_stage_stage_check check (stage in (
    'GOAL_SETTING', 'EMPLOYEE_ACKNOWLEDGEMENT', 'HOD_APPROVAL', 'PROGRESS_TRACKING', 'MID_YEAR_REVIEW',
    'SELF_APPRAISAL', 'MANAGER_APPRAISAL', 'HOD_REVIEW', 'HR_COMPETENCY_RATING',
    'CALIBRATION_FINAL_APPROVAL', 'APPROVAL_PUBLISH'));

-- Notification audiences (adds GP).
alter table pms.pms_cycle_notification drop constraint if exists pms_cycle_notification_audience_check;
alter table pms.pms_cycle_notification add constraint pms_cycle_notification_audience_check check (audience in (
    'HOD_REVIEWERS', 'REPORTING_MANAGERS', 'EMPLOYEES', 'HR', 'GP'));
