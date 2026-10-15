# PMS API – Frontend Guide

Written for: frontend developers integrating screens 1.1–1.6 (appraisal cycles), 2.1–2.4 (goal templates), 2.5–2.10 (KRA, KPI, competency and rating-scale masters).

This document lists **every** endpoint exposed by `pms-app`, what each one does, what to send, what comes back, and which errors to handle.

---

## 1. Basics

| Item | Value |
|---|---|
| Base path | `/api/v1/pms` |
| Full example | `https://<host>/api/v1/pms/get/cycles` |
| Format | JSON in and out (except CSV export) |
| Field naming | `snake_case` everywhere (`cycle_code`, `period_start`, `kra_id`) |
| IDs | PMS-owned ids (cycles, templates, KRAs, KPIs, competencies, rating scales) are UUID strings, e.g. `3f2504e0-4f89-11d3-9a0c-0305e82c3301`. Sentrifugo ids (organisation, plant, department, role) are 24-character hex strings, e.g. `6aae4eb6c67aad006ac331e0`. |
| Dates | `LocalDate` → `"YYYY-MM-DD"`; `LocalDateTime` → `"YYYY-MM-DDTHH:mm:ss"` |
| Decimals | JSON numbers, e.g. `40.00` (weightages, scores) |
| CSRF | Not used. Auth is by bearer token only. |
| Sessions | Stateless. No cookies are needed. |

### 1.1 Authentication

Every endpoint needs a bearer token obtained from IAM login (`POST /auth/login` on IAM):

```http
Authorization: Bearer <access_token>
```

Send the token **without** a `Bearer ` prefix in the Swagger UI "Authorize" box; Swagger adds the prefix itself. In your own code, add the prefix in the header as shown above.

The organisation is taken from the token, never from the request. Do not send `organisation_id` in any body or query.

### 1.2 Permissions

There are two kinds of endpoints:

| Kind | Who can call it |
|---|---|
| **Read** (`GET`) | Any authenticated user of the organisation |
| **Write** (`POST`, `PUT`, `DELETE`) | Users with `performance_management` → `create_resource` in IAM, or org/super admins |

If a write is attempted without the permission, the API returns `403 FORBIDDEN` (see §2.3). Hide or disable write buttons based on the user's permissions.

### 1.3 Success envelope

Every JSON success response uses this shape:

```json
{
  "success": true,
  "message": "PMS cycle retrieved successfully",
  "data": { }
}
```

- `message` is human-readable text you can show in a toast.
- `data` is the payload described under each endpoint. For delete endpoints, `data` is `null`.
- List endpoints add a count to the message, e.g. `"Found 3 KRAs"`.

Exception: the **CSV export** endpoint returns the raw file (see §3.2), not this envelope.

### 1.4 Error envelope

All errors use this shape:

```json
{
  "detail": "Cycle not found",
  "code": "PMS_CYCLE_NOT_FOUND"
}
```

- `detail` is a message you can show to the user (or log).
- `code` is a stable identifier you should branch on.

---

## 2. Error reference

### 2.1 HTTP status and codes

| HTTP | `code` | When | Example `detail` |
|---|---|---|---|
| 400 | `BAD_REQUEST` | Body is not valid JSON, or a path/query value has the wrong type (e.g. `cycleId=abc`, `skip=x`). | `Malformed request` |
| 401 | `UNAUTHORIZED` | Token missing, expired or unknown. | `Missing bearer token` or the session error message |
| 403 | `FORBIDDEN` | Token is valid but the user lacks the write permission. | `You do not have permission to perform this action` |
| 403 | `PMS_INVALID_ORGANISATION` | The session has no organisation id. | `The session has no organisation.` |
| 404 | `NOT_FOUND` | Unknown URL. | `Not found` |
| 404 | `PMS_CYCLE_NOT_FOUND` | Cycle id not in this organisation. | `Cycle not found` |
| 404 | `PMS_TEMPLATE_NOT_FOUND` | Goal template id not in this organisation. | `Goal template not found` |
| 404 | `PMS_KRA_NOT_FOUND` | KRA id not found. | `KRA not found` |
| 404 | `PMS_KPI_NOT_FOUND` | KPI id not found. | `KPI not found` |
| 404 | `PMS_COMPETENCY_NOT_FOUND` | Competency id not found. | `Competency not found` |
| 404 | `PMS_RATING_SCALE_NOT_FOUND` | Rating scale id not found. | `Rating scale not found` |
| 409 | `PMS_KRA_DUPLICATE` | KRA name already exists (case-insensitive). | `A KRA named '…' already exists.` |
| 409 | `PMS_KPI_DUPLICATE` | KPI name already exists under the same KRA (case-insensitive). | `A KPI named '…' already exists under this KRA.` |
| 409 | `PMS_COMPETENCY_DUPLICATE` | Competency name already exists (case-insensitive). | `A competency named '…' already exists.` |
| 409 | `PMS_KRA_IN_USE` | Deleting a KRA that has KPIs or is used by a template. | `Cannot delete a KRA that still has KPIs under it.` |
| 409 | `PMS_KPI_IN_USE` | Deleting a KPI used by a goal template. | `Cannot delete a KPI used in a goal template.` |
| 409 | `PMS_COMPETENCY_IN_USE` | Deleting a competency used by a goal template. | `Cannot delete a competency used in a goal template.` |
| 409 | `PMS_CYCLE_NOT_EDITABLE` | Editing a cycle that is CLOSED or CANCELLED. | `Only a draft or active cycle can be edited.` |
| 409 | `PMS_CYCLE_NOT_CANCELLABLE` | Cancelling a cycle that is CLOSED or CANCELLED. | `Only a draft or active cycle can be cancelled.` |
| 409 | `PMS_CYCLE_NOT_PUBLISHABLE` | Publishing a cycle that is not DRAFT. | `Only a draft cycle can be published.` |
| 409 | `PMS_CYCLE_NOT_PUBLISHED` | Asking for activation of a DRAFT cycle. | `This cycle has not been published yet.` |
| 422 | `VALIDATION_ERROR` | Bean validation failed (required field missing, too long, wrong range) **or** a business rule was broken with this generic code. | `name: must not be blank; period_end: must not be null` |
| 422 | `PMS_CYCLE_PUBLISH_VALIDATION_FAILED` | Cycle is not complete enough to publish. | `Cycle cannot be published: a rating scale must be selected; …` |
| 422 | `PMS_TEMPLATE_INCOMPLETE` | Template step saved as complete but is missing KRAs, KPIs or competencies, or weightages are wrong. | `KPI weightage must total 100 (got 90).` |
| 422 | (domain codes like `PMS_KRA_NOT_FOUND`, `PMS_RATING_SCALE_NOT_FOUND`) | A referenced id is inactive or not found inside a save body. | `kra_id 3f… does not refer to an active KRA` |
| 500 | `INTERNAL_SERVER_ERROR` | Unexpected server error. | `An unexpected error occurred.` |

Notes for the frontend:

- **422 is used for validation.** Read `code`: `VALIDATION_ERROR` means a field or generic rule; `PMS_*` codes are business rules you can map to specific UI messages.
- For `VALIDATION_ERROR` from bean validation, `detail` is `field: message` pairs joined by `; `. Parse it for inline field errors, or show it as a single banner.

### 2.2 Shared enums (send and receive as lowercase strings)

Input is case-insensitive, but **always send lowercase**. Responses are always lowercase.

| Field | Allowed values |
|---|---|
| Cycle `type` | `annual`, `mid_year`, `custom` |
| Cycle `status` | `draft`, `active`, `closed`, `cancelled` |
| Cycle stage `stage` | `goal_setting`, `employee_acknowledgement`, `hod_approval`, `progress_tracking`, `mid_year_review`, `self_appraisal`, `manager_appraisal`, `hod_review`, `calibration_final_approval` |
| Employment type | `permanent`, `contract`, `trainee` |
| Goal template `status` | `draft`, `active`, `inactive` |
| Master `status` (KRA, KPI, competency, rating scale) | `active`, `inactive` |
| KPI / target `target_type` | `individual`, `common` |

### 2.3 Auth failure handling

- On `401`, clear the stored token and send the user to login.
- On `403` with `FORBIDDEN`, keep the user on the page and show a "no permission" message. Do not retry.

---

## 3. Appraisal cycles — `/pms-cycle`

Covers screens 1.1 to 1.6. The cycle wizard has four steps: **Basic (1.2) → Stages (1.3) → Applicability (1.4) → Finalize (1.5)**, then **Publish**, then **Activation (1.6)**.

Shared types used below:

**`CycleBasic`** (screen 1.2)

| Field | Type | Required | Rules |
|---|---|---|---|
| `name` | string | yes | 1–200 chars |
| `description` | string | no | max 2000 |
| `type` | string | yes | `annual`, `mid_year` or `custom` |
| `period_start` | date | yes | `YYYY-MM-DD` |
| `period_end` | date | yes | `YYYY-MM-DD`, must be after `period_start` |

**`CycleStage`** (screen 1.3)

| Field | Type | Required | Rules |
|---|---|---|---|
| `stage` | string | yes | One of the stage values in §2.2, unique per cycle |
| `start_date` | date | no | |
| `end_date` | date | no | Must not be before `start_date` |
| `notify` | boolean | no | Notify for this stage |

**`CycleApplicability`** (screen 1.4)

| Field | Type | Rules |
|---|---|---|
| `all_plants` | boolean | If `true`, `plant_ids` is ignored |
| `plant_ids` | string[] | Sentrifugo plant ids (24-character strings) |
| `all_departments` | boolean | If `false`, `department_ids` must have at least one id on publish |
| `department_ids` | string[] | Sentrifugo department ids (24-character strings) |
| `goal_setting` | object | **Who gets goals.** `{ employment_types, min_service_months }`. At least one employment type and `min_service_months` ≥ 0 are required on publish. |
| `appraisal` | object | **Who is rated.** `{ employment_types, min_service_months, service_as_on }`. All three are required on publish; service must be complete on or before `service_as_on`. |
| `exclude_notice_period` | boolean | Exclude resigned / notice-period employees |

Employment types are `permanent`, `contract`, `probationary` and `trainee`. The old flat `employment_types`, `min_service_months`, `service_as_on` and `exclude_probation` fields are gone: "probationary" is now an employment type, chosen per half.

**`CycleFinalize`** (screen 1.5)

| Field | Type | Rules |
|---|---|---|
| `rating_scale_id` | UUID | Must be an **active** rating scale. Required on publish. |
| `notify_managers` | boolean | |
| `notify_employees` | boolean | |
| `notify_hod` | boolean | |
| `notify_hr` | boolean | |

---

### 3.1 List cycles (screen 1.1)

`GET /api/v1/pms/pms-cycle/get/cycles`

Any authenticated user.

**Query parameters** (all optional)

| Name | Type | Default | Notes |
|---|---|---|---|
| `search` | string | | Matches cycle code or name |
| `year` | integer | | Financial year, e.g. `2026` |
| `type` | string | | `annual`, `mid_year`, `custom` |
| `plant_id` | string | | |
| `status` | string | | `draft`, `active`, `closed`, `cancelled`. Does **not** change `summary`. |
| `skip` | integer | `0` | Must be a multiple of `limit`. Otherwise `422 VALIDATION_ERROR`. |
| `limit` | integer | `20` | Max `100`. Values ≤ 0 use `20`. |

**Response `data`**

```json
{
  "items": [
    {
      "id": "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
      "cycle_code": "PMS-2526-A",
      "name": "Annual Appraisal FY 2025-26",
      "type": "annual",
      "period_start": "2025-04-01",
      "period_end": "2026-03-31",
      "applicable_to": "All Plants",
      "status": "active",
      "created_on": "2026-01-10"
    }
  ],
  "total": 1,
  "summary": { "all": 4, "draft": 1, "active": 2, "closed": 1, "cancelled": 0 }
}
```

| Field | Notes |
|---|---|
| `items[].applicable_to` | `"All Plants"` when the cycle covers all plants, otherwise `null`. The UI should show plant names itself. PMS does not know them. |
| `total` | Count matching **all filters**, for pagination |
| `summary` | Counts by status. **Ignores** the `status` filter, so tabs keep their numbers. |

**Pagination:** `page = skip / limit`. Next page is `skip + limit` while `skip + limit < total`.

**Message:** `"Found N PMS cycles"`.

---

### 3.2 Export cycles as CSV (screen 1.1 "Export Data")

`GET /api/v1/pms/pms-cycle/get/cycles/export`

Same query parameters as §3.1, except `skip` and `limit` (all matching rows are exported).

**Response:** raw bytes, **not** the JSON envelope.

- `Content-Type: text/csv`
- `Content-Disposition: attachment; filename="pms-cycles.csv"`

Frontend: fetch as a blob with the `Authorization` header and trigger a download. A plain `<a href>` will not send the bearer token.

Error responses (401, 403, 404) are still JSON.

---

### 3.3 Get one cycle (screens 1.2–1.5 edit / view)

`GET /api/v1/pms/pms-cycle/get/cycle/{cycleId}`

Any authenticated user. Returns `404 PMS_CYCLE_NOT_FOUND` if the cycle is not in the user's organisation.

**Path:** `cycleId` (UUID)

**Response `data`:** a `PmsCycleResponse`

```json
{
  "id": "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
  "cycle_code": "PMS-2526-A",
  "status": "draft",
  "created_on": "2026-01-10",
  "published_on": null,
  "applicable_to": null,
  "basic": {
    "name": "Annual Appraisal FY 2025-26",
    "description": "Year-end appraisal",
    "type": "annual",
    "period_start": "2025-04-01",
    "period_end": "2026-03-31"
  },
  "stages": [
    { "stage": "goal_setting", "start_date": "2025-04-01", "end_date": "2025-04-30", "notify": true }
  ],
  "applicability": {
    "all_plants": true,
    "plant_ids": [],
    "all_departments": true,
    "department_ids": [],
    "goal_setting": { "employment_types": ["permanent", "probationary", "trainee"], "min_service_months": 0 },
    "appraisal": { "employment_types": ["permanent"], "min_service_months": 12, "service_as_on": "2026-04-30" },
    "exclude_notice_period": false
  },
  "finalize": {
    "rating_scale_id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "notify_managers": true,
    "notify_employees": true,
    "notify_hod": false,
    "notify_hr": true
  },
  "current_step": 2,
  "completed_step": 1
}
```

| Field | Notes |
|---|---|
| `status` | `draft`, `active`, `closed`, `cancelled` |
| `applicable_to` | `"All Plants"` or `null` |
| `stages` | Only the stages saved so far. May be empty. |
| `applicability.allPlants` etc. | Booleans are always present (`false` if never set). |
| `finalize` | `null` if not saved yet. Check for null before reading inner fields. |
| `current_step` / `completed_step` | Where the user is in the wizard. Use these to restore progress. |

---

### 3.4 Create cycle (screen 1.2 "Next")

`POST /api/v1/pms/pms-cycle/create/cycle` — **write**

Creates a **DRAFT** cycle. The server generates `cycle_code` as `PMS-{FY start yy}{FY end yy}-{A|M|C}` (A = annual, M = mid_year, C = custom).

**Request body**

```json
{
  "basic": {
    "name": "Annual Appraisal FY 2025-26",
    "description": "Year-end appraisal",
    "type": "annual",
    "period_start": "2025-04-01",
    "period_end": "2026-03-31"
  },
  "stages": [],
  "applicability": null,
  "finalize": null,
  "current_step": 2,
  "completed_step": 1
}
```

| Field | Required | Notes |
|---|---|---|
| `basic` | **yes** | See `CycleBasic` |
| `stages` | no | Array of `CycleStage`. Can be omitted on create. |
| `applicability` | no | `CycleApplicability` |
| `finalize` | no | `CycleFinalize` |
| `current_step` | no | 1–4 |
| `completed_step` | no | 0–4 |

Stages, applicability and finalize are **not** checked for completeness here. That happens on publish.

**Response:** `201 Created`, `data` = `PmsCycleResponse` (same as §3.3).

**Errors:** `403` no permission · `422 VALIDATION_ERROR` (missing `basic`, bad type, end before start, etc.)

---

### 3.5 Update cycle (screens 1.2–1.5 Next / Previous)

`PUT /api/v1/pms/pms-cycle/update/cycle/{cycleId}` — **write**

Saves the whole wizard state for a DRAFT or ACTIVE cycle. Send the same body shape as create.

**Replacement rules**

- `basic` is always replaced.
- `stages`, `applicability`, `finalize`: if **supplied**, replace what is stored. If **omitted** (`null` / missing), they are left unchanged.
- Because a supplied `stages` list **replaces** the old list, always send the full list.

**Errors:** `409 PMS_CYCLE_NOT_EDITABLE` for CLOSED or CANCELLED cycles · `404 PMS_CYCLE_NOT_FOUND` · `422` validation.

**Response:** `200 OK`, `data` = `PmsCycleResponse`.

---

### 3.6 Publish cycle (screen 1.5 "Publish Cycle")

`POST /api/v1/pms/pms-cycle/publish/cycle/{cycleId}` — **write**

No request body.

Moves a complete DRAFT cycle to **ACTIVE** and stamps `published_on`. Publishing requires all of the following:

- A rating scale selected (`finalize.rating_scale_id`), and that scale must be active
- At least one plant (or `all_plants = true`)
- At least one department, unless `all_departments = true`
- `min_service_months` set
- `service_as_on` set
- At least one employment type
- Start **and** end dates for **all nine** stages

If any of these are missing, the response is `422 PMS_CYCLE_PUBLISH_VALIDATION_FAILED` and `detail` lists every problem separated by `; `. Show it so the user can fix them.

Also: publishing a non-DRAFT cycle returns `409 PMS_CYCLE_NOT_PUBLISHABLE`.

**Response:** `200 OK`, `data` = `PmsCycleActivationResponse` (see §3.7).

> Eligibility processing and notifications are not implemented yet. Publishing only changes the status.

---

### 3.7 Get activation (screen 1.6)

`GET /api/v1/pms/pms-cycle/get/cycle/{cycleId}/activation`

Returns the published cycle and what was recorded about notifications and eligibility.

**Errors:** `409 PMS_CYCLE_NOT_PUBLISHED` for a DRAFT cycle · `404 PMS_CYCLE_NOT_FOUND`.

**Response `data`**

```json
{
  "cycle": { "...same as PmsCycleResponse..." },
  "published_on": "2026-02-01",
  "notifications": [],
  "eligibility": null
}
```

| Field | Notes |
|---|---|
| `published_on` | `YYYY-MM-DD` |
| `notifications` | Array of `{ audience, sent, status, sent_on }`. **Empty today**, because nothing writes notifications yet. |
| `notifications[].audience` | `hod_reviewers`, `reporting_managers`, `employees`, `hr` |
| `notifications[].status` | `pending`, `sent`, `failed`, `partial` |
| `eligibility` | `null` until an eligibility run exists. When present: `{ status, started_on, completed_on, total_eligible, excluded_probation, excluded_notice_period, excluded_min_service }`. `status` is `started`, `completed` or `failed`. |

Show "Not started" / empty states for these fields. Do not invent counts.

---

### 3.8 Cancel cycle

`POST /api/v1/pms/pms-cycle/cancel/cycle/{cycleId}` — **write**

No request body. Cancels a DRAFT or ACTIVE cycle and sets `status = cancelled`.

**Errors:** `409 PMS_CYCLE_NOT_CANCELLABLE` for CLOSED or CANCELLED cycles.

**Response:** `200 OK`, `data` = `PmsCycleResponse`.

---

## 4. Goal templates — `/pms-goal-template`

Covers screens 2.1 to 2.4. Wizard: **Basic info (2.2) → KRA & KPI (2.3) → Competencies (2.4)**.

A template starts as `draft`. It becomes `active` only when its KRA/KPI step **and** its competency step are both complete.

**`GoalTemplateBasic`** (screen 2.2)

| Field | Type | Required | Rules |
|---|---|---|---|
| `financial_year` | string | yes | max 20, e.g. `"2025-26"` |
| `template_name` | string | yes | max 200 |
| `description` | string | no | max 2000 |
| `department_id` | string | yes | Sentrifugo department id (24-character string) |
| `role_id` | string | yes | Sentrifugo role id (24-character string) |
| `plant_id` | string | no | Sentrifugo plant id (24-character string) |
| `effective_from` | date | yes | `YYYY-MM-DD` |
| `status` | string | no | `draft`, `active`, `inactive`. See notes below. |

Status rules for create and basic-info update:

- `inactive` is kept as `inactive`.
- Anything else, including `active`, is stored as `draft`.
- `active` is only accepted on **update** when the template is already complete. Otherwise it stays `draft`.

**Goal template response** (`PmsGoalTemplateResponse`), used by §4.2–4.5

```json
{
  "id": "a1b2c3d4-0000-4000-8000-000000000001",
  "financial_year": "2025-26",
  "template_name": "Plant Engineer – FY 2025-26",
  "description": "Standard engineer goals",
  "department_id": "…",
  "role_id": "…",
  "plant_id": "…",
  "effective_from": "2025-04-01",
  "status": "active",
  "kras": [
    {
      "kra_id": "…",
      "kra_name": "Production Excellence",
      "kpis": [
        {
          "kpi_id": "…",
          "kpi_name": "OEE %",
          "unit": "%",
          "weightage": 60.00,
          "target_type": "individual",
          "expected_outcome": "Improve OEE",
          "evidence_required": "MES report"
        }
      ]
    }
  ],
  "competencies": [
    { "competency_id": "…", "name": "Communication", "category": "Behavioural", "weightage": 100.00 }
  ],
  "total_kpi_weightage": 100.00,
  "total_competency_weightage": 100.00
}
```

| Field | Notes |
|---|---|
| `department_id`, `role_id`, `plant_id` | Ids only. Department, role and plant **names** must be looked up in Sentrifugo. |
| `kras[].kpis[].expected_outcome`, `evidence_required` | Come from the KPI master. A template cannot override them. |
| `total_kpi_weightage`, `total_competency_weightage` | Running totals for the progress bar. |

---

### 4.1 List goal templates (screen 2.1)

`GET /api/v1/pms/pms-goal-template/get/goal-templates`

Any authenticated user. All query parameters are optional.

| Name | Type | Notes |
|---|---|---|
| `financial_year` | string | e.g. `2025-26` |
| `department_id` | string | |
| `plant_id` | string | |
| `search` | string | Searches template name |

**Response `data`:** array of

```json
{
  "id": "a1b2c3d4-0000-4000-8000-000000000001",
  "template_name": "Plant Engineer – FY 2025-26",
  "financial_year": "2025-26",
  "department_id": "…",
  "role_id": "…",
  "plant_id": "…",
  "effective_from": "2025-04-01",
  "status": "active"
}
```

Message: `"Found N goal templates"`. Not paginated.

---

### 4.2 Get a goal template (screens 2.2–2.4 edit / view)

`GET /api/v1/pms/pms-goal-template/get/goal-template/{templateId}`

**Path:** `templateId` (UUID). Returns `404 PMS_TEMPLATE_NOT_FOUND` if not found.

**Response `data`:** `PmsGoalTemplateResponse` (shape above).

Use it to load the wizard for edit: fill step 1 from the top-level fields, step 2 from `kras`, step 3 from `competencies`.

---

### 4.3 Create goal template (screen 2.2 "Save and Next")

`POST /api/v1/pms/pms-goal-template/create/goal-template` — **write**

**Request body:** `GoalTemplateBasic` (see above). Example:

```json
{
  "financial_year": "2025-26",
  "template_name": "Plant Engineer – FY 2025-26",
  "description": "Standard engineer goals",
  "department_id": "…",
  "role_id": "…",
  "plant_id": "…",
  "effective_from": "2025-04-01",
  "status": "draft"
}
```

**Response:** `201 Created`, `data` = `PmsGoalTemplateResponse` with empty `kras` and `competencies`. Keep the returned `id` for steps 2.3 and 2.4.

**Errors:** `422 VALIDATION_ERROR` · `403` no permission.

---

### 4.4 Update goal template basic info (screen 2.2)

`PUT /api/v1/pms/pms-goal-template/update/goal-template/{templateId}` — **write**

Same body as §4.3. It **replaces** all basic fields; send the full object.

**Response:** `200 OK`, `data` = `PmsGoalTemplateResponse` (the `kras` and `competencies` are kept).

If the stored template is `active` but no longer complete, the server sets it back to `draft`.

---

### 4.5 Save KRA and KPI configuration (screen 2.3)

`PUT /api/v1/pms/pms-goal-template/update/goal-template/{templateId}/kra-kpi` — **write**

Replaces **all** KRA and KPI selections of the template in one transaction.

**Request body**

```json
{
  "save_as_draft": false,
  "kras": [
    {
      "kra_id": "…",
      "kpis": [
        { "kpi_id": "…", "weightage": 60.00, "target_type": "individual" },
        { "kpi_id": "…", "weightage": 40.00 }
      ]
    }
  ]
}
```

| Field | Type | Required | Rules |
|---|---|---|---|
| `save_as_draft` | boolean | no | `true` = "Save as Draft": incomplete data allowed. `false` or omitted = "Save and Next": full validation. |
| `kras` | array | yes (may be empty when `save_as_draft` is `true`) | Each KRA once |
| `kras[].kra_id` | UUID | yes | Must be an **active** KRA |
| `kras[].kpis` | array | yes | Each KPI once across the template |
| `kpis[].kpi_id` | UUID | yes | Must be an **active** KPI that belongs to its KRA |
| `kpis[].weightage` | decimal | yes | `> 0` and `≤ 100` |
| `kpis[].target_type` | string | no | `individual` or `common`. Defaults to the KPI master's value. |

**Rules when `save_as_draft` is false**

- Every selected KRA needs at least one KPI.
- Total KPI weightage must equal exactly `100` (or `422 PMS_TEMPLATE_INCOMPLETE`: `KPI weightage must total 100 (got 90).`).
- At least one KRA must be selected (`PMS_TEMPLATE_INCOMPLETE`).

When `save_as_draft` is `true`, the total can be anything up to `100`. Over 100 is always rejected.

**Response:** `200 OK`, `data` = `PmsGoalTemplateResponse`.

**Errors:** `404 PMS_TEMPLATE_NOT_FOUND` · `422 PMS_KRA_NOT_FOUND` / `PMS_KPI_NOT_FOUND` (inactive or unknown id) · `422 VALIDATION_ERROR` (KPI not under its KRA, duplicate KRA/KPI) · `422 PMS_TEMPLATE_INCOMPLETE`.

---

### 4.6 Save competency configuration (screen 2.4)

`PUT /api/v1/pms/pms-goal-template/update/goal-template/{templateId}/competencies` — **write**

Replaces all competencies of the template and their weightages in one transaction.

**Request body**

```json
{
  "competencies": [
    { "competency_id": "…", "weightage": 50.00 },
    { "competency_id": "…", "weightage": 50.00 }
  ]
}
```

| Field | Type | Required | Rules |
|---|---|---|---|
| `competencies` | array | yes | May be empty to clear. Each competency once. |
| `competency_id` | UUID | yes | Must be an **active** competency |
| `weightage` | decimal | yes | `> 0` and `≤ 100` |

When the list is not empty, weightages **must total exactly 100**, otherwise `422 PMS_TEMPLATE_INCOMPLETE` (`Competency weightage must total 100 (got N).`).

If the template now has complete KRA/KPI data and competencies, it becomes `active`.

**Response:** `200 OK`, `data` = `PmsGoalTemplateResponse`.

---

## 5. Master data — `/pms-master`

Three groups: **KRA** (screens 2.5, 2.6), **KPI** (2.7, 2.8), **Competency** (2.9).

### 5.1 KRA

**`KraRequest`**

| Field | Type | Required | Rules |
|---|---|---|---|
| `name` | string | yes | 1–200 chars. Unique per organisation, case-insensitive. |
| `status` | string | no | `active` or `inactive`. Default `active` on create. On update, omitting it leaves the status as is. |

**`KraResponse`**

```json
{ "id": "…", "name": "Production Excellence", "status": "active" }
```

#### 5.1.1 List KRAs (screen 2.5, dropdowns)

`GET /api/v1/pms/pms-master/get/kras` — any authenticated user.

**Response `data`:** array of `KraResponse`, oldest first. Message: `"Found N KRAs"`.

#### 5.1.2 Create KRA (screen 2.6)

`POST /api/v1/pms/pms-master/create/kra` — **write**

Body: `KraRequest`. **Response:** `201 Created`, `data` = `KraResponse`.

**Errors:** `409 PMS_KRA_DUPLICATE` if the name exists.

#### 5.1.3 Update KRA (screen 2.5 edit)

`PUT /api/v1/pms/pms-master/update/kra/{kraId}` — **write**

Body: `KraRequest` (renames and/or changes status). **Response:** `200 OK`, `data` = `KraResponse`.

**Errors:** `404 PMS_KRA_NOT_FOUND` · `409 PMS_KRA_DUPLICATE` if the new name is taken.

#### 5.1.4 Delete KRA (screen 2.5 delete)

`DELETE /api/v1/pms/pms-master/delete/kra/{kraId}` — **write**

No body. **Response:** `200 OK`, `data: null`, message `"KRA deleted successfully"`.

**Errors:** `409 PMS_KRA_IN_USE` if the KRA still has KPIs or is used by a goal template. Remove those first.

---

### 5.2 KPI

**`KpiRequest`**

| Field | Type | Required | Rules |
|---|---|---|---|
| `kra_id` | UUID | yes | Parent KRA. Must exist. |
| `name` | string | yes | 1–200 chars. Unique per KRA. |
| `unit` | string | yes | 1–50 chars. Pick from the list in §5.2.2. |
| `target_type` | string | yes | `individual` or `common` |
| `expected_outcome` | string | no | max 500 |
| `evidence_required` | string | no | max 500 |
| `status` | string | no | `active` or `inactive`. Default `active`. |

**`KpiResponse`**

```json
{
  "id": "…",
  "kra_id": "…",
  "kra_name": "Production Excellence",
  "name": "OEE %",
  "unit": "%",
  "target_type": "individual",
  "expected_outcome": "Improve OEE",
  "evidence_required": "MES report",
  "status": "active"
}
```

#### 5.2.1 List KPIs (screen 2.7)

`GET /api/v1/pms/pms-master/get/kpis` — any authenticated user.

**Response `data`:** array of `KpiResponse`. Message `"Found N KPIs"`.

#### 5.2.2 List KPI units (screen 2.8 unit dropdown)

`GET /api/v1/pms/pms-master/get/kpi-units` — any authenticated user. Fixed list, not from the database.

**Response `data`:** `["TPD", "kcal/kg", "%", "kWh/t", "Count", "Months", "MPa"]`

#### 5.2.3 Create KPI (screen 2.8)

`POST /api/v1/pms/pms-master/create/kpi` — **write**

Body: `KpiRequest`. **Response:** `201 Created`, `data` = `KpiResponse`.

**Errors:** `404 PMS_KRA_NOT_FOUND` (kra_id unknown) · `409 PMS_KPI_DUPLICATE` if the name already exists under this KRA · `422 VALIDATION_ERROR` if `target_type` is not `individual` or `common`.

#### 5.2.4 Update KPI (screen 2.7 edit)

`PUT /api/v1/pms/pms-master/update/kpi/{kpiId}` — **write**

Body: `KpiRequest`. It **replaces** every field, including `kra_id`. **Response:** `200 OK`, `data` = `KpiResponse`.

**Errors:** `404 PMS_KPI_NOT_FOUND` / `PMS_KRA_NOT_FOUND` · `409 PMS_KPI_DUPLICATE` under the KRA.

#### 5.2.5 Delete KPI (screen 2.7 delete)

`DELETE /api/v1/pms/pms-master/delete/kpi/{kpiId}` — **write**

**Response:** `200 OK`, `data: null`. **Errors:** `409 PMS_KPI_IN_USE` if a goal template uses it.

---

### 5.3 Competency (screen 2.9)

Screen 2.9 has no add button. Competencies are created through the endpoint below.

**`CompetencyRequest`**

| Field | Type | Required | Rules |
|---|---|---|---|
| `name` | string | yes | 1–200 chars. Unique per organisation. |
| `category` | string | yes | 1–100 chars, e.g. `Behavioural` |
| `status` | string | no | `active` or `inactive`. Default `active`. |

**`CompetencyResponse`**

```json
{ "id": "…", "name": "Communication", "category": "Behavioural", "status": "active" }
```

#### 5.3.1 List competencies (screen 2.9, screen 2.4 picker)

`GET /api/v1/pms/pms-master/get/competencies`

| Name | Type | Notes |
|---|---|---|
| `search` | string | Case-insensitive substring match on name. Optional. |

**Response `data`:** array of `CompetencyResponse`. Message `"Found N competencies"`.

#### 5.3.2 Create competency

`POST /api/v1/pms/pms-master/create/competency` — **write**

Body: `CompetencyRequest`. **Response:** `201 Created`, `data` = `CompetencyResponse`.

**Errors:** `409 PMS_COMPETENCY_DUPLICATE` if the name already exists.

#### 5.3.3 Update competency (screen 2.9 edit)

`PUT /api/v1/pms/pms-master/update/competency/{competencyId}` — **write**

Body: `CompetencyRequest`. **Response:** `200 OK`, `data` = `CompetencyResponse`.

**Errors:** `404 PMS_COMPETENCY_NOT_FOUND` · `409 PMS_COMPETENCY_DUPLICATE`.

#### 5.3.4 Delete competency (screen 2.9 delete)

`DELETE /api/v1/pms/pms-master/delete/competency/{competencyId}` — **write**

**Response:** `200 OK`, `data: null`. **Errors:** `409 PMS_COMPETENCY_IN_USE` if a goal template uses it.

---

## 6. Rating scales — `/pms-rating-scale`

Covers the dropdown on screen 1.5 and the editor on screen 2.10.

**`RatingScaleRequest`**

| Field | Type | Required | Rules |
|---|---|---|---|
| `name` | string | yes | 1–150 chars |
| `description` | string | no | |
| `status` | string | no | `active` or `inactive`. Default `active`. |
| `is_default` | boolean | no | Setting `true` clears the default on the organisation's other scales. |
| `show_definitions_to_employees` | boolean | no | |
| `levels` | array | yes | At least one level |

**`RatingScaleLevel`** (inside `levels`)

| Field | Type | Required | Rules |
|---|---|---|---|
| `rating_value` | integer | yes | ≥ 1. Unique within the scale. |
| `label` | string | yes | 1–100 chars, e.g. `Exceeds` |
| `definition` | string | no | |
| `minimum_score` | decimal | yes | 0.00 – 999.99 |
| `maximum_score` | decimal | yes | 0.00 – 999.99. Must be ≥ `minimum_score`. |
| `colour_code` | string | no | Hex `#RRGGBB`, e.g. `#22C55E` |
| `display_order` | integer | no | |

Score ranges of different levels **must not overlap**.

**`RatingScaleResponse`**

```json
{
  "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "name": "5-point scale",
  "description": "Default appraisal scale",
  "status": "active",
  "is_default": true,
  "show_definitions_to_employees": true,
  "levels": [
    {
      "rating_value": 5,
      "label": "Outstanding",
      "definition": "Consistently exceeds expectations",
      "minimum_score": 90.00,
      "maximum_score": 100.00,
      "colour_code": "#22C55E",
      "display_order": 1
    }
  ]
}
```

Levels are returned **highest rating first**.

### 6.1 List rating scales

`GET /api/v1/pms/pms-rating-scale/get/rating-scales`

| Name | Type | Notes |
|---|---|---|
| `status` | string | Pass `active` to fill the cycle wizard dropdown (screen 1.5). Omit for all scales (screen 2.10). |

**Response `data`:** array of `RatingScaleResponse`. Message `"Found N rating scales"`.

### 6.2 Get one rating scale (screen 2.10)

`GET /api/v1/pms/pms-rating-scale/get/rating-scale/{scaleId}`

**Response `data`:** `RatingScaleResponse`. **Errors:** `404 PMS_RATING_SCALE_NOT_FOUND`.

### 6.3 Create rating scale

`POST /api/v1/pms/pms-rating-scale/create/rating-scale` — **write**

Body: `RatingScaleRequest`. **Response:** `201 Created`, `data` = `RatingScaleResponse`.

Errors: `422 VALIDATION_ERROR` for overlapping score ranges, duplicate `rating_value`, or `minimum_score > maximum_score`.

### 6.4 Save rating scale (screen 2.10 "Save Scale")

`PUT /api/v1/pms/pms-rating-scale/update/rating-scale/{scaleId}` — **write**

Body: `RatingScaleRequest`. Replaces name, status, flags, and **syncs levels by `rating_value`** (send the full level list). **Response:** `200 OK`, `data` = `RatingScaleResponse`.

Errors: `404 PMS_RATING_SCALE_NOT_FOUND` · `422` for the same level rules as create.

---

## 7. Full endpoint index

| # | Method | Path (after `/api/v1/pms`) | Write? | Success | Section |
|---|---|---|---|---|---|
| 1 | GET | `/pms-cycle/get/cycles` | | 200 | §3.1 |
| 2 | GET | `/pms-cycle/get/cycles/export` | | 200 (CSV) | §3.2 |
| 3 | GET | `/pms-cycle/get/cycle/{cycleId}` | | 200 | §3.3 |
| 4 | POST | `/pms-cycle/create/cycle` | ✔ | 201 | §3.4 |
| 5 | PUT | `/pms-cycle/update/cycle/{cycleId}` | ✔ | 200 | §3.5 |
| 6 | POST | `/pms-cycle/publish/cycle/{cycleId}` | ✔ | 200 | §3.6 |
| 7 | GET | `/pms-cycle/get/cycle/{cycleId}/activation` | | 200 | §3.7 |
| 8 | POST | `/pms-cycle/cancel/cycle/{cycleId}` | ✔ | 200 | §3.8 |
| 9 | GET | `/pms-goal-template/get/goal-templates` | | 200 | §4.1 |
| 10 | GET | `/pms-goal-template/get/goal-template/{templateId}` | | 200 | §4.2 |
| 11 | POST | `/pms-goal-template/create/goal-template` | ✔ | 201 | §4.3 |
| 12 | PUT | `/pms-goal-template/update/goal-template/{templateId}` | ✔ | 200 | §4.4 |
| 13 | PUT | `/pms-goal-template/update/goal-template/{templateId}/kra-kpi` | ✔ | 200 | §4.5 |
| 14 | PUT | `/pms-goal-template/update/goal-template/{templateId}/competencies` | ✔ | 200 | §4.6 |
| 15 | GET | `/pms-master/get/kras` | | 200 | §5.1.1 |
| 16 | POST | `/pms-master/create/kra` | ✔ | 201 | §5.1.2 |
| 17 | PUT | `/pms-master/update/kra/{kraId}` | ✔ | 200 | §5.1.3 |
| 18 | DELETE | `/pms-master/delete/kra/{kraId}` | ✔ | 200 | §5.1.4 |
| 19 | GET | `/pms-master/get/kpis` | | 200 | §5.2.1 |
| 20 | GET | `/pms-master/get/kpi-units` | | 200 | §5.2.2 |
| 21 | POST | `/pms-master/create/kpi` | ✔ | 201 | §5.2.3 |
| 22 | PUT | `/pms-master/update/kpi/{kpiId}` | ✔ | 200 | §5.2.4 |
| 23 | DELETE | `/pms-master/delete/kpi/{kpiId}` | ✔ | 200 | §5.2.5 |
| 24 | GET | `/pms-master/get/competencies` | | 200 | §5.3.1 |
| 25 | POST | `/pms-master/create/competency` | ✔ | 201 | §5.3.2 |
| 26 | PUT | `/pms-master/update/competency/{competencyId}` | ✔ | 200 | §5.3.3 |
| 27 | DELETE | `/pms-master/delete/competency/{competencyId}` | ✔ | 200 | §5.3.4 |
| 28 | GET | `/pms-rating-scale/get/rating-scales` | | 200 | §6.1 |
| 29 | GET | `/pms-rating-scale/get/rating-scale/{scaleId}` | | 200 | §6.2 |
| 30 | POST | `/pms-rating-scale/create/rating-scale` | ✔ | 201 | §6.3 |
| 31 | PUT | `/pms-rating-scale/update/rating-scale/{scaleId}` | ✔ | 200 | §6.4 |

---

## 8. Recommended frontend flows

**Appraisal cycle wizard**

1. `POST create/cycle` with `basic` only → store `id`, go to step 2.
2. On each "Next" or "Previous", `PUT update/cycle/{id}` with the full body and the new `current_step` / `completed_step`.
3. Load the rating-scale dropdown with `GET get/rating-scales?status=active`.
4. On "Publish", `POST publish/cycle/{id}`. On `422 PMS_CYCLE_PUBLISH_VALIDATION_FAILED`, show `detail` and stay on the wizard.
5. Open the activation page with `GET get/cycle/{id}/activation`.

**Goal template wizard**

1. `POST create/goal-template` → store `id`.
2. `PUT update/goal-template/{id}/kra-kpi` (use `save_as_draft: true` for drafts).
3. `PUT update/goal-template/{id}/competencies`. The template becomes `active` automatically if complete.
4. Populate KRA and KPI dropdowns from `get/kras` and `get/kpis`; competency picker from `get/competencies`.

**Deleting master data:** show the confirm dialog, then call `DELETE`. On `409 *_IN_USE`, tell the user which template uses it. The API does not return the template names.

---

## 9. Known limitations (plan the UI around these)

- **Names are not returned for ids.** Department, role and plant names must come from Sentrifugo. Plant names in the cycle list are `null` unless "All Plants".
- **Activation notifications and eligibility are empty** until those features are built.
- **Cycle list is paginated; goal template list, master lists and rating scales are not.** Load them in full.
- **Stages and applicability are optional on draft save.** Validation happens only on publish.
- **Stage `stage` values** must match the nine values in §2.2 exactly (lowercase).

---

## 10. Manager screens after goal setting (3.6 – 3.14)

All paths are after `/api/v1/pms`. Every request and response uses snake_case and the envelope in §1.3. Ratings are whole numbers 1–5. Every manager call checks that the employee is one of the caller's direct reports (`403 PMS_NOT_IN_TEAM` otherwise). IAM permission codes are in the `performance_management` module.

### 10.1 Goal status and send to HOD — `/pms-goal-assignment` (screen 3.6)

`GET /get/team?financial_year=2026-27` (`team_goals`) now also returns `template_name` and two extra `goal_status` values:

| `goal_status` | Meaning |
|---|---|
| `returned_by_hod` | The HOD sent the sheet back. `hod_remarks` is on `get/employee-targets`. |
| `transferred_in` | The employee moved into the team mid-year and period 2 KRAs are not sent yet (§10.2). |

`GET /get/employee-targets` also returns `employee_name`, `emp_code`, `role_name`, `manager_name` (manager view only) and per KPI `target_type` (`common` / `individual`) and `history` (up to three previous years: `financial_year`, `target`, `actual`).

`POST /assign-template` (`team_goals`) — body `{ "financial_year": "2026-27", "designation_id": "…", "template_id": "…" }`. "Create & assign template" (screen 3.2): gives the template named by `template_id` (the role's active template; the team list returns its id) to every direct report with that role who has none; employees who already have a goal sheet keep theirs. Returns `{ designation_id, template_id, template_name, assigned, already_assigned }`. `404 PMS_TEMPLATE_NOT_FOUND` for an unknown template; `422 PMS_TEMPLATE_ROLE_MISMATCH` when it is not the role's active template for the year; `422 PMS_ROLE_NOT_IN_TEAM` when none of your reports has the role. Safe to repeat.

`GET /get/team` rows carry `template_id` and `template_name`: the template assigned to the employee, or (until assigned) the role's active template, plus `template_assigned` (boolean). Saving targets (`PUT /update/employee-targets`, `POST /send`) needs the template assigned first: `409 PMS_TEMPLATE_NOT_ASSIGNED`. An assigned employee with no targets saved yet is still `not_started`.

**Acknowledge / request change (employee, `my_goals`).** `POST /acknowledge` `{ financial_year, comment? }` (comment up to 500 characters, kept and returned as `acknowledge_comment`) moves `sent_to_employee` to `acknowledged`. `POST /request-change` `{ financial_year, kpi_id, proposed_target?, reason }` (reason required, up to 1000 characters; `kpi_id` must be on the employee's own sheet, else `422 PMS_KPI_NOT_IN_TEMPLATE`; proposed target not negative) moves it to `change_requested`. Both are only allowed while the sheet is `sent_to_employee` (`422 PMS_INVALID_GOAL_STATE`) and only ever touch the caller's own sheet. The employee may ask again after the manager resends. While a sheet is `sent_to_employee`, `acknowledged`, `with_hod` or `approved` the manager cannot save or send targets (`409 PMS_INVALID_GOAL_STATE`), and `send-to-hod` accepts acknowledged sheets only.

`POST /send-to-hod` (`team_goals`) — body `{ "financial_year": "2026-27", "employee_user_ids": ["…"], "remarks": "…" }`. Moves each acknowledged sheet to `with_hod` and returns the ids sent. `remarks` is the manager's optional note to the HOD (up to 500 characters, `422 VALIDATION_ERROR` beyond that); it is kept on the sheet trail. `422 PMS_INVALID_GOAL_STATE` if any sheet is not `acknowledged`; `422 PMS_TARGETS_INVALID` if the sheet is incomplete (a target missing or the weights not totalling 100).

**Only revised KRAs go to the HOD.** A KRA is revised when its current `target` differs from `original_target`, the value the manager first assigned. The HOD queue and `get/hod-review` list only those KRAs; unrevised KRAs are never shown to the HOD. A sheet with no revised KRA has nothing for the HOD to decide: it is approved when the employee acknowledges (`approved`, trail event "Goals approved: no KRA was revised, so nothing needed the HOD"), and `send-to-hod` on such a sheet approves it instead of queuing it.

**The send-to-HOD loop.** Only a sheet the manager has sent (`with_hod`) is in the HOD's queue and can be approved or returned; an `acknowledged` sheet waits for the manager (`422 PMS_NOT_AWAITING_HOD` otherwise). The HOD approves it (`approved`) or returns it with remarks (`422 PMS_REMARKS_REQUIRED` without them), which puts it back with the manager as `returned_by_hod`. The manager revises and resends (`POST /send`, per-target `remarks`), the employee acknowledges or asks for a change again, and the manager sends it to the HOD once more; every round stays on the trail. A resend clears the HOD's remarks from the sheet, so a later draft is not shown as returned.

### 10.2 Transferred employees — `/pms-goal-transfer` (screens 3.6 banner, 3.8)

| Method | Path | Permission | Purpose |
|---|---|---|---|
| GET | `/get/notices?financial_year=` | `team_goals` | Transfers into the team still needing period 2 KRAs |
| GET | `/get/transfer-sheet?employee_user_id=&financial_year=` | `team_goals` | Frozen period 1 rows and editable period 2 rows |
| PUT | `/update/transfer-sheet` | `team_goals` | Save draft (replaces period 2 rows) |
| POST | `/send/transfer-sheet` | `team_goals` | Validate and send to the employee |
| POST | `/create/transfer` | `pms_cycle` | HR records a transfer, with the earlier KRAs as period 1 |

Period 2 rows start from the active template of the employee's role (`source: "template"`). `PUT` / `POST` body: `employee_user_id`, `financial_year`, `reason`, `remarks`, `kras[]` of `{ kra_name, weight, target, unit, source, source_label }` (period 2 only).

Send is refused with `422 PMS_TRANSFER_INVALID` unless period 2 weights total 100, every target is set, a `reason` is given and `remarks` has 10+ characters. A sent sheet cannot be edited (`422 PMS_INVALID_GOAL_STATE`). `404 PMS_TRANSFER_NOT_FOUND`, `409 PMS_TRANSFER_EXISTS`.

### 10.3 Team progress — `/pms-progress` (screen 3.9)

`GET /get/team?financial_year=` (`team_progress`) returns one row per direct report: `locked`, `kras_on_track`, `kras_total`, `attention[]` (`kra_name`, `rag`), `achievement`, `comment`, `overall_rag`. A row is `locked` until the HOD approves the goals.

`PUT /update/my-progress` (`progress`) — the employee reports actuals: `{ financial_year, period: "current" | "h1" | "fy", comment, entries: [{ kpi_id, actual, attachment_name }] }`. `409 PMS_GOALS_NOT_APPROVED` before approval.

Achievement is `actual / target × 100`; `rag` is `on_track` from 95%, `at_risk` from 90%, else `off_track`. A zero target is met only by a zero actual. There is no "lower is better" direction on the KPI master yet.

### 10.4 Mid-year review and target revision — `/pms-mid-year` (screens 3.10 – 3.12)

All `mid_year_review`; the goals must be HOD approved (`409 PMS_GOALS_NOT_APPROVED`).

| Method | Path | Purpose |
|---|---|---|
| GET | `/get/team?financial_year=` | Rows: `role`, `h1_achievement`, `rag`, `status` (`not_started`, `completed_no_revision`, `revision_requested`) |
| GET | `/get/review?employee_user_id=&financial_year=` | KRAs with `target`, `h1_actual`, `rag`, plus saved notes and decision |
| POST | `/submit/review` | `{ employee_user_id, financial_year, discussion_notes, revision_required }` |
| GET | `/get/revision-request?employee_user_id=&financial_year=` | Every KPI with its locked `weight` and `current_target` |
| POST | `/submit/revision-request` | `{ employee_user_id, financial_year, attachment_name, rows: [{ kpi_id, revised_target, reason }] }` |

`revision_required: false` completes the review. `true` needs `discussion_notes` of 10+ characters (`422 PMS_NOTES_REQUIRED`) and keeps the review open until the revision request is sent. The revision request takes only changed targets, each with a reason of 5+ characters (`422 PMS_REVISION_INVALID`), and moves the review to `revision_requested`. `409 PMS_REVISION_PENDING` if one is already with the HOD. HOD approval of the revision is not built yet.

### 10.5 Appraisal — `/pms-appraisal` (screens 3.13 – 3.14)

| Method | Path | Permission | Purpose |
|---|---|---|---|
| GET | `/get/team?financial_year=` | `team_appraisal` | `self_status`, `self_score`, `manager_status` (`waiting`, `pending`, `draft`, `submitted_to_hod`) |
| GET | `/get/manager-appraisal?employee_user_id=&financial_year=` | `team_appraisal` | KRAs, manager competencies, HR competencies (view only) |
| PUT | `/update/manager-appraisal` | `team_appraisal` | Save draft; ratings may be empty |
| POST | `/submit/manager-appraisal` | `team_appraisal` | Submit to the HOD |
| PUT | `/update/self-appraisal` | `self_appraisal` | The employee saves (`submit: false`) or submits (`submit: true`) |

Save and submit body: `{ employee_user_id, financial_year, kras: [{ kpi_id, rating, comment }], competencies: [{ id, rating, comment }], recommended_rating, comments }`.

`get/manager-appraisal` returns `409 PMS_SELF_APPRAISAL_PENDING` until the employee has submitted. Submit is refused with `422 PMS_APPRAISAL_INVALID` unless every KRA and manager competency is rated, a `recommended_rating` is given, and any rating two or more points from the self rating has a comment of 5+ characters. Competencies whose category starts with "HR" are rated by Plant HR and are returned under `hr_competencies` with only the self rating.

### 10.5a HR competency rating — `/pms-hr-appraisal` (screens 1.7 – 1.8)

Corporate HR rates the HR competencies once the manager has submitted the appraisal. Every call needs `rating_normalization` (the code Corporate HR already holds; no new IAM permission).

| Method | Path | Purpose |
|---|---|---|
| GET | `/get/queue?financial_year=&department_id=&status=&search=` | Employees whose manager appraisal is in. `status` is `pending`, `draft`, `rated` or `all`. Rows: `employee_user_id`, `employee_name`, `emp_code`, `department_id`, `functional_head`, `hod_rated_on`, `status`, `sent_to_calibration` |
| GET | `/get/employee?employee_user_id=&financial_year=` | `hr_competencies` (rate now: `self_rating`, `hr_rating`, `comment`), `manager_competencies` (view only: self, manager, HOD), `remarks`, `locked` (true once sent to calibration). `409 PMS_MANAGER_APPRAISAL_PENDING` until the manager has submitted |
| PUT | `/update/hr-ratings` | Save draft; ratings may be empty |
| POST | `/submit/hr-ratings` | Every HR competency must be rated 1-5 (`422 PMS_HR_RATING_INCOMPLETE`) |
| POST | `/send/calibration` | `{ financial_year, employee_user_ids }`. Only rated employees (`409 PMS_HR_RATING_PENDING`). Returns `{ sent }` |

Save and submit body: `{ employee_user_id, financial_year, competencies: [{ id, rating, comment }], remarks }`.

### 10.5b Calibration — `/pms-calibration` (screen 1.9)

Needs `rating_normalization`.

| Method | Path | Purpose |
|---|---|---|
| GET | `/get/calibration?financial_year=` | `distribution` (per rating: `current_percent`, `guideline_percent`, `hr_percent`), `employees` (manager, HOD, `hr_competency_avg`, `current_rating`, `calibrated_rating`, `reason`), `published` |
| PUT | `/update/calibration` | `{ financial_year, distribution: [{ rating, hr_percent }], employees: [{ employee_user_id, calibrated_rating, reason }] }`. `409 PMS_CALIBRATION_PUBLISHED` once published |
| POST | `/publish/calibration` | `{ financial_year }`. `422 PMS_CALIBRATION_TOTAL` unless HR's targets total 100%; `422 PMS_CALIBRATION_INVALID` when a changed rating has no reason of 5+ characters |

`current_rating` is the HOD rating, else the manager's recommended rating. The guideline is the standard 5 / 20 / 60 / 10 / 5 split for ratings 5 to 1.

### 10.6 Goal template status and duplicate — `/pms-goal-template` (screen 2.1)

`PUT /update/goal-template/{templateId}/status` (`goal_template`) — body `{ "status": "draft" | "active" | "inactive" }`, returns the template. Activating needs a complete template (`409 PMS_TEMPLATE_INCOMPLETE`) and no other active template for the same role and year (`409 PMS_TEMPLATE_ROLE_CONFLICT`).

`POST /duplicate/goal-template/{templateId}` (`goal_template`) — creates a draft named `"<name> (Copy)"` in the same year with the same KRAs, KPIs and competencies. Returns `201` and the new template.

### 10.7 Not built yet

- **Eligibility preview** for the cycle wizard (`POST /pms-cycle/preview-eligibility`) needs an employee-directory query from IAM that does not exist as an RPC yet.
- **Acknowledgement deadline** (`acknowledge_by`) is not returned.
- **Period 2 appraisal** for a transferred employee (rating each period separately) and **HOD approval** of target revisions and appraisals are not built.
- The employee's own screens (`progress`, `self_appraisal`) have only the minimal write endpoints above.

### 10.8 My goals — `/pms-goal-assignment` (screens 4.1, 4.5)

`GET /get/my-goals?financial_year=2026-27` (`my_goals`) returns the signed-in employee's own sheet. `404 PMS_GOALS_NOT_FOUND` until the manager has saved targets.

**Change-request rules.** Per KPI: `original_target` is the first target the manager sent and never changes (show it in the *Target* column); `revised_target` is the manager's answer to a change request (show it only in *Revised Target*); `revision_count` is the permanent number of requests made. Only one request can be open per KPI: while the sheet is `change_requested`, a second `POST /request-change` returns `422 PMS_INVALID_GOAL_STATE` (or `409 PMS_REVISION_PENDING`). When the manager sends the sheet again the request is answered and the employee can ask again, which increments `revision_count`.

`GET /get/my-change-requests?financial_year=` (`my_goals`) returns the employee's full request log; `GET /get/change-requests?employee_user_id=&financial_year=` (`team_goals`) returns one report's log for the manager. Each row: `kpi_id, kra_name, kpi_name, revision_no, requested_on, reason, proposed_target, target_at_request, status (requested|responded), decision (revised|unchanged), final_target, manager_remarks, responded_on, acknowledged_on`. New table `pms.goal_change_request` and column `original_target` — see `sql/V5__pms_goal_change_requests.sql`.

| Field | Meaning |
|---|---|
| `goal_status` | `sent_to_employee` (can acknowledge or request a change), `acknowledged`, `change_requested`, `with_hod`, `approved` |
| `manager_name` | The manager who set the targets (stored when they save) |
| `sent_on` | Date the manager sent the sheet (`yyyy-MM-dd`), null before it is sent |
| `acknowledge_by` | End date of the active cycle's employee-acknowledgement stage for the year; null when no active cycle sets one |
| `kpis[].target` | The current target |
| `kpis[].revised_target` | The revised target still to acknowledge; null for KPIs not being revised |
| `kpis[].previous_target` | The target before that revision. The screen shows it as "Target" beside "Revised target" |
| `revision` | Null unless a revision is waiting: `source`, `kpi_id`, `revised_target`, `reason`, `approved_by`, `approved_on`, `acknowledge_by` (the first revised KPI is named; every revised KPI has `revised_target`) |

`revision.source` is `manager_revised` when the manager revised the sheet after the employee's change request or an HOD return and resent it with `POST /send` (each target may carry a `remarks` string, which becomes the revision `reason`; only targets that moved are listed). It is `hod_approved` for a mid-year revision approved by the HOD. A `manager_revised` revision is acknowledged together with the sheet: the employee's `POST /acknowledge` moves it to `acknowledged` and clears `revision`. The manager's own `get/employee-targets` already shows the new targets.
| `hod_remarks` | Remarks when the HOD returned the sheet |

`POST /acknowledge` — body `{ "financial_year": "2026-27" }`. For a `sent_to_employee` sheet it moves the sheet to `acknowledged`. When the sheet is `approved` and a revision is waiting, it applies the revised targets and clears `revision` (the sheet stays `approved`). `422 PMS_INVALID_GOAL_STATE` otherwise.

Not built: the HOD action that approves or returns a target revision. Until it exists nothing sets a revision to approved, so `revision` stays null.

### 10.9 HOD approvals — `/pms-goal-assignment` (`goal_approval`)

Goal sheets the manager sent to the HOD (status `with_hod`) and mid-year target revisions (`pending_hod`) share one list. `request_type` is `goal_sheet` or `target_revision` everywhere below.

| Call | Purpose |
|---|---|
| `GET /get/hod-queue?financial_year=` | List. Per row: employee, manager, `request_type`, `changed`, `first_change {kpi_name, unit, from, to}`, `more_changes`, `asked_by_*`, `asked_on`, `revision_rounds`. |
| `GET /get/hod-review?employee_user_id=&financial_year=&request_type=` | Review screen: `rows[]` with `approved_now`, `requested`, `change`, `change_percent`, `reason`, `changed`, `decision` (`pending/approved/returned/none`), plus `employee_request` and `manager_reply`. |
| `POST /hod/approve` `{employee_user_id, financial_year, request_type, kpi_ids?, remarks?}` | Approves a whole goal sheet, or the selected KPIs of a revision (all waiting KPIs when `kpi_ids` is empty). |
| `POST /hod/return` same body, `remarks` required | `422 PMS_REMARKS_REQUIRED` without remarks. A revision closes once every KPI has a decision; returned KPIs go back to the manager and are not shown to the employee. |
| `POST /hod/approve-selected` `{financial_year, items:[{employee_user_id, request_type}]}` | Bulk approve. Returns `approved`, `failed` and a `results[]` entry per request; a failure does not stop the others. |
| `GET /get/hod-history?financial_year=&request_type=&decision=&search=` | Decisions already made, newest first. |
| `GET /get/hod-trail?employee_user_id=&financial_year=` | `moves[]` (how each KPI moved) and `requests[]` (each request with its steps). |

Errors: `409 PMS_NOT_AWAITING_HOD` (not waiting), `422 PMS_KPI_NOT_AWAITING_HOD`, `422 PMS_NOTHING_SELECTED`, `422 PMS_REMARKS_REQUIRED` (return without remarks, also on the legacy `/return`).

Every step is written to `pms.goal_sheet_history` (`sql/V6__pms_goal_sheet_history.sql`) by send, request-change, acknowledge, send-to-HOD, approve, return and the mid-year revision. Per-KPI HOD decisions are stored on `target_revision_row.decision`.

The HOD list is organisation-wide for holders of `goal_approval`; it is not yet restricted to the HOD own reporting line.

### 10.10 Update progress — `/pms-progress` (`progress`, screen 4.4)

`GET /get/my-progress?financial_year=&period=current` returns the employee sheet (needs HOD-approved goals): per KPI `target` (the original, never overwritten), `revised_target` (final target after a revision, null if never revised), `actual`, `status`, `evidence_name`; plus `state` (`open` or `frozen`), `submitted_on`, `comment`.

`PUT /update/my-progress` body: `{financial_year, period, comment, submit, entries:[{kpi_id, actual, status (on_track|at_risk|off_track), attachment_name}]}`.
- `submit: false` saves a draft; the same KPI and period replaces the earlier figure.
- `submit: true` needs an actual and a status for every KPI (`422 PMS_PROGRESS_INCOMPLETE`) and freezes the period (`409 PMS_PROGRESS_FROZEN` on a later save).
- There is no manager approval: the manager only views the figures (`GET /get/team`, and `employee_actual` / `progress_status` on each KPI of `/get/employee-targets` and `/get/my-goals`).

New columns `goal_progress.status` and `goal_progress.submitted_on`: `sql/V7__pms_progress_status_submit.sql`.

### 10.11 Self appraisal — `/pms-appraisal` (`self_appraisal`, screen 4.6)

`GET /get/self-appraisal?financial_year=` (needs HOD-approved goals) returns `status` (`draft` or `submitted`), `self_score`, `kras[]` and `competencies[]`.
- KRA: `kpi_name`, `target` (original), `revised_target` (final target after a revision, else null), `actual` (the saved figure, else what the employee reported in Update Progress), `self_rating`, `evidence`, `attachment_name`.
- Competency: `id`, `name`, `type` (`hr` or `manager`), `self_rating`, `comment`.

`PUT /update/self-appraisal`: `{financial_year, submit, kras:[{kpi_id, rating, actual, evidence, attachment_name}], competencies:[{competency_id, rating, comment}]}`. Ratings are 1-5. `submit: true` needs every KRA and competency rated (`422 PMS_APPRAISAL_INCOMPLETE`); a submitted appraisal is read only (`409 PMS_INVALID_GOAL_STATE`). Columns `appraisal_line.actual`, `evidence`, `self_comment`: `sql/V8__pms_self_appraisal_fields.sql`.

### 10.12 Stage notices — `/pms-goal-assignment/get/stage-notices`

`GET /get/stage-notices?financial_year=2026-27` (any signed-in user) returns the stage windows of the organisation active cycle (latest published) that concern the caller, taken from the cycle timeline (screen 1.3):

| Permission held | Stage | Audience |
|---|---|---|
| `team_goals` | `goal_setting` | manager |
| `my_goals` | `employee_acknowledgement` | employee |
| `goal_approval` | `hod_approval` | hod |

Each notice: `stage`, `title`, `audience`, `cycle_name`, `start_date`, `end_date`, `state` (`upcoming|open|closed`), `days_left`, `notify`. `notify` is true when the stage Notification switch (timeline) and the cycle audience switch (Notify Managers / Employees / HOD, step 4) are both on and the window is not closed. The bell lists only `notify` notices; the banner on Team Goal Setting, My Goals and Goal Approvals shows the window and its end date whenever the stage is returned.

### 10.13 Cycle screens aligned to the mockups

- **Stages (1.3):** `goal_setting`, `employee_acknowledgement`, `hod_approval` (Goal Approval L2), `self_appraisal`, `manager_appraisal` (L1 Rating), `hod_review` (L2 Rating), `hr_competency_rating`, `calibration_final_approval`, `approval_publish`. All nine need dates to publish. `progress_tracking` and `mid_year_review` are no longer cycle stages (old rows still load).
- **Finalize (1.5):** `notify_employees`, `notify_managers` (Section Heads), `notify_hod` (Functional Heads), `notify_hr` (Plant Heads and Plant HR), `notify_gp`, and `approval_workflow_id`.
- **Approval Workflow master** `/pms-approval-workflow` (`pms_cycle`): `GET /get/workflows` (a Standard one is created the first time), `POST /create/workflow`, `PUT /update/workflow/{id}`, `DELETE /delete/workflow/{id}` (`409 PMS_WORKFLOW_IN_USE` when a cycle uses it). Body: `{name, description, steps: [...], status}`.
- **Competencies (2.5):** `weightage` is optional; when left out the ticked competencies share 100 equally.
- SQL: `sql/V9__pms_cycle_mockup_alignment.sql`.

### 10.14 Publish notifications — `/pms-notification`

When a cycle is published, every audience ticked on screen 1.5 gets an in-app notification (the bell). The same happens when an audience is ticked later on a live cycle (only the newly ticked ones). Recipients are the users IAM reports as holding the grant (`users_with_permission` RPC):

| Audience (screen 1.5) | Grant |
|---|---|
| Employees | `my_goals` |
| Section Heads | `team_goals` |
| Functional Heads | `goal_approval` |
| Plant Heads and Plant HR | `appraisal_history` |
| GP | `goal_template` |

`GET /get/my` (any signed-in user) returns `{unread, items:[{id, title, message, link_path, created_on, read}]}` (latest 50). `POST /read` with `{ids?: []}` marks them read (empty = all). Counts per audience are in the cycle activation `notifications`. Table `pms.user_notification`.
