package com.sentrifugo.pms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Drives every PMS screen API (cycle 1.1-1.6, goal template 2.1-2.4, masters 2.5-2.9, rating scale 2.10) end to end
 * over HTTP through the real security chain, controllers, services, mappers and JPA, on in-memory H2. Only the
 * Valkey client is mocked.
 */
@SpringBootTest(
        classes = PmsApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                // Never touch the real database/Valkey configured in application.properties.
                "spring.datasource.url=jdbc:h2:mem:pmsapi;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS pms",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        })
class PmsApiTest {

    private static final String ORG_A = "11111111-1111-1111-1111-111111111111";
    private static final String ORG_B = "22222222-2222-2222-2222-222222222222";
    private static final String BASE = "/api/v1/pms";
    private static final String PLANT_1 = "aaaaaaaa-0000-0000-0000-00000000000a";
    private static final String PLANT_2 = "aaaaaaaa-0000-0000-0000-00000000000b";
    private static final String DEPT = "dddddddd-0000-0000-0000-000000000001";
    private static final String ROLE = "eeeeeeee-0000-0000-0000-000000000001";

    private static String session(String userId, String orgId, boolean canWrite) {
        // The action codes are IAM's seeded performance_management permission catalog (the controllers' actions).
        return """
                {"user_id": "%s", "email": "u@example.com", "org_id": "%s", "is_super_admin": false,
                 "is_org_admin": false,
                 "permissions": {"performance_management": {"acl": "editor",
                   "actions": {"pms_cycle": %s, "goal_template": %s, "kra_master": %s, "kpi_master": %s,
                               "competency_master": %s, "rating_scale": %s}, "action_acls": {}}}}
                """.formatted(userId, orgId, canWrite, canWrite, canWrite, canWrite, canWrite, canWrite);
    }

    /** A session holding only the given performance_management actions (a manager, an employee, an HOD). */
    private static String sessionWith(String userId, String action) {
        return """
                {"user_id": "%s", "email": "u@example.com", "org_id": "%s", "is_super_admin": false,
                 "is_org_admin": false,
                 "permissions": {"performance_management": {"acl": "editor",
                   "actions": {"%s": true}, "action_acls": {}}}}
                """.formatted(userId, ORG_A, action);
    }

    @MockitoBean
    StringRedisTemplate redis;

    /** IAM: who holds a grant, for the publish notifications. */
    @MockitoBean
    com.sentrifugo.integration.IamEmployeeClient iam;

    @Value("${local.server.port}")
    int port;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void stubSessions() {
        ValueOperations<String, String> sessions = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(sessions);
        when(sessions.get("session:writer-a")).thenReturn(session("aaaaaaaa-0000-0000-0000-000000000001", ORG_A, true));
        when(sessions.get("session:reader-a")).thenReturn(session("aaaaaaaa-0000-0000-0000-000000000002", ORG_A, false));
        when(sessions.get("session:mgr-a")).thenReturn(sessionWith("aaaaaaaa-0000-0000-0000-000000000011", "team_goals"));
        when(sessions.get("session:emp-a")).thenReturn(sessionWith("aaaaaaaa-0000-0000-0000-000000000012", "my_goals"));
        when(sessions.get("session:hod-a")).thenReturn(sessionWith("aaaaaaaa-0000-0000-0000-000000000013", "goal_approval"));
        when(sessions.get("session:writer-b")).thenReturn(session("bbbbbbbb-0000-0000-0000-000000000001", ORG_B, true));
        when(sessions.get("session:mongo-org")).thenReturn(session("u1", "665f1c2e9b1e8a00aaaaaaaa", true));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private HttpResponse<String> call(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + BASE + path))
                .header("Content-Type", "application/json");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode ok(HttpResponse<String> response, int status) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        JsonNode body = json.readTree(response.body());
        assertThat(body.get("success").asBoolean()).isTrue();
        return body.get("data");
    }

    private String code(HttpResponse<String> response, int status) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        return json.readTree(response.body()).get("code").asString();
    }

    private static String unique() {
        return Long.toString(System.nanoTime(), 36);
    }

    private String scaleBody(String name, boolean isDefault) {
        return """
                {"name": "%s", "is_default": %s, "show_definitions_to_employees": true, "levels": [
                  {"rating_value": 5, "label": "Outstanding", "definition": "Exceptional", "minimum_score": 4.50, "maximum_score": 5.00, "colour_code": "#16a34a"},
                  {"rating_value": 4, "label": "Exceeds", "minimum_score": 3.50, "maximum_score": 4.49, "colour_code": "#2563eb"},
                  {"rating_value": 3, "label": "Meets", "minimum_score": 2.50, "maximum_score": 3.49, "colour_code": "#6d5ae6"},
                  {"rating_value": 2, "label": "Needs Improvement", "minimum_score": 1.50, "maximum_score": 2.49, "colour_code": "#d97706"},
                  {"rating_value": 1, "label": "Unsatisfactory", "minimum_score": 1.00, "maximum_score": 1.49, "colour_code": "#dc2626"}]}
                """.formatted(name, isDefault);
    }

    private String createScale(String token, String name) throws Exception {
        return ok(call("POST", "/pms-rating-scale/create/rating-scale", token, scaleBody(name, false)), 201)
                .get("id").asString();
    }

    private String createKra(String token, String name) throws Exception {
        return ok(call("POST", "/pms-master/create/kra", token, "{\"name\": \"" + name + "\"}"), 201)
                .get("id").asString();
    }

    private String createKpi(String token, String kraId, String name) throws Exception {
        return ok(call("POST", "/pms-master/create/kpi", token, """
                {"kra_id": "%s", "name": "%s", "unit": "TPD", "target_type": "common",
                 "expected_outcome": "Sustain planned output", "evidence_required": "DCS report"}
                """.formatted(kraId, name)), 201).get("id").asString();
    }

    private String createCompetency(String token, String name) throws Exception {
        return ok(call("POST", "/pms-master/create/competency", token,
                "{\"name\": \"" + name + "\", \"category\": \"Behavioural\"}"), 201).get("id").asString();
    }

    // ── security ─────────────────────────────────────────────────────────────

    @Test
    void protectedRoutesNeedATokenAndWritesNeedThePermission() throws Exception {
        assertThat(code(call("GET", "/pms-cycle/get/cycles", null, null), 401)).isEqualTo("UNAUTHORIZED");
        assertThat(code(call("POST", "/pms-master/create/kra", "reader-a", "{\"name\": \"x\"}"), 403))
                .isEqualTo("FORBIDDEN");
        assertThat(call("GET", "/pms-master/get/kras", "reader-a", null).statusCode()).isEqualTo(200);
        // IAM ids are 24-character MongoDB ObjectIds, not UUIDs; they must scope correctly.
        assertThat(call("GET", "/pms-cycle/get/cycles", "mongo-org", null).statusCode()).isEqualTo(200);
    }

    // ── 2.10 / 1.5 rating scale ──────────────────────────────────────────────

    @Test
    void ratingScaleCreateListUpdateAndDefaultHandling() throws Exception {
        String first = createScale("writer-a", "Standard " + unique());
        JsonNode created = ok(call("GET", "/pms-rating-scale/get/rating-scale/" + first, "writer-a", null), 200);
        assertThat(created.get("levels").size()).isEqualTo(5);
        assertThat(created.get("levels").get(0).get("rating_value").asInt()).isEqualTo(5); // highest first
        assertThat(created.get("status").asString()).isEqualTo("active");

        // making a second scale the default clears the first one's flag
        String second = ok(call("POST", "/pms-rating-scale/create/rating-scale", "writer-a",
                scaleBody("Default " + unique(), true)), 201).get("id").asString();
        assertThat(ok(call("GET", "/pms-rating-scale/get/rating-scale/" + first, "writer-a", null), 200)
                .get("is_default").asBoolean()).isFalse();
        assertThat(ok(call("GET", "/pms-rating-scale/get/rating-scale/" + second, "writer-a", null), 200)
                .get("is_default").asBoolean()).isTrue();

        // edit a label and drop a level
        String edited = """
                {"name": "Edited", "is_default": true, "show_definitions_to_employees": false, "levels": [
                  {"rating_value": 5, "label": "Top", "minimum_score": 4.00, "maximum_score": 5.00, "colour_code": "#16a34a"},
                  {"rating_value": 1, "label": "Low", "minimum_score": 1.00, "maximum_score": 3.99, "colour_code": "#dc2626"}]}
                """;
        JsonNode updated = ok(call("PUT", "/pms-rating-scale/update/rating-scale/" + second, "writer-a", edited), 200);
        assertThat(updated.get("levels").size()).isEqualTo(2);
        assertThat(updated.get("levels").get(0).get("label").asString()).isEqualTo("Top");
        assertThat(updated.get("show_definitions_to_employees").asBoolean()).isFalse();

        // overlapping ranges are refused
        String overlapping = scaleBody("Bad", false).replace("\"maximum_score\": 4.49", "\"maximum_score\": 4.60");
        assertThat(code(call("POST", "/pms-rating-scale/create/rating-scale", "writer-a", overlapping), 422))
                .isEqualTo("VALIDATION_ERROR");

        // another organisation cannot see it, and the active filter works
        assertThat(code(call("GET", "/pms-rating-scale/get/rating-scale/" + first, "writer-b", null), 404))
                .isEqualTo("PMS_RATING_SCALE_NOT_FOUND");
        assertThat(ok(call("GET", "/pms-rating-scale/get/rating-scales?status=active", "writer-a", null), 200)
                .size()).isGreaterThanOrEqualTo(2);
    }

    // ── 2.5-2.9 masters ──────────────────────────────────────────────────────

    @Test
    void masterDataCrudDuplicatesAndInUseProtection() throws Exception {
        String suffix = unique();
        String kra = createKra("writer-a", "Production " + suffix);
        assertThat(code(call("POST", "/pms-master/create/kra", "writer-a",
                "{\"name\": \"production " + suffix + "\"}"), 409)).isEqualTo("PMS_KRA_DUPLICATE");

        String kpi = createKpi("writer-a", kra, "Clinker " + suffix);
        assertThat(code(call("POST", "/pms-master/create/kpi", "writer-a", """
                {"kra_id": "%s", "name": "clinker %s", "unit": "TPD", "target_type": "common"}
                """.formatted(kra, suffix)), 409)).isEqualTo("PMS_KPI_DUPLICATE");
        assertThat(code(call("POST", "/pms-master/create/kpi", "writer-a", """
                {"kra_id": "%s", "name": "x", "unit": "TPD", "target_type": "weekly"}
                """.formatted(kra)), 422)).isEqualTo("VALIDATION_ERROR");
        assertThat(code(call("POST", "/pms-master/create/kpi", "writer-b", """
                {"kra_id": "%s", "name": "y", "unit": "TPD", "target_type": "common"}
                """.formatted(kra)), 422)).isEqualTo("PMS_KRA_NOT_FOUND"); // another organisation's KRA

        JsonNode kpis = ok(call("GET", "/pms-master/get/kpis", "writer-a", null), 200);
        boolean listed = false;
        for (JsonNode k : kpis) {
            if (k.get("id").asString().equals(kpi)) {
                listed = true;
                assertThat(k.get("kra_name").asString()).isEqualTo("Production " + suffix);
                assertThat(k.get("target_type").asString()).isEqualTo("common");
            }
        }
        assertThat(listed).isTrue();

        // a KRA with KPIs cannot be deleted; once the KPI is gone it can
        assertThat(code(call("DELETE", "/pms-master/delete/kra/" + kra, "writer-a", null), 409))
                .isEqualTo("PMS_KRA_IN_USE");
        JsonNode updatedKpi = ok(call("PUT", "/pms-master/update/kpi/" + kpi, "writer-a", """
                {"kra_id": "%s", "name": "Clinker renamed %s", "unit": "%%", "target_type": "individual"}
                """.formatted(kra, suffix)), 200);
        assertThat(updatedKpi.get("target_type").asString()).isEqualTo("individual");
        assertThat(updatedKpi.get("expected_outcome").isNull()).isTrue(); // optional fields are replaced
        ok(call("DELETE", "/pms-master/delete/kpi/" + kpi, "writer-a", null), 200);
        ok(call("DELETE", "/pms-master/delete/kra/" + kra, "writer-a", null), 200);
        assertThat(code(call("PUT", "/pms-master/update/kra/" + kra, "writer-a", "{\"name\": \"gone\"}"), 404))
                .isEqualTo("PMS_KRA_NOT_FOUND");

        String competency = createCompetency("writer-a", "Discipline " + suffix);
        assertThat(ok(call("GET", "/pms-master/get/competencies?search=discipline%20" + suffix, "writer-a", null), 200)
                .size()).isEqualTo(1);
        assertThat(ok(call("PUT", "/pms-master/update/competency/" + competency, "writer-a",
                "{\"name\": \"Discipline " + suffix + "\", \"category\": \"Technical\", \"status\": \"inactive\"}"),
                200).get("status").asString()).isEqualTo("inactive");
        ok(call("DELETE", "/pms-master/delete/competency/" + competency, "writer-a", null), 200);
        assertThat(ok(call("GET", "/pms-master/get/kpi-units", "writer-a", null), 200).size()).isGreaterThan(0);
    }

    // ── 2.1-2.4 goal template ────────────────────────────────────────────────

    @Test
    void goalTemplateWizardValidatesWeightsAndActivatesWhenComplete() throws Exception {
        String suffix = unique();
        String kra = createKra("writer-a", "Efficiency " + suffix);
        String otherKra = createKra("writer-a", "Safety " + suffix);
        String kpi1 = createKpi("writer-a", kra, "Run factor " + suffix);
        String kpi2 = createKpi("writer-a", kra, "Output " + suffix);
        String foreignKpi = createKpi("writer-a", otherKra, "LTI " + suffix);
        String comp1 = createCompetency("writer-a", "Teamwork " + suffix);
        String comp2 = createCompetency("writer-a", "Initiative " + suffix);

        JsonNode template = ok(call("POST", "/pms-goal-template/create/goal-template", "writer-a", """
                {"financial_year": "FY 2026-27", "template_name": "Production Engineer %s", "description": "d",
                 "department_id": "%s", "role_id": "%s", "plant_id": "%s", "effective_from": "2026-04-01",
                 "status": "active"}
                """.formatted(suffix, DEPT, ROLE, PLANT_1)), 201);
        String id = template.get("id").asString();
        assertThat(template.get("status").asString()).isEqualTo("draft"); // active only once complete

        String path = "/pms-goal-template/update/goal-template/" + id;

        // weights that do not total 100 are refused unless it is a draft save
        String partial = """
                {"save_as_draft": %s, "kras": [{"kra_id": "%s", "kpis": [
                  {"kpi_id": "%s", "weightage": 25, "target_type": "common"},
                  {"kpi_id": "%s", "weightage": 20}]}]}
                """;
        assertThat(code(call("PUT", path + "/kra-kpi", "writer-a", partial.formatted("false", kra, kpi1, kpi2)), 422))
                .isEqualTo("PMS_TEMPLATE_INCOMPLETE");
        JsonNode draft = ok(call("PUT", path + "/kra-kpi", "writer-a", partial.formatted("true", kra, kpi1, kpi2)), 200);
        assertThat(draft.get("total_kpi_weightage").decimalValue().intValue()).isEqualTo(45);

        // a KPI from a different KRA, a repeated KPI and an unknown KRA are refused
        assertThat(code(call("PUT", path + "/kra-kpi", "writer-a", """
                {"kras": [{"kra_id": "%s", "kpis": [{"kpi_id": "%s", "weightage": 100}]}]}
                """.formatted(kra, foreignKpi)), 422)).isEqualTo("VALIDATION_ERROR");
        assertThat(code(call("PUT", path + "/kra-kpi", "writer-b", """
                {"kras": [{"kra_id": "%s", "kpis": [{"kpi_id": "%s", "weightage": 100}]}]}
                """.formatted(kra, kpi1)), 404)).isEqualTo("PMS_TEMPLATE_NOT_FOUND");

        // the complete selection, KPI target type defaulting to the master's value
        JsonNode complete = ok(call("PUT", path + "/kra-kpi", "writer-a", """
                {"kras": [{"kra_id": "%s", "kpis": [
                  {"kpi_id": "%s", "weightage": 60, "target_type": "individual"},
                  {"kpi_id": "%s", "weightage": 40}]}]}
                """.formatted(kra, kpi1, kpi2)), 200);
        assertThat(complete.get("kras").size()).isEqualTo(1);
        assertThat(complete.get("kras").get(0).get("kra_name").asString()).isEqualTo("Efficiency " + suffix);
        assertThat(complete.get("kras").get(0).get("kpis").get(0).get("target_type").asString()).isEqualTo("individual");
        assertThat(complete.get("kras").get(0).get("kpis").get(1).get("target_type").asString()).isEqualTo("common");
        assertThat(complete.get("status").asString()).isEqualTo("draft");

        // competencies: must total 100; the final save activates the template
        assertThat(code(call("PUT", path + "/competencies", "writer-a", """
                {"competencies": [{"competency_id": "%s", "weightage": 50}]}
                """.formatted(comp1)), 422)).isEqualTo("PMS_TEMPLATE_INCOMPLETE");
        JsonNode finished = ok(call("PUT", path + "/competencies", "writer-a", """
                {"competencies": [{"competency_id": "%s", "weightage": 55}, {"competency_id": "%s", "weightage": 45}]}
                """.formatted(comp1, comp2)), 200);
        assertThat(finished.get("status").asString()).isEqualTo("active");
        assertThat(finished.get("competencies").size()).isEqualTo(2);
        assertThat(finished.get("total_competency_weightage").decimalValue().intValue()).isEqualTo(100);

        // masters used by an active template cannot be deleted
        assertThat(code(call("DELETE", "/pms-master/delete/kpi/" + kpi1, "writer-a", null), 409))
                .isEqualTo("PMS_KPI_IN_USE");
        assertThat(code(call("DELETE", "/pms-master/delete/competency/" + comp1, "writer-a", null), 409))
                .isEqualTo("PMS_COMPETENCY_IN_USE");

        // an edit that makes it incomplete drops it back to draft
        JsonNode demoted = ok(call("PUT", path + "/kra-kpi", "writer-a", partial.formatted("true", kra, kpi1, kpi2)), 200);
        assertThat(demoted.get("status").asString()).isEqualTo("draft");

        // list filters
        assertThat(ok(call("GET", "/pms-goal-template/get/goal-templates?financial_year=FY%202026-27&search=engineer%20"
                + suffix + "&department_id=" + DEPT + "&plant_id=" + PLANT_1, "writer-a", null), 200).size())
                .isEqualTo(1);
        assertThat(ok(call("GET", "/pms-goal-template/get/goal-templates?search=engineer%20" + suffix, "writer-b", null),
                200).size()).isZero();
        assertThat(ok(call("GET", "/pms-goal-template/get/goal-templates?plant_id=" + PLANT_2 + "&search=" + suffix,
                "writer-a", null), 200).size()).isZero();

        // basic info update
        JsonNode renamed = ok(call("PUT", path, "writer-a", """
                {"financial_year": "FY 2026-27", "template_name": "Renamed %s", "department_id": "%s",
                 "role_id": "%s", "effective_from": "2026-05-01", "status": "inactive"}
                """.formatted(suffix, DEPT, ROLE)), 200);
        assertThat(renamed.get("template_name").asString()).isEqualTo("Renamed " + suffix);
        assertThat(renamed.get("status").asString()).isEqualTo("inactive");
    }

    // ── 1.1-1.6 cycle ────────────────────────────────────────────────────────

    private static final String[] STAGES = {"goal_setting", "employee_acknowledgement", "hod_approval",
            "self_appraisal", "manager_appraisal", "hod_review", "hr_competency_rating",
            "calibration_final_approval", "approval_publish"};

    private String stagesJson(boolean complete) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < STAGES.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            boolean withDates = complete || i < 3;
            sb.append("{\"stage\": \"").append(STAGES[i]).append("\", \"notify\": true");
            if (withDates) {
                sb.append(", \"start_date\": \"2026-05-0").append(1 + i % 9).append("\", \"end_date\": \"2027-03-31\"");
            }
            sb.append('}');
        }
        return sb.append(']').toString();
    }

    private String cycleBody(String name, String type, String start, String end, String stages, String scaleId,
                             String plantJson) {
        return """
                {"basic": {"name": "%s", "description": "d", "type": "%s", "period_start": "%s", "period_end": "%s"},
                 %s
                 "applicability": {%s "all_departments": true,
                                   "goal_setting": {"employment_types": ["permanent", "probationary", "trainee"],
                                                    "min_service_months": 0},
                                   "appraisal": {"employment_types": ["permanent"], "min_service_months": 12,
                                                 "service_as_on": "2026-04-01"},
                                   "exclude_notice_period": true},
                 "finalize": {"rating_scale_id": %s, "notify_managers": true, "notify_employees": true,
                              "notify_hod": false, "notify_hr": true},
                 "current_step": 3}
                """.formatted(name, type, start, end, stages == null ? "" : "\"stages\": " + stages + ",",
                plantJson, scaleId == null ? "null" : "\"" + scaleId + "\"");
    }

    @Test
    void approvalWorkflowMasterAndTheCycleFinalizeStepKeepTheFiveAudiencesAndTheWorkflow() throws Exception {
        // a Standard workflow exists the first time the master is listed
        JsonNode first = ok(call("GET", "/pms-approval-workflow/get/workflows", "writer-a", null), 200);
        assertThat(first.size()).isGreaterThanOrEqualTo(1);
        assertThat(first.get(0).get("steps").size()).isGreaterThan(1);

        String name = "Workflow " + unique();
        JsonNode created = ok(call("POST", "/pms-approval-workflow/create/workflow", "writer-a",
                "{\"name\": \"" + name + "\", \"steps\": [\"Section Head\", \"GP\"]}"), 201);
        String id = created.get("id").asString();
        assertThat(code(call("POST", "/pms-approval-workflow/create/workflow", "writer-a",
                "{\"name\": \"" + name.toUpperCase() + "\", \"steps\": [\"GP\"]}"), 409))
                .isEqualTo("PMS_WORKFLOW_EXISTS");
        assertThat(code(call("POST", "/pms-approval-workflow/create/workflow", "writer-a",
                "{\"name\": \"No steps\", \"steps\": []}"), 422)).isNotBlank();
        ok(call("PUT", "/pms-approval-workflow/update/workflow/" + id, "writer-a",
                "{\"name\": \"" + name + "\", \"steps\": [\"Section Head\", \"HOD\", \"GP\"]}"), 200);

        // the cycle keeps the workflow and the GP switch
        String scale = createScale("writer-a", "Wf scale " + unique());
        String body = cycleBody("FY 2026-27 " + unique(), "annual", "2026-04-01", "2027-03-31", stagesJson(false),
                scale, "\"all_plants\": true,")
                .replace("\"notify_hr\": true}", "\"notify_hr\": true, \"notify_gp\": true, "
                        + "\"approval_workflow_id\": \"" + id + "\"}");
        JsonNode cycle = ok(call("POST", "/pms-cycle/create/cycle", "writer-a", body), 201);
        assertThat(cycle.get("finalize").get("notify_gp").asBoolean()).isTrue();
        assertThat(cycle.get("finalize").get("approval_workflow_id").asString()).isEqualTo(id);
        assertThat(cycle.get("stages").size()).isEqualTo(9);
        assertThat(code(call("DELETE", "/pms-approval-workflow/delete/workflow/" + id, "writer-a", null), 409))
                .isEqualTo("PMS_WORKFLOW_IN_USE");
    }

    @Test
    void publishingNotifiesEveryTickedAudienceInTheBell() throws Exception {
        String employee = "aaaaaaaa-0000-0000-0000-000000000012";
        String manager = "aaaaaaaa-0000-0000-0000-000000000011";
        org.mockito.Mockito.when(iam.usersWithPermission(org.mockito.ArgumentMatchers.eq(ORG_A),
                org.mockito.ArgumentMatchers.eq("performance_management"),
                org.mockito.ArgumentMatchers.eq("my_goals")))
                .thenReturn(java.util.List.of(new com.sentrifugo.integration.model.IamUserRef(employee, "e@x.com",
                        "Emp")));
        org.mockito.Mockito.when(iam.usersWithPermission(org.mockito.ArgumentMatchers.eq(ORG_A),
                org.mockito.ArgumentMatchers.eq("performance_management"),
                org.mockito.ArgumentMatchers.eq("team_goals")))
                .thenReturn(java.util.List.of(new com.sentrifugo.integration.model.IamUserRef(manager, "m@x.com",
                        "Mgr")));

        String scale = createScale("writer-a", "Bell scale " + unique());
        String id = ok(call("POST", "/pms-cycle/create/cycle", "writer-a", cycleBody("FY 2026-27 " + unique(),
                "annual", "2026-04-01", "2027-03-31", stagesJson(true), scale, "\"all_plants\": true,")), 201)
                .get("id").asString();
        // employees and managers are ticked in cycleBody; HOD is not
        JsonNode activation = ok(call("POST", "/pms-cycle/publish/cycle/" + id, "writer-a", null), 200);
        assertThat(activation.get("notifications").size()).isGreaterThanOrEqualTo(3);

        JsonNode bell = ok(call("GET", "/pms-notification/get/my", "emp-a", null), 200);
        assertThat(bell.get("unread").asInt()).isEqualTo(1);
        assertThat(bell.get("items").get(0).get("title").asString()).contains("is live");
        assertThat(bell.get("items").get(0).get("message").asString()).contains("Goal setting is open from");
        assertThat(bell.get("items").get(0).get("link_path").asString()).isEqualTo("/pms/my-goals/2026");

        // the manager gets theirs; an HOD who was not ticked gets nothing
        assertThat(ok(call("GET", "/pms-notification/get/my", "mgr-a", null), 200).get("unread").asInt())
                .isEqualTo(1);
        assertThat(ok(call("GET", "/pms-notification/get/my", "hod-a", null), 200).get("unread").asInt()).isZero();

        // reading clears the unread count
        JsonNode after = ok(call("POST", "/pms-notification/read", "emp-a", "{}"), 200);
        assertThat(after.get("unread").asInt()).isZero();
        assertThat(after.get("items").get(0).get("read").asBoolean()).isTrue();
    }

    @Test
    void stageNoticesGiveEachRoleItsOwnWindowFromTheCycleTimeline() throws Exception {
        String scale = createScale("writer-a", "Notice scale " + unique());
        String id = ok(call("POST", "/pms-cycle/create/cycle", "writer-a", cycleBody("FY 2026-27 " + unique(),
                "annual", "2026-04-01", "2027-03-31", stagesJson(true), scale, "\"all_plants\": true,")), 201)
                .get("id").asString();
        ok(call("POST", "/pms-cycle/publish/cycle/" + id, "writer-a", null), 200);
        String path = "/pms-goal-assignment/get/stage-notices?financial_year=2026-27";

        // a manager gets the goal-setting window, with its end date and the bell switched on
        JsonNode manager = ok(call("GET", path, "mgr-a", null), 200);
        assertThat(manager.size()).isEqualTo(1);
        assertThat(manager.get(0).get("stage").asString()).isEqualTo("goal_setting");
        assertThat(manager.get(0).get("audience").asString()).isEqualTo("manager");
        assertThat(manager.get(0).get("end_date").asString()).isEqualTo("2027-03-31");
        assertThat(manager.get(0).get("notify").asBoolean()).isTrue();
        assertThat(manager.get(0).get("state").asString()).isIn("upcoming", "open");

        // the employee gets acknowledgement; the HOD gets approval, which the cycle does not notify
        JsonNode employee = ok(call("GET", path, "emp-a", null), 200);
        assertThat(employee.size()).isEqualTo(1);
        assertThat(employee.get(0).get("stage").asString()).isEqualTo("employee_acknowledgement");
        JsonNode hod = ok(call("GET", path, "hod-a", null), 200);
        assertThat(hod.size()).isEqualTo(1);
        assertThat(hod.get(0).get("stage").asString()).isEqualTo("hod_approval");
        assertThat(hod.get(0).get("notify").asBoolean()).isFalse();

        // someone with none of those permissions gets nothing; another year has no cycle
        assertThat(ok(call("GET", path, "reader-a", null), 200).size()).isZero();
        assertThat(ok(call("GET", "/pms-goal-assignment/get/stage-notices?financial_year=2031-32", "mgr-a", null),
                200).size()).isZero();
    }

    @Test
    void cycleDraftWizardPublishLifecycleAndOrganisationIsolation() throws Exception {
        String scale = createScale("writer-a", "Cycle scale " + unique());
        String name = "FY 2026-27 " + unique();

        // step 1-4 saved as a draft with everything but complete stage dates
        JsonNode created = ok(call("POST", "/pms-cycle/create/cycle", "writer-a",
                cycleBody(name, "annual", "2026-04-01", "2027-03-31", stagesJson(false), scale,
                        "\"all_plants\": false, \"plant_ids\": [\"" + PLANT_1 + "\"],")), 201);
        String id = created.get("id").asString();
        assertThat(created.get("cycle_code").asString()).startsWith("PMS-2627-A");
        assertThat(created.get("status").asString()).isEqualTo("draft");
        assertThat(created.get("stages").size()).isEqualTo(9);
        assertThat(created.get("stages").get(0).get("stage").asString()).isEqualTo("goal_setting");
        assertThat(created.get("applicability").get("plant_ids").get(0).asString()).isEqualTo(PLANT_1);
        assertThat(created.get("applicability").get("goal_setting").get("employment_types").size()).isEqualTo(3);
        assertThat(created.get("applicability").get("appraisal").get("employment_types").get(0).asString())
                .isEqualTo("permanent");
        assertThat(created.get("applicability").get("appraisal").get("min_service_months").asInt()).isEqualTo(12);
        assertThat(created.get("finalize").get("notify_hod").asBoolean()).isFalse();
        assertThat(created.get("created_on").isNull()).isFalse();

        // publishing an incomplete draft lists every gap
        HttpResponse<String> incomplete = call("POST", "/pms-cycle/publish/cycle/" + id, "writer-a", null);
        assertThat(code(incomplete, 422)).isEqualTo("PMS_CYCLE_PUBLISH_VALIDATION_FAILED");
        assertThat(incomplete.body()).contains("stage self_appraisal needs both a start and end date");
        assertThat(code(call("GET", "/pms-cycle/get/cycle/" + id + "/activation", "writer-a", null), 409))
                .isEqualTo("PMS_CYCLE_NOT_PUBLISHED");

        // another organisation sees nothing
        assertThat(code(call("GET", "/pms-cycle/get/cycle/" + id, "writer-b", null), 404)).isEqualTo("PMS_CYCLE_NOT_FOUND");
        assertThat(code(call("POST", "/pms-cycle/publish/cycle/" + id, "writer-b", null), 404))
                .isEqualTo("PMS_CYCLE_NOT_FOUND");

        // complete the timeline, switch to all plants + selected departments, then publish
        JsonNode updated = ok(call("PUT", "/pms-cycle/update/cycle/" + id, "writer-a", """
                {"basic": {"name": "%s renamed", "type": "mid_year", "period_start": "2026-04-01", "period_end": "2026-09-30"},
                 "stages": %s,
                 "applicability": {"all_plants": true, "all_departments": false,
                   "department_ids": ["%s"],
                   "goal_setting": {"employment_types": ["permanent", "contract"], "min_service_months": 0},
                   "appraisal": {"employment_types": ["permanent", "contract"], "min_service_months": 6,
                                 "service_as_on": "2026-04-01"}}}
                """.formatted(name, stagesJson(true), DEPT)), 200);
        assertThat(updated.get("basic").get("type").asString()).isEqualTo("mid_year");
        assertThat(updated.get("applicable_to").asString()).isEqualTo("All Plants");
        assertThat(updated.get("applicability").get("department_ids").get(0).asString()).isEqualTo(DEPT);
        assertThat(updated.get("applicability").get("plant_ids").size()).isZero();
        assertThat(updated.get("applicability").get("goal_setting").get("employment_types").size()).isEqualTo(2);
        assertThat(updated.get("applicability").get("appraisal").get("employment_types").size()).isEqualTo(2);
        assertThat(updated.get("finalize").get("rating_scale_id").asString()).isEqualTo(scale); // untouched
        assertThat(updated.get("cycle_code").asString()).startsWith("PMS-2627-A");

        JsonNode activation = ok(call("POST", "/pms-cycle/publish/cycle/" + id, "writer-a", null), 200);
        assertThat(activation.get("cycle").get("status").asString()).isEqualTo("active");
        assertThat(activation.get("published_on").isNull()).isFalse();
        assertThat(activation.get("notifications").size()).isEqualTo(3); // one record per ticked audience
        assertThat(activation.get("eligibility").isNull()).isTrue();
        assertThat(call("POST", "/pms-cycle/publish/cycle/" + id, "writer-a", null).statusCode()).isEqualTo(409);
        ok(call("GET", "/pms-cycle/get/cycle/" + id + "/activation", "writer-a", null), 200);

        // an active cycle is editable, then cancelled once; a cancelled one is final
        ok(call("PUT", "/pms-cycle/update/cycle/" + id, "writer-a",
                cycleBody(name + " v2", "mid_year", "2026-04-01", "2026-09-30", null, scale, "\"all_plants\": true,")), 200);
        assertThat(ok(call("POST", "/pms-cycle/cancel/cycle/" + id, "writer-a", null), 200)
                .get("status").asString()).isEqualTo("cancelled");
        assertThat(code(call("POST", "/pms-cycle/cancel/cycle/" + id, "writer-a", null), 409))
                .isEqualTo("PMS_CYCLE_NOT_CANCELLABLE");
        assertThat(code(call("PUT", "/pms-cycle/update/cycle/" + id, "writer-a",
                cycleBody(name, "annual", "2026-04-01", "2027-03-31", null, scale, "")), 409))
                .isEqualTo("PMS_CYCLE_NOT_EDITABLE");
    }

    @Test
    void cycleValidationAndRatingScaleChecks() throws Exception {
        String scale = createScale("writer-a", "Check scale " + unique());
        assertThat(code(call("POST", "/pms-cycle/create/cycle", "writer-a",
                cycleBody("n", "annual", "2027-03-31", "2026-04-01", null, scale, "")), 422)).isEqualTo("VALIDATION_ERROR");
        assertThat(code(call("POST", "/pms-cycle/create/cycle", "writer-a",
                cycleBody("n", "weekly", "2026-04-01", "2027-03-31", null, scale, "")), 422)).isEqualTo("VALIDATION_ERROR");
        assertThat(code(call("POST", "/pms-cycle/create/cycle", "writer-a",
                cycleBody("", "annual", "2026-04-01", "2027-03-31", null, scale, "")), 422)).isEqualTo("VALIDATION_ERROR");
        assertThat(code(call("POST", "/pms-cycle/create/cycle", "writer-a",
                cycleBody("n", "annual", "2026-04-01", "2027-03-31", null,
                        "99999999-9999-9999-9999-999999999999", "")), 422)).isEqualTo("PMS_RATING_SCALE_NOT_FOUND");
        // another organisation's scale is not usable
        String foreignScale = createScale("writer-b", "Foreign " + unique());
        assertThat(code(call("POST", "/pms-cycle/create/cycle", "writer-a",
                cycleBody("n", "annual", "2026-04-01", "2027-03-31", null, foreignScale, "")), 422))
                .isEqualTo("PMS_RATING_SCALE_NOT_FOUND");
        // duplicate stage and bad stage name
        String dupStages = "[{\"stage\": \"goal_setting\"}, {\"stage\": \"goal_setting\"}]";
        assertThat(code(call("POST", "/pms-cycle/create/cycle", "writer-a",
                cycleBody("n", "annual", "2026-04-01", "2027-03-31", dupStages, scale, "")), 422)).isEqualTo("VALIDATION_ERROR");
        assertThat(code(call("POST", "/pms-cycle/create/cycle", "writer-a",
                cycleBody("n", "annual", "2026-04-01", "2027-03-31", "[{\"stage\": \"nope\"}]", scale, "")), 422))
                .isEqualTo("VALIDATION_ERROR");
        assertThat(code(call("POST", "/pms-cycle/create/cycle", "writer-a", "{not json"), 400)).isEqualTo("BAD_REQUEST");
    }

    @Test
    void cycleListSummaryPlantFilterAndExport() throws Exception {
        String scale = createScale("writer-b", "List scale " + unique());
        String tag = "list-" + unique();
        String keep = ok(call("POST", "/pms-cycle/create/cycle", "writer-b",
                cycleBody(tag + " keep", "annual", "2026-04-01", "2027-03-31", null, scale,
                        "\"all_plants\": false, \"plant_ids\": [\"" + PLANT_1 + "\"],")), 201).get("id").asString();
        String drop = ok(call("POST", "/pms-cycle/create/cycle", "writer-b",
                cycleBody(tag + " drop", "custom", "2026-07-01", "2026-12-31", null, scale, "\"all_plants\": true,")),
                201).get("id").asString();
        ok(call("POST", "/pms-cycle/cancel/cycle/" + drop, "writer-b", null), 200);

        JsonNode all = ok(call("GET", "/pms-cycle/get/cycles?search=" + tag, "writer-b", null), 200);
        assertThat(all.get("total").asInt()).isEqualTo(2);
        assertThat(all.get("summary").get("all").asInt()).isEqualTo(2);

        // the status tab narrows items/total but not the summary
        JsonNode cancelled = ok(call("GET", "/pms-cycle/get/cycles?search=" + tag + "&status=cancelled", "writer-b",
                null), 200);
        assertThat(cancelled.get("total").asInt()).isEqualTo(1);
        assertThat(cancelled.get("items").get(0).get("id").asString()).isEqualTo(drop);
        assertThat(cancelled.get("items").get(0).get("applicable_to").asString()).isEqualTo("All Plants");
        assertThat(cancelled.get("summary").get("all").asInt()).isEqualTo(2);
        assertThat(cancelled.get("summary").get("draft").asInt()).isEqualTo(1);

        // plant filter: PLANT_1 matches "keep" (explicit) and "drop" (all plants); PLANT_2 matches only "drop"
        assertThat(ok(call("GET", "/pms-cycle/get/cycles?search=" + tag + "&plant_id=" + PLANT_1, "writer-b", null), 200)
                .get("total").asInt()).isEqualTo(2);
        JsonNode plant2 = ok(call("GET", "/pms-cycle/get/cycles?search=" + tag + "&plant_id=" + PLANT_2, "writer-b", null),
                200);
        assertThat(plant2.get("total").asInt()).isEqualTo(1);
        assertThat(plant2.get("items").get(0).get("id").asString()).isEqualTo(drop);

        assertThat(ok(call("GET", "/pms-cycle/get/cycles?search=" + tag + "&type=annual&year=2026", "writer-b", null), 200)
                .get("total").asInt()).isEqualTo(1);
        assertThat(code(call("GET", "/pms-cycle/get/cycles?status=bogus", "writer-b", null), 422))
                .isEqualTo("VALIDATION_ERROR");
        assertThat(code(call("GET", "/pms-cycle/get/cycles?skip=5&limit=20", "writer-b", null), 422))
                .isEqualTo("VALIDATION_ERROR");
        assertThat(ok(call("GET", "/pms-cycle/get/cycles?search=" + tag, "writer-a", null), 200).get("total").asInt())
                .isZero(); // organisation A sees none of B's cycles

        HttpResponse<String> csv = call("GET", "/pms-cycle/get/cycles/export?search=" + tag, "writer-b", null);
        assertThat(csv.statusCode()).isEqualTo(200);
        assertThat(csv.headers().firstValue("Content-Type").orElse("")).startsWith("text/csv");
        assertThat(csv.body()).startsWith("Cycle ID,Cycle Name").contains(tag + " keep");
        assertThat(keep).isNotEqualTo(drop);
    }
}
