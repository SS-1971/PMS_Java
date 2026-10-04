-- Moves PMS persistence onto the project database convention.
--
--   * dedicated `pms` schema (V1 created everything in `public`)
--   * BaseEntity columns on every table: id UUID, created_by / modified_by UUID,
--     created_date / modified_date TIMESTAMP, is_active BOOLEAN (soft-delete flag)
--   * V1's deleted_on / deleted_by / correlation_id / created_on / modified_on are replaced by is_active
--     and the *_date columns
--
-- Rows are copied from the V1 `public.pms_*` tables so existing data survives. The V1 tables are left in
-- place (untouched, no longer read) so this migration is non-destructive; drop them in a later migration
-- once the copy has been verified.
--
-- created_by / modified_by were free-text IAM user ids (VARCHAR(64)); they can only be carried over when
-- they already are UUIDs, otherwise they become NULL (the audit trail for those rows is not recoverable
-- as a UUID). organisation_id and the IAM-owned plant/department/designation ids stay VARCHAR(24) — they
-- are opaque IAM ids, not PMS-generated UUIDs.

CREATE SCHEMA IF NOT EXISTS pms;

-- ── IAM replicas (not BaseEntity: keyed by IAM's own id) ────────────────────────────────────────────

CREATE TABLE pms.pms_plants (
    id              VARCHAR(24) PRIMARY KEY,
    organisation_id VARCHAR(24) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE,
    synced_on       TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'UTC')
);
CREATE INDEX idx_pms_plants_org ON pms.pms_plants (organisation_id);

CREATE TABLE pms.pms_departments (
    id              VARCHAR(24) PRIMARY KEY,
    organisation_id VARCHAR(24) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE,
    synced_on       TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'UTC')
);
CREATE INDEX idx_pms_departments_org ON pms.pms_departments (organisation_id);

CREATE TABLE pms.pms_designations (
    id              VARCHAR(24) PRIMARY KEY,
    organisation_id VARCHAR(24) NOT NULL,
    department_id   VARCHAR(24),
    name            VARCHAR(200) NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE,
    synced_on       TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'UTC')
);
CREATE INDEX idx_pms_designations_org ON pms.pms_designations (organisation_id);
CREATE INDEX idx_pms_designations_dept ON pms.pms_designations (department_id);

INSERT INTO pms.pms_plants (id, organisation_id, name, is_active, is_deleted, synced_on)
SELECT id, organisation_id, name, is_active, is_deleted, synced_on AT TIME ZONE 'UTC' FROM public.pms_plants;
INSERT INTO pms.pms_departments (id, organisation_id, name, is_active, is_deleted, synced_on)
SELECT id, organisation_id, name, is_active, is_deleted, synced_on AT TIME ZONE 'UTC' FROM public.pms_departments;
INSERT INTO pms.pms_designations (id, organisation_id, department_id, name, is_active, is_deleted, synced_on)
SELECT id, organisation_id, department_id, name, is_active, is_deleted, synced_on AT TIME ZONE 'UTC'
FROM public.pms_designations;

-- ── Masters ──────────────────────────────────────────────────────────────────────────────────────────

CREATE TABLE pms.pms_kras (
    id              UUID PRIMARY KEY,
    created_by      UUID,
    modified_by     UUID,
    created_date    TIMESTAMP,
    modified_date   TIMESTAMP,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    organisation_id VARCHAR(24) NOT NULL,
    name            VARCHAR(100) NOT NULL
);
CREATE UNIQUE INDEX ux_pms_kras_name ON pms.pms_kras (organisation_id, lower(name)) WHERE is_active;

CREATE TABLE pms.pms_kpis (
    id                UUID PRIMARY KEY,
    created_by        UUID,
    modified_by       UUID,
    created_date      TIMESTAMP,
    modified_date     TIMESTAMP,
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    organisation_id   VARCHAR(24) NOT NULL,
    kra_id            UUID NOT NULL REFERENCES pms.pms_kras (id),
    name              VARCHAR(120) NOT NULL,
    unit              VARCHAR(40),
    target_type       VARCHAR(20) NOT NULL,
    expected_outcome  VARCHAR(200),
    evidence_required VARCHAR(200)
);
CREATE INDEX idx_pms_kpis_kra ON pms.pms_kpis (kra_id);
CREATE UNIQUE INDEX ux_pms_kpis_name ON pms.pms_kpis (kra_id, lower(name)) WHERE is_active;

CREATE TABLE pms.pms_competencies (
    id              UUID PRIMARY KEY,
    created_by      UUID,
    modified_by     UUID,
    created_date    TIMESTAMP,
    modified_date   TIMESTAMP,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    organisation_id VARCHAR(24) NOT NULL,
    name            VARCHAR(120) NOT NULL,
    category        VARCHAR(20) NOT NULL,
    is_enabled      BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX ux_pms_competencies_name ON pms.pms_competencies (organisation_id, lower(name)) WHERE is_active;

CREATE TABLE pms.pms_rating_scales (
    id                            UUID PRIMARY KEY,
    created_by                    UUID,
    modified_by                   UUID,
    created_date                  TIMESTAMP,
    modified_date                 TIMESTAMP,
    is_active                     BOOLEAN NOT NULL DEFAULT TRUE,
    organisation_id               VARCHAR(24) NOT NULL,
    name                          VARCHAR(100) NOT NULL,
    is_default                    BOOLEAN NOT NULL DEFAULT FALSE,
    show_definitions_to_employees BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX idx_pms_rating_scales_org ON pms.pms_rating_scales (organisation_id);

CREATE TABLE pms.pms_rating_levels (
    id            UUID PRIMARY KEY,
    created_by    UUID,
    modified_by   UUID,
    created_date  TIMESTAMP,
    modified_date TIMESTAMP,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    scale_id      UUID NOT NULL REFERENCES pms.pms_rating_scales (id) ON DELETE CASCADE,
    rating        INT NOT NULL,
    label         VARCHAR(40) NOT NULL,
    definition    VARCHAR(200),
    score_min     NUMERIC(4, 2) NOT NULL,
    score_max     NUMERIC(4, 2) NOT NULL,
    color         VARCHAR(7) NOT NULL
);
CREATE UNIQUE INDEX ux_pms_rating_levels ON pms.pms_rating_levels (scale_id, rating);

-- ── Goal templates ───────────────────────────────────────────────────────────────────────────────────

CREATE TABLE pms.pms_goal_templates (
    id              UUID PRIMARY KEY,
    created_by      UUID,
    modified_by     UUID,
    created_date    TIMESTAMP,
    modified_date   TIMESTAMP,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    organisation_id VARCHAR(24) NOT NULL,
    financial_year  INT NOT NULL,
    name            VARCHAR(150) NOT NULL,
    description     VARCHAR(500),
    department_id   VARCHAR(24) NOT NULL,
    designation_id  VARCHAR(24) NOT NULL,
    effective_from  DATE,
    status          VARCHAR(20) NOT NULL
);
CREATE INDEX idx_pms_goal_templates_org_fy ON pms.pms_goal_templates (organisation_id, financial_year);
-- Name + FY + designation uniqueness among non-inactive templates (PMS_TEMPLATE_DUPLICATE) is enforced in
-- the service layer; the "non-inactive" carve-out is not expressible as a plain partial unique index.

CREATE TABLE pms.pms_goal_template_kras (
    id            UUID PRIMARY KEY,
    created_by    UUID,
    modified_by   UUID,
    created_date  TIMESTAMP,
    modified_date TIMESTAMP,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    template_id   UUID NOT NULL REFERENCES pms.pms_goal_templates (id) ON DELETE CASCADE,
    kra_id        UUID NOT NULL REFERENCES pms.pms_kras (id)
);
CREATE UNIQUE INDEX ux_pms_gt_kras ON pms.pms_goal_template_kras (template_id, kra_id);

CREATE TABLE pms.pms_goal_template_kpis (
    id                UUID PRIMARY KEY,
    created_by        UUID,
    modified_by       UUID,
    created_date      TIMESTAMP,
    modified_date     TIMESTAMP,
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    template_kra_id   UUID NOT NULL REFERENCES pms.pms_goal_template_kras (id) ON DELETE CASCADE,
    kpi_id            UUID NOT NULL REFERENCES pms.pms_kpis (id),
    weight            NUMERIC(5, 2) NOT NULL,
    target_type       VARCHAR(20),
    expected_outcome  VARCHAR(200),
    evidence_required VARCHAR(200)
);
CREATE UNIQUE INDEX ux_pms_gt_kpis ON pms.pms_goal_template_kpis (template_kra_id, kpi_id);

CREATE TABLE pms.pms_goal_template_competencies (
    id            UUID PRIMARY KEY,
    created_by    UUID,
    modified_by   UUID,
    created_date  TIMESTAMP,
    modified_date TIMESTAMP,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    template_id   UUID NOT NULL REFERENCES pms.pms_goal_templates (id) ON DELETE CASCADE,
    competency_id UUID NOT NULL REFERENCES pms.pms_competencies (id),
    weight        NUMERIC(5, 2) NOT NULL
);
CREATE UNIQUE INDEX ux_pms_gt_competencies ON pms.pms_goal_template_competencies (template_id, competency_id);

-- ── Cycles ───────────────────────────────────────────────────────────────────────────────────────────

CREATE TABLE pms.pms_cycles (
    id              UUID PRIMARY KEY,
    created_by      UUID,
    modified_by     UUID,
    created_date    TIMESTAMP,
    modified_date   TIMESTAMP,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    organisation_id VARCHAR(24) NOT NULL,
    cycle_code      VARCHAR(20) NOT NULL,
    name            VARCHAR(150) NOT NULL,
    description     VARCHAR(500),
    type            VARCHAR(20) NOT NULL,
    period_start    DATE NOT NULL,
    period_end      DATE NOT NULL,
    status          VARCHAR(20) NOT NULL,
    published_on    DATE,

    appl_plant_ids             JSONB   NOT NULL DEFAULT '[]',
    appl_all_departments       BOOLEAN NOT NULL DEFAULT TRUE,
    appl_department_ids        JSONB   NOT NULL DEFAULT '[]',
    appl_employment_types      JSONB   NOT NULL DEFAULT '[]',
    appl_min_service_months    INT     NOT NULL DEFAULT 0,
    appl_service_as_on         DATE,
    appl_exclude_probation     BOOLEAN NOT NULL DEFAULT FALSE,
    appl_exclude_notice_period BOOLEAN NOT NULL DEFAULT FALSE,

    finalize_rating_scale_id  UUID REFERENCES pms.pms_rating_scales (id),
    finalize_notify_managers  BOOLEAN NOT NULL DEFAULT TRUE,
    finalize_notify_employees BOOLEAN NOT NULL DEFAULT TRUE,
    finalize_notify_hod       BOOLEAN NOT NULL DEFAULT TRUE,
    finalize_notify_hr        BOOLEAN NOT NULL DEFAULT TRUE,

    notifications JSONB NOT NULL DEFAULT '[]'
);
CREATE UNIQUE INDEX ux_pms_cycles_code ON pms.pms_cycles (cycle_code);
CREATE INDEX idx_pms_cycles_org_status ON pms.pms_cycles (organisation_id, status);

CREATE TABLE pms.pms_cycle_stages (
    id            UUID PRIMARY KEY,
    created_by    UUID,
    modified_by   UUID,
    created_date  TIMESTAMP,
    modified_date TIMESTAMP,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    cycle_id      UUID NOT NULL REFERENCES pms.pms_cycles (id) ON DELETE CASCADE,
    stage         VARCHAR(40) NOT NULL,
    start_date    DATE,
    end_date      DATE,
    notify        BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX ux_pms_cycle_stages ON pms.pms_cycle_stages (cycle_id, stage);

-- ── Data copy from the V1 public tables (parents before children) ────────────────────────────────────
-- Enum columns were stored lowercase ('draft'); they are now the constant name ('DRAFT').

INSERT INTO pms.pms_kras (id, created_by, modified_by, created_date, modified_date, is_active, organisation_id, name)
SELECT id,
       CASE WHEN created_by  ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' THEN created_by::uuid END,
       CASE WHEN modified_by ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' THEN modified_by::uuid END,
       created_on AT TIME ZONE 'UTC', modified_on AT TIME ZONE 'UTC', deleted_on IS NULL, organisation_id, name
FROM public.pms_kras;

INSERT INTO pms.pms_kpis (id, created_by, modified_by, created_date, modified_date, is_active, organisation_id, kra_id,
                          name, unit, target_type, expected_outcome, evidence_required)
SELECT id,
       CASE WHEN created_by  ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' THEN created_by::uuid END,
       CASE WHEN modified_by ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' THEN modified_by::uuid END,
       created_on AT TIME ZONE 'UTC', modified_on AT TIME ZONE 'UTC', deleted_on IS NULL, organisation_id, kra_id,
       name, unit, upper(target_type), expected_outcome, evidence_required
FROM public.pms_kpis;

INSERT INTO pms.pms_competencies (id, created_by, modified_by, created_date, modified_date, is_active, organisation_id,
                                  name, category, is_enabled)
SELECT id,
       CASE WHEN created_by  ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' THEN created_by::uuid END,
       CASE WHEN modified_by ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' THEN modified_by::uuid END,
       created_on AT TIME ZONE 'UTC', modified_on AT TIME ZONE 'UTC', deleted_on IS NULL, organisation_id,
       name, upper(category), is_active
FROM public.pms_competencies;

INSERT INTO pms.pms_rating_scales (id, created_by, modified_by, created_date, modified_date, is_active, organisation_id,
                                   name, is_default, show_definitions_to_employees)
SELECT id,
       CASE WHEN created_by  ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' THEN created_by::uuid END,
       CASE WHEN modified_by ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' THEN modified_by::uuid END,
       created_on AT TIME ZONE 'UTC', modified_on AT TIME ZONE 'UTC', deleted_on IS NULL, organisation_id,
       name, is_default, show_definitions_to_employees
FROM public.pms_rating_scales;

INSERT INTO pms.pms_rating_levels (id, created_date, scale_id, rating, label, definition, score_min, score_max, color)
SELECT id, now() AT TIME ZONE 'UTC', scale_id, rating, label, definition, score_min, score_max, color
FROM public.pms_rating_levels;

INSERT INTO pms.pms_goal_templates (id, created_by, modified_by, created_date, modified_date, is_active, organisation_id,
                                    financial_year, name, description, department_id, designation_id, effective_from,
                                    status)
SELECT id,
       CASE WHEN created_by  ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' THEN created_by::uuid END,
       CASE WHEN modified_by ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' THEN modified_by::uuid END,
       created_on AT TIME ZONE 'UTC', modified_on AT TIME ZONE 'UTC', deleted_on IS NULL, organisation_id,
       financial_year, name, description, department_id, designation_id, effective_from, upper(status)
FROM public.pms_goal_templates;

INSERT INTO pms.pms_goal_template_kras (id, created_date, template_id, kra_id)
SELECT id, now() AT TIME ZONE 'UTC', template_id, kra_id FROM public.pms_goal_template_kras;

INSERT INTO pms.pms_goal_template_kpis (id, created_date, template_kra_id, kpi_id, weight, target_type,
                                        expected_outcome, evidence_required)
SELECT id, now() AT TIME ZONE 'UTC', template_kra_id, kpi_id, weight, upper(target_type), expected_outcome,
       evidence_required
FROM public.pms_goal_template_kpis;

INSERT INTO pms.pms_goal_template_competencies (id, created_date, template_id, competency_id, weight)
SELECT id, now() AT TIME ZONE 'UTC', template_id, competency_id, weight FROM public.pms_goal_template_competencies;

INSERT INTO pms.pms_cycles (id, created_by, modified_by, created_date, modified_date, is_active, organisation_id,
                            cycle_code, name, description, type, period_start, period_end, status, published_on,
                            appl_plant_ids, appl_all_departments, appl_department_ids, appl_employment_types,
                            appl_min_service_months, appl_service_as_on, appl_exclude_probation,
                            appl_exclude_notice_period, finalize_rating_scale_id, finalize_notify_managers,
                            finalize_notify_employees, finalize_notify_hod, finalize_notify_hr, notifications)
SELECT id,
       CASE WHEN created_by  ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' THEN created_by::uuid END,
       CASE WHEN modified_by ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' THEN modified_by::uuid END,
       created_on AT TIME ZONE 'UTC', modified_on AT TIME ZONE 'UTC', deleted_on IS NULL, organisation_id,
       cycle_code, name, description, upper(type), period_start, period_end, upper(status), published_on,
       appl_plant_ids, appl_all_departments, appl_department_ids, appl_employment_types,
       appl_min_service_months, appl_service_as_on, appl_exclude_probation,
       appl_exclude_notice_period, finalize_rating_scale_id, finalize_notify_managers,
       finalize_notify_employees, finalize_notify_hod, finalize_notify_hr, notifications
FROM public.pms_cycles;

INSERT INTO pms.pms_cycle_stages (id, created_date, cycle_id, stage, start_date, end_date, notify)
SELECT id, now() AT TIME ZONE 'UTC', cycle_id, upper(stage), start_date, end_date, notify FROM public.pms_cycle_stages;
