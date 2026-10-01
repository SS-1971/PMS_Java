-- PMS Cycle + PMS Configuration schema.
--
-- Ids: rows PMS owns (kras, kpis, competencies, rating scales/levels, goal
-- templates, cycles) use generated UUIDs. Ids PMS only references (plant,
-- department, designation) are VARCHAR(24) — IAM's Mongo ObjectId hex, kept
-- as opaque strings rather than re-typed, so a replica row's id is always
-- byte-for-byte the id IAM published.
--
-- Audit columns (created_on/by, modified_on/by, deleted_on/by, correlation_id)
-- mirror the soft-delete + audit-stamp convention the Python services use
-- (src/correlation.py's audit_create()/stamp_modified()).

-- ── IAM replicas (kept current by pms-messaging's domain_events consumer) ──

CREATE TABLE pms_plants (
    id              VARCHAR(24) PRIMARY KEY,
    organisation_id VARCHAR(24) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE,
    synced_on       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_pms_plants_org ON pms_plants (organisation_id);

CREATE TABLE pms_departments (
    id              VARCHAR(24) PRIMARY KEY,
    organisation_id VARCHAR(24) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE,
    synced_on       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_pms_departments_org ON pms_departments (organisation_id);

CREATE TABLE pms_designations (
    id              VARCHAR(24) PRIMARY KEY,
    organisation_id VARCHAR(24) NOT NULL,
    department_id   VARCHAR(24),
    name            VARCHAR(200) NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE,
    synced_on       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_pms_designations_org ON pms_designations (organisation_id);
CREATE INDEX idx_pms_designations_dept ON pms_designations (department_id);

-- ── Masters (2.5–2.10) ──────────────────────────────────────────────────────

CREATE TABLE pms_kras (
    id              UUID PRIMARY KEY,
    organisation_id VARCHAR(24) NOT NULL,
    name            VARCHAR(100) NOT NULL,
    created_on      TIMESTAMPTZ NOT NULL,
    created_by      VARCHAR(64) NOT NULL,
    modified_on     TIMESTAMPTZ,
    modified_by     VARCHAR(64),
    deleted_on      TIMESTAMPTZ,
    deleted_by      VARCHAR(64),
    correlation_id  VARCHAR(64)
);
CREATE UNIQUE INDEX ux_pms_kras_name ON pms_kras (organisation_id, lower(name)) WHERE deleted_on IS NULL;

CREATE TABLE pms_kpis (
    id                UUID PRIMARY KEY,
    organisation_id   VARCHAR(24) NOT NULL,
    kra_id            UUID NOT NULL REFERENCES pms_kras (id),
    name              VARCHAR(120) NOT NULL,
    unit              VARCHAR(40),
    target_type       VARCHAR(20) NOT NULL,
    expected_outcome  VARCHAR(200),
    evidence_required VARCHAR(200),
    created_on        TIMESTAMPTZ NOT NULL,
    created_by        VARCHAR(64) NOT NULL,
    modified_on       TIMESTAMPTZ,
    modified_by       VARCHAR(64),
    deleted_on        TIMESTAMPTZ,
    deleted_by        VARCHAR(64),
    correlation_id    VARCHAR(64)
);
CREATE INDEX idx_pms_kpis_kra ON pms_kpis (kra_id);
CREATE UNIQUE INDEX ux_pms_kpis_name ON pms_kpis (kra_id, lower(name)) WHERE deleted_on IS NULL;

CREATE TABLE pms_competencies (
    id              UUID PRIMARY KEY,
    organisation_id VARCHAR(24) NOT NULL,
    name            VARCHAR(120) NOT NULL,
    category        VARCHAR(20) NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_on      TIMESTAMPTZ NOT NULL,
    created_by      VARCHAR(64) NOT NULL,
    modified_on     TIMESTAMPTZ,
    modified_by     VARCHAR(64),
    deleted_on      TIMESTAMPTZ,
    deleted_by      VARCHAR(64),
    correlation_id  VARCHAR(64)
);
CREATE UNIQUE INDEX ux_pms_competencies_name ON pms_competencies (organisation_id, lower(name)) WHERE deleted_on IS NULL;

CREATE TABLE pms_rating_scales (
    id                            UUID PRIMARY KEY,
    organisation_id               VARCHAR(24) NOT NULL,
    name                          VARCHAR(100) NOT NULL,
    is_default                    BOOLEAN NOT NULL DEFAULT FALSE,
    show_definitions_to_employees BOOLEAN NOT NULL DEFAULT TRUE,
    created_on                    TIMESTAMPTZ NOT NULL,
    created_by                    VARCHAR(64) NOT NULL,
    modified_on                   TIMESTAMPTZ,
    modified_by                   VARCHAR(64),
    deleted_on                    TIMESTAMPTZ,
    deleted_by                    VARCHAR(64),
    correlation_id                VARCHAR(64)
);
CREATE INDEX idx_pms_rating_scales_org ON pms_rating_scales (organisation_id);

CREATE TABLE pms_rating_levels (
    id         UUID PRIMARY KEY,
    scale_id   UUID NOT NULL REFERENCES pms_rating_scales (id) ON DELETE CASCADE,
    rating     INT NOT NULL,
    label      VARCHAR(40) NOT NULL,
    definition VARCHAR(200),
    score_min  NUMERIC(4, 2) NOT NULL,
    score_max  NUMERIC(4, 2) NOT NULL,
    color      VARCHAR(7) NOT NULL
);
CREATE UNIQUE INDEX ux_pms_rating_levels ON pms_rating_levels (scale_id, rating);

-- ── Goal templates (2.1–2.4) ─────────────────────────────────────────────────

CREATE TABLE pms_goal_templates (
    id              UUID PRIMARY KEY,
    organisation_id VARCHAR(24) NOT NULL,
    financial_year  INT NOT NULL,
    name            VARCHAR(150) NOT NULL,
    description     VARCHAR(500),
    department_id   VARCHAR(24) NOT NULL,
    designation_id  VARCHAR(24) NOT NULL,
    effective_from  DATE,
    status          VARCHAR(20) NOT NULL,
    created_on      TIMESTAMPTZ NOT NULL,
    created_by      VARCHAR(64) NOT NULL,
    modified_on     TIMESTAMPTZ,
    modified_by     VARCHAR(64),
    deleted_on      TIMESTAMPTZ,
    deleted_by      VARCHAR(64),
    correlation_id  VARCHAR(64)
);
CREATE INDEX idx_pms_goal_templates_org_fy ON pms_goal_templates (organisation_id, financial_year);
-- Name + FY + designation must be unique among non-inactive templates (PMS_TEMPLATE_DUPLICATE);
-- enforced in the service layer (the "non-inactive" carve-out isn't expressible as a plain
-- partial unique index without also excluding soft-deleted rows, which the service already checks).

CREATE TABLE pms_goal_template_kras (
    id          UUID PRIMARY KEY,
    template_id UUID NOT NULL REFERENCES pms_goal_templates (id) ON DELETE CASCADE,
    kra_id      UUID NOT NULL REFERENCES pms_kras (id)
);
CREATE UNIQUE INDEX ux_pms_gt_kras ON pms_goal_template_kras (template_id, kra_id);

CREATE TABLE pms_goal_template_kpis (
    id                UUID PRIMARY KEY,
    template_kra_id   UUID NOT NULL REFERENCES pms_goal_template_kras (id) ON DELETE CASCADE,
    kpi_id            UUID NOT NULL REFERENCES pms_kpis (id),
    weight            NUMERIC(5, 2) NOT NULL,
    target_type       VARCHAR(20),
    expected_outcome  VARCHAR(200),
    evidence_required VARCHAR(200)
);
CREATE UNIQUE INDEX ux_pms_gt_kpis ON pms_goal_template_kpis (template_kra_id, kpi_id);

CREATE TABLE pms_goal_template_competencies (
    id            UUID PRIMARY KEY,
    template_id   UUID NOT NULL REFERENCES pms_goal_templates (id) ON DELETE CASCADE,
    competency_id UUID NOT NULL REFERENCES pms_competencies (id),
    weight        NUMERIC(5, 2) NOT NULL
);
CREATE UNIQUE INDEX ux_pms_gt_competencies ON pms_goal_template_competencies (template_id, competency_id);

-- ── Cycles (1.1–1.6) ──────────────────────────────────────────────────────────

CREATE TABLE pms_cycles (
    id              UUID PRIMARY KEY,
    organisation_id VARCHAR(24) NOT NULL,
    cycle_code      VARCHAR(20) NOT NULL,
    name            VARCHAR(150) NOT NULL,
    description     VARCHAR(500),
    type            VARCHAR(20) NOT NULL,
    period_start    DATE NOT NULL,
    period_end      DATE NOT NULL,
    status          VARCHAR(20) NOT NULL,
    published_on    DATE,

    -- applicability
    appl_plant_ids             JSONB   NOT NULL DEFAULT '[]',
    appl_all_departments       BOOLEAN NOT NULL DEFAULT TRUE,
    appl_department_ids        JSONB   NOT NULL DEFAULT '[]',
    appl_employment_types      JSONB   NOT NULL DEFAULT '[]',
    appl_min_service_months    INT     NOT NULL DEFAULT 0,
    appl_service_as_on         DATE,
    appl_exclude_probation     BOOLEAN NOT NULL DEFAULT FALSE,
    appl_exclude_notice_period BOOLEAN NOT NULL DEFAULT FALSE,

    -- finalize
    finalize_rating_scale_id  UUID REFERENCES pms_rating_scales (id),
    finalize_notify_managers  BOOLEAN NOT NULL DEFAULT TRUE,
    finalize_notify_employees BOOLEAN NOT NULL DEFAULT TRUE,
    finalize_notify_hod       BOOLEAN NOT NULL DEFAULT TRUE,
    finalize_notify_hr        BOOLEAN NOT NULL DEFAULT TRUE,

    -- Per-audience notification counts from the most recent publish (screen 1.6),
    -- e.g. [{"audience":"employees","sent":1102}] — re-readable via
    -- GET .../activation so a page refresh doesn't lose the summary.
    notifications  JSONB NOT NULL DEFAULT '[]',

    created_on     TIMESTAMPTZ NOT NULL,
    created_by     VARCHAR(64) NOT NULL,
    modified_on    TIMESTAMPTZ,
    modified_by    VARCHAR(64),
    deleted_on     TIMESTAMPTZ,
    deleted_by     VARCHAR(64),
    correlation_id VARCHAR(64)
);
CREATE UNIQUE INDEX ux_pms_cycles_code ON pms_cycles (cycle_code);
CREATE INDEX idx_pms_cycles_org_status ON pms_cycles (organisation_id, status);

CREATE TABLE pms_cycle_stages (
    id         UUID PRIMARY KEY,
    cycle_id   UUID NOT NULL REFERENCES pms_cycles (id) ON DELETE CASCADE,
    stage      VARCHAR(40) NOT NULL,
    start_date DATE,
    end_date   DATE,
    notify     BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX ux_pms_cycle_stages ON pms_cycle_stages (cycle_id, stage);
