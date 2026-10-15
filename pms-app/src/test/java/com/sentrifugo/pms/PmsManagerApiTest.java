package com.sentrifugo.pms;

import com.sentrifugo.db.entity.PmsCycleEntity;
import com.sentrifugo.db.entity.PmsCycleStageEntity;
import com.sentrifugo.db.entity.PmsGoalAssignmentEntity;
import com.sentrifugo.db.entity.PmsGoalAssignmentTargetEntity;
import com.sentrifugo.db.entity.PmsTargetRevisionEntity;
import com.sentrifugo.db.entity.PmsTargetRevisionRowEntity;
import com.sentrifugo.db.enums.PmsCycleStageType;
import com.sentrifugo.db.enums.PmsCycleStatus;
import com.sentrifugo.db.enums.PmsCycleType;
import com.sentrifugo.db.enums.PmsRevisionStatus;
import com.sentrifugo.db.repository.PmsCycleRepository;
import com.sentrifugo.db.repository.PmsCycleStageRepository;
import com.sentrifugo.db.repository.PmsGoalAssignmentRepository;
import com.sentrifugo.db.repository.PmsGoalAssignmentTargetRepository;
import com.sentrifugo.db.repository.PmsTargetRevisionRepository;
import com.sentrifugo.db.repository.PmsTargetRevisionRowRepository;
import com.sentrifugo.integration.IamEmployeeClient;
import com.sentrifugo.integration.model.IamEmployee;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Drives the manager screens after goal setting (3.6 - 3.14) and the template status / duplicate calls end to
 * end over HTTP through the real security chain, controllers, services and JPA, on in-memory H2. Only Valkey
 * and the IAM employee directory are mocked.
 */
@SpringBootTest(
        classes = PmsApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:pmsmanager;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS pms",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        })
class PmsManagerApiTest {

    private static final String ORG = "11111111-1111-1111-1111-111111111111";
    private static final String BASE = "/api/v1/pms";
    private static final String FY = "2026-27";
    private static final String DEPT = "dddddddd-0000-0000-0000-000000000001";

    @MockitoBean
    StringRedisTemplate redis;

    @MockitoBean
    IamEmployeeClient iam;

    @Value("${local.server.port}")
    int port;

    @Autowired
    PmsCycleRepository cycles;
    @Autowired
    PmsCycleStageRepository cycleStages;
    @Autowired
    PmsGoalAssignmentRepository assignments;
    @Autowired
    PmsGoalAssignmentTargetRepository assignmentTargets;
    @Autowired
    PmsTargetRevisionRepository revisions;
    @Autowired
    PmsTargetRevisionRowRepository revisionRows;

    private final ObjectMapper json = new ObjectMapper();
    private ValueOperations<String, String> sessions;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void stubRedis() {
        sessions = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(sessions);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String unique() {
        return Long.toString(System.nanoTime(), 36);
    }

    /** Registers a session token whose user holds exactly the given performance_management actions. */
    private String login(String userId, String... actions) {
        String token = "tok-" + unique() + "-" + userId.substring(0, 4);
        StringBuilder granted = new StringBuilder();
        for (String action : actions) {
            granted.append(granted.length() == 0 ? "" : ", ").append('"').append(action).append("\": true");
        }
        when(sessions.get("session:" + token)).thenReturn("""
                {"user_id": "%s", "email": "u@example.com", "org_id": "%s", "is_super_admin": false,
                 "is_org_admin": false, "full_name": "Test User", "first_name": "Test", "last_name": "User",
                 "permissions": {"performance_management": {"acl": "editor",
                   "actions": {%s}, "action_acls": {}}}}
                """.formatted(userId, ORG, granted));
        return token;
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }

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

    /** One approved-or-not employee under one manager, with a complete active template for their role. */
    private record Fixture(String hr, String manager, String employee, String hod, String employeeId,
                           String managerId, String roleId, String kpi1, String kpi2, String comp, String hrComp) {
    }

    private Fixture fixture(boolean sendAndApprove) throws Exception {
        String hr = login(uuid(), "pms_cycle", "goal_template", "kra_master", "kpi_master", "competency_master",
                "rating_scale");
        String managerId = uuid();
        String employeeId = uuid();
        String manager = login(managerId, "team_goals", "team_progress", "mid_year_review", "team_appraisal");
        String employee = login(employeeId, "my_goals", "progress", "self_appraisal");
        String hod = login(uuid(), "goal_approval");
        String roleId = uuid();
        String suffix = unique();

        String kra = ok(call("POST", "/pms-master/create/kra", hr, "{\"name\": \"Production " + suffix + "\"}"), 201)
                .get("id").asString();
        String kpi1 = kpi(hr, kra, "Clinker " + suffix, "TPD");
        String kpi2 = kpi(hr, kra, "Run factor " + suffix, "%");
        String comp = ok(call("POST", "/pms-master/create/competency", hr,
                "{\"name\": \"Teamwork " + suffix + "\", \"category\": \"Behavioural\"}"), 201).get("id").asString();
        String hrComp = ok(call("POST", "/pms-master/create/competency", hr,
                "{\"name\": \"Discipline " + suffix + "\", \"category\": \"HR\"}"), 201).get("id").asString();

        String templateId = ok(call("POST", "/pms-goal-template/create/goal-template", hr, """
                {"financial_year": "%s", "template_name": "Engineer %s", "department_id": "%s", "role_id": "%s",
                 "effective_from": "2026-04-01"}
                """.formatted(FY, suffix, DEPT, roleId)), 201).get("id").asString();
        String path = "/pms-goal-template/update/goal-template/" + templateId;
        ok(call("PUT", path + "/kra-kpi", hr, """
                {"kras": [{"kra_id": "%s", "kpis": [
                  {"kpi_id": "%s", "weightage": 60}, {"kpi_id": "%s", "weightage": 40}]}]}
                """.formatted(kra, kpi1, kpi2)), 200);
        JsonNode finished = ok(call("PUT", path + "/competencies", hr, """
                {"competencies": [{"competency_id": "%s"}, {"competency_id": "%s"}]}
                """.formatted(comp, hrComp)), 200);
        assertThat(finished.get("status").asString()).isEqualTo("active");

        when(iam.directReports(eq(ORG), eq(managerId))).thenReturn(List.of(
                new IamEmployee(employeeId, "SCL-1", "Suresh", "Babu", "s@x.com", DEPT, roleId, "Kiln Engineer")));

        Fixture f = new Fixture(hr, manager, employee, hod, employeeId, managerId, roleId, kpi1, kpi2, comp, hrComp);
        if (sendAndApprove) {
            sendTargets(f, "5800", "92");
            ok(call("POST", "/pms-goal-assignment/acknowledge", employee, "{\"financial_year\": \"" + FY + "\"}"),
                    200);
            // nothing was revised, so acknowledging approves the sheet: only revised KRAs go to the HOD
        }
        return f;
    }

    private String kpi(String hr, String kra, String name, String unit) throws Exception {
        return ok(call("POST", "/pms-master/create/kpi", hr, """
                {"kra_id": "%s", "name": "%s", "unit": "%s", "target_type": "common",
                 "expected_outcome": "Sustain planned output", "evidence_required": "DCS report"}
                """.formatted(kra, name, unit)), 201).get("id").asString();
    }

    /**
     * "Create and assign template" for the fixture's role, sending the template id the team list returns;
     * safe to repeat (it only assigns who has none).
     */
    private JsonNode assignTemplate(Fixture f) throws Exception {
        String templateId = ok(call("GET", "/pms-goal-assignment/get/team?financial_year=" + FY, f.manager(), null),
                200).get(0).get("template_id").asString();
        return ok(call("POST", "/pms-goal-assignment/assign-template", f.manager(),
                "{\"financial_year\": \"%s\", \"designation_id\": \"%s\", \"template_id\": \"%s\"}"
                        .formatted(FY, f.roleId(), templateId)), 200);
    }

    private void sendTargets(Fixture f, String target1, String target2) throws Exception {
        assignTemplate(f);
        ok(call("POST", "/pms-goal-assignment/send", f.manager(), """
                {"employee_user_id": "%s", "financial_year": "%s", "targets": [
                  {"kpi_id": "%s", "weight": 60, "target": %s}, {"kpi_id": "%s", "weight": 40, "target": %s}]}
                """.formatted(f.employeeId(), FY, f.kpi1(), target1, f.kpi2(), target2)), 200);
    }

    private void report(Fixture f, String period, String actual1, String actual2, String comment) throws Exception {
        ok(call("PUT", "/pms-progress/update/my-progress", f.employee(), """
                {"financial_year": "%s", "period": "%s", "comment": "%s", "entries": [
                  {"kpi_id": "%s", "actual": %s}, {"kpi_id": "%s", "actual": %s}]}
                """.formatted(FY, period, comment, f.kpi1(), actual1, f.kpi2(), actual2)), 200);
    }

    // ── 4.1 - 4.3 acknowledge and request change ────────────────────────────

    @Test
    void employeeAcknowledgesOrRequestsChangeAndTheManagerResendsUntilAcknowledged() throws Exception {
        Fixture f = fixture(false);
        String teamQuery = "/pms-goal-assignment/get/team?financial_year=" + FY;
        String myGoals = "/pms-goal-assignment/get/my-goals?financial_year=" + FY;
        String toHod = "{\"financial_year\": \"%s\", \"employee_user_ids\": [\"%s\"]}".formatted(FY, f.employeeId());
        String edit = """
                {"employee_user_id": "%s", "financial_year": "%s", "targets": [
                  {"kpi_id": "%s", "weight": 60, "target": 5700}, {"kpi_id": "%s", "weight": 40, "target": 90}]}
                """.formatted(f.employeeId(), FY, f.kpi1(), f.kpi2());

        // the employee sees nothing to act on before the manager has sent the goals
        sendTargets(f, "5800", "92");
        assertThat(ok(call("GET", myGoals, f.employee(), null), 200).get("goal_status").asString())
                .isEqualTo("sent_to_employee");

        // while the sheet is with the employee the manager can neither edit it nor send it to the HOD
        assertThat(code(call("PUT", "/pms-goal-assignment/update/employee-targets", f.manager(), edit), 409))
                .isEqualTo("PMS_INVALID_GOAL_STATE");
        assertThat(code(call("POST", "/pms-goal-assignment/send-to-hod", f.manager(), toHod), 422))
                .isEqualTo("PMS_INVALID_GOAL_STATE");

        // request change: the reason is required and the KPI must be on the employee's own sheet
        String change = "{\"financial_year\": \"%s\", \"kpi_id\": \"%s\", \"proposed_target\": 5500, "
                + "\"reason\": \"%s\"}";
        assertThat(call("POST", "/pms-goal-assignment/request-change", f.employee(),
                change.formatted(FY, f.kpi1(), "")).statusCode()).isIn(400, 422);
        assertThat(code(call("POST", "/pms-goal-assignment/request-change", f.employee(),
                change.formatted(FY, uuid(), "Kiln shutdown in Q3")), 422)).isEqualTo("PMS_KPI_NOT_IN_TEMPLATE");
        JsonNode requested = ok(call("POST", "/pms-goal-assignment/request-change", f.employee(),
                change.formatted(FY, f.kpi1(), "Kiln shutdown in Q3")), 200);
        assertThat(requested.get("goal_status").asString()).isEqualTo("change_requested");
        assertThat(requested.get("change_reason").asString()).isEqualTo("Kiln shutdown in Q3");
        // only a sheet that is "sent" can be answered, and the manager cannot send an unacknowledged sheet on
        assertThat(code(call("POST", "/pms-goal-assignment/acknowledge", f.employee(),
                "{\"financial_year\": \"%s\"}".formatted(FY)), 422)).isEqualTo("PMS_INVALID_GOAL_STATE");
        assertThat(code(call("POST", "/pms-goal-assignment/send-to-hod", f.manager(), toHod), 422))
                .isEqualTo("PMS_INVALID_GOAL_STATE");
        JsonNode member = ok(call("GET", teamQuery, f.manager(), null), 200).get(0);
        assertThat(member.get("goal_status").asString()).isEqualTo("change_requested");

        // the manager revises and resends: the request is cleared and the employee can answer again
        JsonNode resent = ok(call("POST", "/pms-goal-assignment/send", f.manager(), edit), 200);
        assertThat(resent.get("goal_status").asString()).isEqualTo("sent_to_employee");
        assertThat(resent.get("change_reason").isNull()).isTrue();
        ok(call("POST", "/pms-goal-assignment/request-change", f.employee(),
                change.formatted(FY, f.kpi2(), "Second thoughts on run factor")), 200); // repeated requests are allowed
        ok(call("POST", "/pms-goal-assignment/send", f.manager(), edit), 200);

        // another employee cannot see or answer this sheet; they have none of their own
        String stranger = login(uuid(), "my_goals");
        assertThat(code(call("GET", myGoals, stranger, null), 404)).isEqualTo("PMS_GOALS_NOT_FOUND");

        // acknowledge with a comment; the manager can now send it to the HOD
        JsonNode ack = ok(call("POST", "/pms-goal-assignment/acknowledge", f.employee(),
                "{\"financial_year\": \"%s\", \"comment\": \"Agreed.\"}".formatted(FY)), 200);
        assertThat(ack.get("goal_status").asString()).isEqualTo("acknowledged");
        assertThat(ack.get("acknowledge_comment").asString()).isEqualTo("Agreed.");
        assertThat(code(call("POST", "/pms-goal-assignment/request-change", f.employee(),
                change.formatted(FY, f.kpi1(), "Too late")), 422)).isEqualTo("PMS_INVALID_GOAL_STATE");
        assertThat(code(call("PUT", "/pms-goal-assignment/update/employee-targets", f.manager(), edit), 409))
                .isEqualTo("PMS_INVALID_GOAL_STATE");
        ok(call("POST", "/pms-goal-assignment/send-to-hod", f.manager(), toHod), 200);
        assertThat(ok(call("GET", teamQuery, f.manager(), null), 200).get(0).get("goal_status").asString())
                .isEqualTo("with_hod");
    }

    // ── change requests: original target kept, one open request per KPI, permanent count ──

    @Test
    void originalTargetIsKeptAndEveryChangeRequestIsCountedAndLogged() throws Exception {
        Fixture f = fixture(false);
        String myGoals = "/pms-goal-assignment/get/my-goals?financial_year=" + FY;
        String mine = "/pms-goal-assignment/get/my-change-requests?financial_year=" + FY;
        String theirs = "/pms-goal-assignment/get/change-requests?employee_user_id=" + f.employeeId()
                + "&financial_year=" + FY;
        String ask = "{\"financial_year\": \"%s\", \"kpi_id\": \"%s\", \"proposed_target\": 5500, "
                + "\"reason\": \"%s\"}";
        String answer = """
                {"employee_user_id": "%s", "financial_year": "%s", "targets": [
                  {"kpi_id": "%s", "weight": 60, "target": 5600, "remarks": "Partly agreed"},
                  {"kpi_id": "%s", "weight": 40, "target": 92}]}
                """.formatted(f.employeeId(), FY, f.kpi1(), f.kpi2());

        sendTargets(f, "5800", "92");
        JsonNode kpi1 = ok(call("GET", myGoals, f.employee(), null), 200).get("kpis").get(0);
        assertThat(kpi1.get("original_target").decimalValue().intValue()).isEqualTo(5800);
        assertThat(kpi1.get("revision_count").asInt()).isZero();
        assertThat(kpi1.get("revised_target").isNull()).isTrue();

        // one open request: it is logged, and a second one is locked out until the manager answers
        ok(call("POST", "/pms-goal-assignment/request-change", f.employee(), ask.formatted(FY, f.kpi1(), "Shutdown")),
                200);
        assertThat(code(call("POST", "/pms-goal-assignment/request-change", f.employee(),
                ask.formatted(FY, f.kpi1(), "Again")), 422)).isEqualTo("PMS_INVALID_GOAL_STATE");
        JsonNode open = ok(call("GET", mine, f.employee(), null), 200);
        assertThat(open.size()).isEqualTo(1);
        assertThat(open.get(0).get("revision_no").asInt()).isEqualTo(1);
        assertThat(open.get(0).get("status").asString()).isEqualTo("requested");
        assertThat(open.get(0).get("target_at_request").decimalValue().intValue()).isEqualTo(5800);

        // the manager's final target: shown as revised; the original target is untouched
        ok(call("POST", "/pms-goal-assignment/send", f.manager(), answer), 200);
        kpi1 = ok(call("GET", myGoals, f.employee(), null), 200).get("kpis").get(0);
        assertThat(kpi1.get("original_target").decimalValue().intValue()).isEqualTo(5800);
        assertThat(kpi1.get("revised_target").decimalValue().intValue()).isEqualTo(5600);
        assertThat(kpi1.get("revision_count").asInt()).isEqualTo(1);
        JsonNode first = ok(call("GET", mine, f.employee(), null), 200).get(0);
        assertThat(first.get("status").asString()).isEqualTo("responded");
        assertThat(first.get("decision").asString()).isEqualTo("revised");
        assertThat(first.get("final_target").decimalValue().intValue()).isEqualTo(5600);
        assertThat(first.get("manager_remarks").asString()).isEqualTo("Partly agreed");

        // unlocked again: a second request is counted, and a kept target is logged as unchanged
        ok(call("POST", "/pms-goal-assignment/request-change", f.employee(), ask.formatted(FY, f.kpi1(), "Still low")),
                200);
        ok(call("POST", "/pms-goal-assignment/send", f.manager(), answer), 200);
        kpi1 = ok(call("GET", myGoals, f.employee(), null), 200).get("kpis").get(0);
        assertThat(kpi1.get("revision_count").asInt()).isEqualTo(2);
        assertThat(kpi1.get("original_target").decimalValue().intValue()).isEqualTo(5800);
        JsonNode second = ok(call("GET", mine, f.employee(), null), 200).get(1);
        assertThat(second.get("revision_no").asInt()).isEqualTo(2);
        assertThat(second.get("decision").asString()).isEqualTo("unchanged");

        // acknowledging stamps the answers; the log and the original target stay
        ok(call("POST", "/pms-goal-assignment/acknowledge", f.employee(),
                "{\"financial_year\": \"%s\"}".formatted(FY)), 200);
        JsonNode log = ok(call("GET", mine, f.employee(), null), 200);
        assertThat(log.get(0).get("acknowledged_on").isNull()).isFalse();
        assertThat(log.get(1).get("acknowledged_on").isNull()).isFalse();
        kpi1 = ok(call("GET", myGoals, f.employee(), null), 200).get("kpis").get(0);
        assertThat(kpi1.get("original_target").decimalValue().intValue()).isEqualTo(5800);
        assertThat(kpi1.get("revised_target").decimalValue().intValue()).isEqualTo(5600);
        assertThat(ok(call("GET", theirs, f.manager(), null), 200).size()).isEqualTo(2);
    }

    // ── two KPIs revised in turn: each keeps its own original target, count and history ──

    @Test
    void eachKpiKeepsItsOwnOriginalTargetCountAndHistory() throws Exception {
        Fixture f = fixture(false);
        String myGoals = "/pms-goal-assignment/get/my-goals?financial_year=" + FY;
        String mine = "/pms-goal-assignment/get/my-change-requests?financial_year=" + FY;
        String ask = "{\"financial_year\": \"%s\", \"kpi_id\": \"%s\", \"proposed_target\": %s, "
                + "\"reason\": \"%s\"}";
        String answer = """
                {"employee_user_id": "%s", "financial_year": "%s", "targets": [
                  {"kpi_id": "%s", "weight": 60, "target": %s},
                  {"kpi_id": "%s", "weight": 40, "target": %s}]}
                """;

        sendTargets(f, "5800", "92");
        ok(call("POST", "/pms-goal-assignment/request-change", f.employee(), ask.formatted(FY, f.kpi1(), "5500", "A1")), 200);
        ok(call("POST", "/pms-goal-assignment/send", f.manager(),
                answer.formatted(f.employeeId(), FY, f.kpi1(), "5600", f.kpi2(), "92")), 200);
        // KPI 2 is questioned in the next cycle; KPI 1 keeps its original, revised value and count
        ok(call("POST", "/pms-goal-assignment/request-change", f.employee(), ask.formatted(FY, f.kpi2(), "88", "B1")), 200);
        ok(call("POST", "/pms-goal-assignment/send", f.manager(),
                answer.formatted(f.employeeId(), FY, f.kpi1(), "5600", f.kpi2(), "90")), 200);

        JsonNode kpis = ok(call("GET", myGoals, f.employee(), null), 200).get("kpis");
        assertThat(kpis.get(0).get("original_target").decimalValue().intValue()).isEqualTo(5800);
        assertThat(kpis.get(0).get("revised_target").decimalValue().intValue()).isEqualTo(5600);
        assertThat(kpis.get(0).get("revision_count").asInt()).isEqualTo(1);
        assertThat(kpis.get(1).get("original_target").decimalValue().intValue()).isEqualTo(92);
        assertThat(kpis.get(1).get("revised_target").decimalValue().intValue()).isEqualTo(90);
        assertThat(kpis.get(1).get("revision_count").asInt()).isEqualTo(1);

        JsonNode log = ok(call("GET", mine, f.employee(), null), 200);
        assertThat(log.size()).isEqualTo(2);
        assertThat(log.get(0).get("reason").asString()).isEqualTo("A1");
        assertThat(log.get(1).get("reason").asString()).isEqualTo("B1");
        assertThat(log.get(1).get("revision_no").asInt()).isEqualTo(1);
    }

    /** The organisation-wide HOD lists, narrowed to the fixture employee (other tests leave sheets behind). */
    private JsonNode mine(JsonNode rows, Fixture f) {
        var out = json.createArrayNode();
        rows.forEach(r -> {
            if (f.employeeId().equals(r.get("employee_user_id").asString())) {
                out.add(r);
            }
        });
        return out;
    }

    // ── HOD approvals: goal sheets ───────────────────────────────────────────

    @Test
    void hodReviewsReturnsAndApprovesAGoalSheetAndEveryStepIsInTheHistory() throws Exception {
        Fixture f = fixture(false);
        String base = "/pms-goal-assignment";
        String yq = "?financial_year=" + FY;
        String toHod = "{\"financial_year\": \"%s\", \"employee_user_ids\": [\"%s\"]}".formatted(FY, f.employeeId());
        String ack = "{\"financial_year\": \"%s\"}".formatted(FY);
        String action = "{\"employee_user_id\": \"%s\", \"financial_year\": \"%s\", \"request_type\": "
                + "\"goal_sheet\", \"remarks\": \"%s\"}";

        sendTargets(f, "5800", "92");
        ok(call("POST", base + "/request-change", f.employee(), ("{\"financial_year\": \"%s\", \"kpi_id\": \"%s\", "
                + "\"proposed_target\": 5500, \"reason\": \"Shutdown\"}").formatted(FY, f.kpi1())), 200);
        sendTargets(f, "5600", "92");
        ok(call("POST", base + "/acknowledge", f.employee(), ack), 200);

        // nothing for the HOD until the manager sends the acknowledged sheet
        assertThat(mine(ok(call("GET", base + "/get/hod-queue" + yq, f.hod(), null), 200), f).size()).isZero();
        ok(call("POST", base + "/send-to-hod", f.manager(), toHod), 200);

        JsonNode item = mine(ok(call("GET", base + "/get/hod-queue" + yq, f.hod(), null), 200), f).get(0);
        assertThat(item.get("request_type").asString()).isEqualTo("goal_sheet");
        assertThat(item.get("changed").asBoolean()).isTrue();
        assertThat(item.get("first_change").get("from").decimalValue().intValue()).isEqualTo(5800);
        assertThat(item.get("first_change").get("to").decimalValue().intValue()).isEqualTo(5600);
        assertThat(item.get("revision_rounds").asInt()).isEqualTo(1);

        String review = base + "/get/hod-review" + yq + "&employee_user_id=" + f.employeeId()
                + "&request_type=goal_sheet";
        JsonNode open = ok(call("GET", review, f.hod(), null), 200);
        assertThat(open.get("employee_request").asString()).isEqualTo("Shutdown");
        assertThat(open.get("rows").get(0).get("approved_now").decimalValue().intValue()).isEqualTo(5800);
        assertThat(open.get("rows").get(0).get("requested").decimalValue().intValue()).isEqualTo(5600);
        assertThat(open.get("rows").get(0).get("changed").asBoolean()).isTrue();
        // a goal sheet is reviewed whole: every KPI is listed, the unchanged ones flagged as such
        assertThat(open.get("rows").size()).isEqualTo(2);
        assertThat(open.get("rows").get(1).get("changed").asBoolean()).isFalse();
        assertThat(open.get("rows").get(0).get("employee_requested").decimalValue().intValue()).isEqualTo(5500);
        assertThat(open.get("rows").get(0).get("expected_outcome").asString()).isNotBlank();
        assertThat(open.get("acknowledged_on").isNull()).isFalse();
        assertThat(open.get("sent_for_approval_on").isNull()).isFalse();

        // returning needs remarks; the manager revises, the employee acknowledges, the manager resends
        assertThat(code(call("POST", base + "/hod/return", f.hod(), action.formatted(f.employeeId(), FY, "")), 422))
                .isEqualTo("PMS_REMARKS_REQUIRED");
        ok(call("POST", base + "/hod/return", f.hod(), action.formatted(f.employeeId(), FY, "Too lenient")), 200);
        assertThat(mine(ok(call("GET", base + "/get/hod-queue" + yq, f.hod(), null), 200), f).size()).isZero();
        assertThat(code(call("POST", base + "/hod/approve", f.hod(), action.formatted(f.employeeId(), FY, "")), 422))
                .isEqualTo("PMS_NOT_AWAITING_HOD");
        sendTargets(f, "5700", "92");
        ok(call("POST", base + "/acknowledge", f.employee(), ack), 200);
        ok(call("POST", base + "/send-to-hod", f.manager(), toHod), 200);

        // bulk approve reports each sheet; a sheet that is not waiting fails without stopping the rest
        String bulk = "{\"financial_year\": \"%s\", \"items\": [{\"employee_user_id\": \"%s\", "
                + "\"request_type\": \"goal_sheet\"}, {\"employee_user_id\": \"%s\", \"request_type\": "
                + "\"goal_sheet\"}]}";
        JsonNode result = ok(call("POST", base + "/hod/approve-selected", f.hod(),
                bulk.formatted(FY, f.employeeId(), uuid())), 200);
        assertThat(result.get("approved").asInt()).isEqualTo(1);
        assertThat(result.get("failed").asInt()).isEqualTo(1);
        assertThat(result.get("results").get(0).get("success").asBoolean()).isTrue();
        assertThat(result.get("results").get(1).get("success").asBoolean()).isFalse();
        assertThat(ok(call("GET", base + "/get/my-goals" + yq, f.employee(), null), 200).get("goal_status").asString())
                .isEqualTo("approved");

        // history: the return and the approval, newest first; filters narrow it
        JsonNode history = mine(ok(call("GET", base + "/get/hod-history" + yq, f.hod(), null), 200), f);
        assertThat(history.size()).isEqualTo(2);
        assertThat(history.get(0).get("decision").asString()).isEqualTo("approved");
        assertThat(history.get(1).get("decision").asString()).isEqualTo("returned");
        assertThat(history.get(1).get("remarks").asString()).isEqualTo("Too lenient");
        assertThat(mine(ok(call("GET", base + "/get/hod-history" + yq + "&decision=returned", f.hod(), null), 200), f).size())
                .isEqualTo(1);
        assertThat(ok(call("GET", base + "/get/hod-history" + yq + "&search=zzzz", f.hod(), null), 200).size())
                .isZero();

        // trail: one goal-sheet request with every step, and the KPI that moved
        JsonNode trail = ok(call("GET", base + "/get/hod-trail" + yq + "&employee_user_id=" + f.employeeId(),
                f.hod(), null), 200);
        assertThat(trail.get("requests").size()).isEqualTo(1);
        assertThat(trail.get("requests").get(0).get("status").asString()).isEqualTo("approved");
        assertThat(trail.get("requests").get(0).get("events").size()).isGreaterThan(5);
        assertThat(trail.get("moves").size()).isGreaterThan(0);

        // a person with only the manager permission cannot use the HOD screens
        code(call("GET", base + "/get/hod-queue" + yq, f.manager(), null), 403);
    }

    // ── HOD approvals: mid-year target revision, KRA by KRA ──────────────────

    @Test
    void hodApprovesSomeKrasOfATargetRevisionAndReturnsTheRest() throws Exception {
        Fixture f = fixture(true);
        String base = "/pms-goal-assignment";
        String yq = "?financial_year=" + FY;
        report(f, "h1", "5180", "91", "");
        ok(call("POST", "/pms-mid-year/submit/review", f.manager(), """
                {"employee_user_id": "%s", "financial_year": "%s", "discussion_notes":
                 "Kiln-1 shutdown extended to 45 days", "revision_required": true}
                """.formatted(f.employeeId(), FY)), 200);
        ok(call("POST", "/pms-mid-year/submit/revision-request", f.manager(), """
                {"employee_user_id": "%s", "financial_year": "%s", "rows": [
                  {"kpi_id": "%s", "revised_target": 5450, "reason": "Shutdown extended to 45 days"},
                  {"kpi_id": "%s", "revised_target": 88, "reason": "Lower run factor during the shutdown"}]}
                """.formatted(f.employeeId(), FY, f.kpi1(), f.kpi2())), 200);

        JsonNode item = mine(ok(call("GET", base + "/get/hod-queue" + yq, f.hod(), null), 200), f).get(0);
        assertThat(item.get("request_type").asString()).isEqualTo("target_revision");
        assertThat(item.get("more_changes").asInt()).isEqualTo(1);
        assertThat(item.get("first_change").get("from").decimalValue().intValue()).isEqualTo(5800);
        assertThat(item.get("first_change").get("to").decimalValue().intValue()).isEqualTo(5450);

        String act = "{\"employee_user_id\": \"%s\", \"financial_year\": \"%s\", \"request_type\": "
                + "\"target_revision\", \"kpi_ids\": [\"%s\"], \"remarks\": \"%s\"}";
        ok(call("POST", base + "/hod/approve", f.hod(), act.formatted(f.employeeId(), FY, f.kpi1(), "Aligned with AOP")),
                200);
        // KRAs left unticked stay waiting
        JsonNode waiting = ok(call("GET", base + "/get/hod-review" + yq + "&employee_user_id=" + f.employeeId()
                + "&request_type=target_revision", f.hod(), null), 200);
        assertThat(waiting.get("rows").get(0).get("decision").asString()).isEqualTo("approved");
        assertThat(waiting.get("rows").get(1).get("decision").asString()).isEqualTo("pending");
        assertThat(mine(ok(call("GET", base + "/get/hod-queue" + yq, f.hod(), null), 200), f).size()).isEqualTo(1);

        assertThat(code(call("POST", base + "/hod/return", f.hod(),
                act.formatted(f.employeeId(), FY, f.kpi2(), "")), 422)).isEqualTo("PMS_REMARKS_REQUIRED");
        assertThat(code(call("POST", base + "/hod/return", f.hod(),
                act.formatted(f.employeeId(), FY, f.kpi1(), "x")), 422)).isEqualTo("PMS_KPI_NOT_AWAITING_HOD");
        ok(call("POST", base + "/hod/return", f.hod(), act.formatted(f.employeeId(), FY, f.kpi2(), "Keep 92")), 200);

        // the request is closed; only the approved KRA reaches the employee
        assertThat(mine(ok(call("GET", base + "/get/hod-queue" + yq, f.hod(), null), 200), f).size()).isZero();
        JsonNode mine = ok(call("GET", base + "/get/my-goals" + yq, f.employee(), null), 200);
        assertThat(mine.get("kpis").get(0).get("revised_target").decimalValue().intValue()).isEqualTo(5450);
        assertThat(mine.get("kpis").get(1).get("revised_target").isNull()).isTrue();
        assertThat(code(call("POST", base + "/hod/approve", f.hod(),
                ("{\"employee_user_id\": \"%s\", \"financial_year\": \"%s\", \"request_type\": "
                        + "\"target_revision\"}").formatted(f.employeeId(), FY)), 409))
                .isEqualTo("PMS_NOT_AWAITING_HOD");

        JsonNode history = mine(ok(call("GET", base + "/get/hod-history" + yq + "&request_type=target_revision", f.hod(),
                null), 200), f);
        assertThat(history.size()).isEqualTo(2);
        JsonNode trail = ok(call("GET", base + "/get/hod-trail" + yq + "&employee_user_id=" + f.employeeId(),
                f.hod(), null), 200);
        assertThat(trail.get("requests").get(0).get("request_type").asString()).isEqualTo("target_revision");
    }

    // ── 4.4 update progress: original and revised target, status, submit and freeze ──

    @Test
    void employeeUpdatesProgressSubmitsItAndTheManagerOnlyViewsIt() throws Exception {
        Fixture f = fixture(true);
        String sheet = "/pms-progress/get/my-progress?financial_year=" + FY + "&period=current";
        String put = "/pms-progress/update/my-progress";
        String body = """
                {"financial_year": "%s", "period": "current", "comment": "Coal feed issue", "submit": %s,
                 "entries": [{"kpi_id": "%s", "actual": 5540, "status": "on_track"},
                             {"kpi_id": "%s", "actual": %s, "status": %s}]}
                """;

        JsonNode open = ok(call("GET", sheet, f.employee(), null), 200);
        assertThat(open.get("state").asString()).isEqualTo("open");
        assertThat(open.get("kras").get(0).get("target").decimalValue().intValue()).isEqualTo(5800);
        assertThat(open.get("kras").get(0).get("revised_target").isNull()).isTrue();
        assertThat(open.get("kras").get(0).get("actual").isNull()).isTrue();

        // a draft keeps what was typed and stays open; a bad status is refused
        ok(call("PUT", put, f.employee(), body.formatted(FY, false, f.kpi1(), f.kpi2(), "93.4", "\"at_risk\"")), 200);
        assertThat(call("PUT", put, f.employee(), body.formatted(FY, false, f.kpi1(), f.kpi2(), "93.4",
                "\"great\"")).statusCode()).isGreaterThanOrEqualTo(400);
        JsonNode draft = ok(call("GET", sheet, f.employee(), null), 200);
        assertThat(draft.get("state").asString()).isEqualTo("open");
        assertThat(draft.get("kras").get(1).get("status").asString()).isEqualTo("at_risk");
        assertThat(draft.get("comment").asString()).isEqualTo("Coal feed issue");

        // submitting needs an actual and a status for every KPI
        assertThat(code(call("PUT", put, f.employee(), body.formatted(FY, true, f.kpi1(), f.kpi2(), "93.4", "null")),
                422)).isEqualTo("PMS_PROGRESS_INCOMPLETE");

        ok(call("PUT", put, f.employee(), body.formatted(FY, true, f.kpi1(), f.kpi2(), "93.4", "\"on_track\"")),
                200);
        assertThat(ok(call("GET", sheet, f.employee(), null), 200).get("state").asString()).isEqualTo("frozen");
        assertThat(code(call("PUT", put, f.employee(), body.formatted(FY, false, f.kpi1(), f.kpi2(), "90",
                "\"on_track\"")), 409)).isEqualTo("PMS_PROGRESS_FROZEN");

        // My Goals shows the figure as its own column; the target columns are untouched
        JsonNode mine = ok(call("GET", "/pms-goal-assignment/get/my-goals?financial_year=" + FY, f.employee(), null),
                200).get("kpis").get(0);
        assertThat(mine.get("employee_actual").decimalValue().intValue()).isEqualTo(5540);
        assertThat(mine.get("progress_status").asString()).isEqualTo("on_track");
        assertThat(mine.get("original_target").decimalValue().intValue()).isEqualTo(5800);
        assertThat(mine.get("revised_target").isNull()).isTrue();

        // the manager sees the same figure, with nothing to approve
        JsonNode theirs = ok(call("GET", "/pms-goal-assignment/get/employee-targets?employee_user_id="
                + f.employeeId() + "&financial_year=" + FY, f.manager(), null), 200).get("kpis").get(0);
        assertThat(theirs.get("employee_actual").decimalValue().intValue()).isEqualTo(5540);
        assertThat(code(call("PUT", put, f.manager(), body.formatted(FY, false, f.kpi1(), f.kpi2(), "1",
                "\"on_track\"")), 403)).isNotBlank();
    }

    // ── 4.6 self appraisal sheet ─────────────────────────────────────────────

    @Test
    void selfAppraisalSheetShowsTargetsAndProgressAndKeepsWhatTheEmployeeTyped() throws Exception {
        Fixture f = fixture(true);
        String sheet = "/pms-appraisal/get/self-appraisal?financial_year=" + FY;
        report(f, "current", "5540", "91", "");

        // before anything is saved: original target, the progress actual as a starting point, nothing rated
        JsonNode open = ok(call("GET", sheet, f.employee(), null), 200);
        assertThat(open.get("status").asString()).isEqualTo("draft");
        assertThat(open.get("kras").size()).isEqualTo(2);
        assertThat(open.get("kras").get(0).get("target").decimalValue().intValue()).isEqualTo(5800);
        assertThat(open.get("kras").get(0).get("revised_target").isNull()).isTrue();
        assertThat(open.get("kras").get(0).get("actual").decimalValue().intValue()).isEqualTo(5540);
        assertThat(open.get("kras").get(0).get("self_rating").isNull()).isTrue();
        assertThat(open.get("competencies").size()).isEqualTo(2);
        assertThat(open.get("competencies").get(0).get("type").asString()).isIn("hr", "manager");

        // a draft keeps the actual, the achievement text, the rating and the competency comment
        String comp1 = open.get("competencies").get(0).get("id").asString();
        String comp2 = open.get("competencies").get(1).get("id").asString();
        ok(call("PUT", "/pms-appraisal/update/self-appraisal", f.employee(), """
                {"financial_year": "%s", "submit": false,
                 "kras": [{"kpi_id": "%s", "rating": 4, "actual": 5600, "evidence": "Achieved despite shutdown",
                           "attachment_name": "DCS_Report.pdf"}],
                 "competencies": [{"competency_id": "%s", "rating": 3, "comment": "Needs work"}]}
                """.formatted(FY, f.kpi1(), comp1)), 200);
        JsonNode draft = ok(call("GET", sheet, f.employee(), null), 200);
        assertThat(draft.get("kras").get(0).get("actual").decimalValue().intValue()).isEqualTo(5600);
        assertThat(draft.get("kras").get(0).get("evidence").asString()).isEqualTo("Achieved despite shutdown");
        assertThat(draft.get("kras").get(0).get("attachment_name").asString()).isEqualTo("DCS_Report.pdf");
        assertThat(draft.get("kras").get(0).get("self_rating").asInt()).isEqualTo(4);
        assertThat(draft.get("competencies").get(0).get("comment").asString()).isEqualTo("Needs work");

        // submit needs every rating; then the sheet is submitted and read only
        assertThat(code(call("PUT", "/pms-appraisal/update/self-appraisal", f.employee(), """
                {"financial_year": "%s", "submit": true, "kras": [{"kpi_id": "%s", "rating": 4}],
                 "competencies": []}
                """.formatted(FY, f.kpi1())), 422)).isEqualTo("PMS_APPRAISAL_INCOMPLETE");
        ok(call("PUT", "/pms-appraisal/update/self-appraisal", f.employee(), """
                {"financial_year": "%s", "submit": true,
                 "kras": [{"kpi_id": "%s", "rating": 4}, {"kpi_id": "%s", "rating": 3, "actual": 91}],
                 "competencies": [{"competency_id": "%s", "rating": 3}, {"competency_id": "%s", "rating": 4}]}
                """.formatted(FY, f.kpi1(), f.kpi2(), comp1, comp2)), 200);
        assertThat(ok(call("GET", sheet, f.employee(), null), 200).get("status").asString()).isEqualTo("submitted");
        assertThat(code(call("PUT", "/pms-appraisal/update/self-appraisal", f.employee(), """
                {"financial_year": "%s", "submit": false, "kras": [], "competencies": []}
                """.formatted(FY)), 409)).isEqualTo("PMS_INVALID_GOAL_STATE");
    }

    // ── 3.2 create and assign the role template ──────────────────────────────

    @Test
    void managerAssignsTheRoleTemplateBeforeSettingTargets() throws Exception {
        Fixture f = fixture(false);
        String q = "/pms-goal-assignment/get/team?financial_year=" + FY;
        String targets = """
                {"employee_user_id": "%s", "financial_year": "%s", "targets": [
                  {"kpi_id": "%s", "weight": 60, "target": 5800}, {"kpi_id": "%s", "weight": 40, "target": 92}]}
                """.formatted(f.employeeId(), FY, f.kpi1(), f.kpi2());

        // before: no template is assigned, but the role's template is shown for the picker
        JsonNode member = ok(call("GET", q, f.manager(), null), 200).get(0);
        assertThat(member.get("template_assigned").asBoolean()).isFalse();
        String templateId = member.get("template_id").asString(); // the role's active template
        assertThat(member.get("template_name").asString()).startsWith("Engineer");
        assertThat(member.get("goal_status").asString()).isEqualTo("not_started");

        // targets cannot be saved until the template is assigned
        assertThat(code(call("PUT", "/pms-goal-assignment/update/employee-targets", f.manager(), targets), 409))
                .isEqualTo("PMS_TEMPLATE_NOT_ASSIGNED");

        // a role nobody in the team has, a manager with no team, an unknown template and a template of another
        // role are all refused
        String body = "{\"financial_year\": \"%s\", \"designation_id\": \"%s\", \"template_id\": \"%s\"}";
        assertThat(code(call("POST", "/pms-goal-assignment/assign-template", f.manager(),
                body.formatted(FY, uuid(), templateId)), 422)).isEqualTo("PMS_ROLE_NOT_IN_TEAM");
        String stranger = login(uuid(), "team_goals");
        assertThat(code(call("POST", "/pms-goal-assignment/assign-template", stranger,
                body.formatted(FY, f.roleId(), templateId)), 422)).isEqualTo("PMS_ROLE_NOT_IN_TEAM");
        assertThat(code(call("POST", "/pms-goal-assignment/assign-template", f.manager(),
                body.formatted(FY, f.roleId(), uuid())), 404)).isEqualTo("PMS_TEMPLATE_NOT_FOUND");
        assertThat(call("POST", "/pms-goal-assignment/assign-template", f.manager(),
                "{\"financial_year\": \"%s\", \"designation_id\": \"%s\"}".formatted(FY, f.roleId()))
                .statusCode()).isIn(400, 422); // template_id is required

        // assigning: one employee gets it; repeating assigns nobody new
        JsonNode first = assignTemplate(f);
        assertThat(first.get("assigned").asInt()).isEqualTo(1);
        assertThat(first.get("already_assigned").asInt()).isZero();
        assertThat(first.get("template_name").asString()).startsWith("Engineer");
        JsonNode again = assignTemplate(f);
        assertThat(again.get("assigned").asInt()).isZero();
        assertThat(again.get("already_assigned").asInt()).isEqualTo(1);

        // after: the template is assigned, the goal is still "not started", and targets can be set
        JsonNode after = ok(call("GET", q, f.manager(), null), 200).get(0);
        assertThat(after.get("template_assigned").asBoolean()).isTrue();
        assertThat(after.get("template_id").asString()).isEqualTo(templateId);
        assertThat(after.get("goal_status").asString()).isEqualTo("not_started");
        assertThat(ok(call("GET", "/pms-goal-assignment/get/employee-targets?employee_user_id=" + f.employeeId()
                + "&financial_year=" + FY, f.manager(), null), 200).get("goal_status").asString())
                .isEqualTo("not_started");

        ok(call("PUT", "/pms-goal-assignment/update/employee-targets", f.manager(), targets), 200);
        assertThat(ok(call("GET", q, f.manager(), null), 200).get(0).get("goal_status").asString())
                .isEqualTo("draft");
    }

    // ── 3.6 send to HOD, team statuses ───────────────────────────────────────

    @Test
    void sendToHodMovesOnlyAcknowledgedSheetsAndTheTeamListShowsRichStatuses() throws Exception {
        Fixture f = fixture(false);
        String q = "/pms-goal-assignment/get/team?financial_year=" + FY;

        JsonNode team = ok(call("GET", q, f.manager(), null), 200);
        assertThat(team.get(0).get("goal_status").asString()).isEqualTo("not_started");
        assertThat(team.get(0).get("template_name").asString()).startsWith("Engineer ");

        sendTargets(f, "5800", "92");
        // the employee asks for less and the manager revises: that KRA is what goes to the HOD
        ok(call("POST", "/pms-goal-assignment/request-change", f.employee(), """
                {"financial_year": "%s", "kpi_id": "%s", "proposed_target": 5500, "reason": "Shutdown"}
                """.formatted(FY, f.kpi1())), 200);
        sendTargets(f, "5650", "92");
        String body = "{\"financial_year\": \"%s\", \"employee_user_ids\": [\"%s\"]}".formatted(FY, f.employeeId());
        // sent to the employee but not acknowledged yet
        assertThat(code(call("POST", "/pms-goal-assignment/send-to-hod", f.manager(), body), 422))
                .isEqualTo("PMS_INVALID_GOAL_STATE");

        ok(call("POST", "/pms-goal-assignment/acknowledge", f.employee(), "{\"financial_year\": \"" + FY + "\"}"), 200);
        JsonNode sent = ok(call("POST", "/pms-goal-assignment/send-to-hod", f.manager(), body), 200);
        assertThat(sent.get(0).asString()).isEqualTo(f.employeeId());
        assertThat(ok(call("GET", q, f.manager(), null), 200).get(0).get("goal_status").asString())
                .isEqualTo("with_hod");

        // the HOD returns it: the manager sees "returned_by_hod" with the remarks on the target view
        ok(call("POST", "/pms-goal-assignment/return", f.hod(), """
                {"employee_user_id": "%s", "financial_year": "%s", "remarks": "Clinker target looks high"}
                """.formatted(f.employeeId(), FY)), 200);
        assertThat(ok(call("GET", q, f.manager(), null), 200).get(0).get("goal_status").asString())
                .isEqualTo("returned_by_hod");
        JsonNode targets = ok(call("GET", "/pms-goal-assignment/get/employee-targets?employee_user_id="
                + f.employeeId() + "&financial_year=" + FY, f.manager(), null), 200);
        assertThat(targets.get("hod_remarks").asString()).isEqualTo("Clinker target looks high");
        assertThat(targets.get("employee_name").asString()).isEqualTo("Suresh Babu");
        assertThat(targets.get("kpis").get(0).get("target_type").asString()).isEqualTo("common");

        // an employee outside the team is refused
        assertThat(code(call("POST", "/pms-goal-assignment/send-to-hod", f.manager(),
                "{\"financial_year\": \"%s\", \"employee_user_ids\": [\"%s\"]}".formatted(FY, uuid())), 403))
                .isEqualTo("PMS_NOT_IN_TEAM");
    }

    // ── 3.9 progress, 3.10 - 3.12 mid-year ───────────────────────────────────

    @Test
    void progressLocksUntilApprovalThenFeedsTheDashboardAndMidYearReview() throws Exception {
        Fixture f = fixture(false);
        sendTargets(f, "5800", "92");

        // goals are not approved yet: the row is locked and the employee cannot report progress
        JsonNode locked = ok(call("GET", "/pms-progress/get/team?financial_year=" + FY, f.manager(), null), 200);
        assertThat(locked.get(0).get("locked").asBoolean()).isTrue();
        assertThat(code(call("PUT", "/pms-progress/update/my-progress", f.employee(), """
                {"financial_year": "%s", "period": "current", "entries": [{"kpi_id": "%s", "actual": 1}]}
                """.formatted(FY, f.kpi1())), 409)).isEqualTo("PMS_GOALS_NOT_APPROVED");

        // nothing was revised, so acknowledging approves the sheet; the HOD has nothing to decide
        ok(call("POST", "/pms-goal-assignment/acknowledge", f.employee(), "{\"financial_year\": \"" + FY + "\"}"), 200);
        assertThat(code(call("POST", "/pms-goal-assignment/approve", f.hod(),
                "{\"employee_user_id\": \"%s\", \"financial_year\": \"%s\"}".formatted(f.employeeId(), FY)), 422))
                .isEqualTo("PMS_NOT_AWAITING_HOD");

        // 5000/5800 = 86.2% (off track), 93/92 = 101.1% (on track)
        report(f, "current", "5000", "93", "Coal feed issue 12-14 Jul");
        JsonNode row = ok(call("GET", "/pms-progress/get/team?financial_year=" + FY, f.manager(), null), 200).get(0);
        assertThat(row.get("locked").asBoolean()).isFalse();
        assertThat(row.get("kras_total").asInt()).isEqualTo(2);
        assertThat(row.get("kras_on_track").asInt()).isEqualTo(1);
        assertThat(row.get("attention").size()).isEqualTo(1);
        assertThat(row.get("attention").get(0).get("rag").asString()).isEqualTo("off_track");
        assertThat(row.get("comment").asString()).isEqualTo("Coal feed issue 12-14 Jul");
        assertThat(row.get("achievement").decimalValue().doubleValue()).isBetween(91.0, 93.0);

        // a KPI that is not on the sheet is refused
        assertThat(code(call("PUT", "/pms-progress/update/my-progress", f.employee(), """
                {"financial_year": "%s", "period": "current", "entries": [{"kpi_id": "%s", "actual": 1}]}
                """.formatted(FY, uuid())), 422)).isEqualTo("PMS_KPI_NOT_IN_TEMPLATE");

        // mid-year: H1 figures win over the running ones
        report(f, "h1", "5180", "91", "");
        String yq = "?financial_year=" + FY;
        JsonNode team = ok(call("GET", "/pms-mid-year/get/team" + yq, f.manager(), null), 200);
        assertThat(team.get(0).get("status").asString()).isEqualTo("not_started");
        String reviewQuery = "?employee_user_id=" + f.employeeId() + "&financial_year=" + FY;
        JsonNode review = ok(call("GET", "/pms-mid-year/get/review" + reviewQuery, f.manager(), null), 200);
        assertThat(review.get("kras").get(0).get("h1_actual").decimalValue().intValue()).isEqualTo(5180);

        // a revision needs notes
        String submit = "{\"employee_user_id\": \"%s\", \"financial_year\": \"%s\", \"discussion_notes\": \"%s\", "
                + "\"revision_required\": true}";
        assertThat(code(call("POST", "/pms-mid-year/submit/review", f.manager(),
                submit.formatted(f.employeeId(), FY, "short")), 422)).isEqualTo("PMS_NOTES_REQUIRED");
        ok(call("POST", "/pms-mid-year/submit/review", f.manager(),
                submit.formatted(f.employeeId(), FY, "Kiln-1 shutdown extended to 45 days")), 200);

        // the revision request: only changed targets, each with a reason
        String revision = "{\"employee_user_id\": \"%s\", \"financial_year\": \"%s\", \"attachment_name\": "
                + "\"Revised_AOP.pdf\", \"rows\": [{\"kpi_id\": \"%s\", \"revised_target\": %s, \"reason\": \"%s\"}]}";
        assertThat(code(call("POST", "/pms-mid-year/submit/revision-request", f.manager(),
                revision.formatted(f.employeeId(), FY, f.kpi1(), "5800", "Shutdown extended")), 422))
                .isEqualTo("PMS_REVISION_INVALID"); // same as the current target
        assertThat(code(call("POST", "/pms-mid-year/submit/revision-request", f.manager(),
                revision.formatted(f.employeeId(), FY, f.kpi1(), "5450", "no")), 422))
                .isEqualTo("PMS_REVISION_INVALID"); // reason too short
        JsonNode sent = ok(call("POST", "/pms-mid-year/submit/revision-request", f.manager(),
                revision.formatted(f.employeeId(), FY, f.kpi1(), "5450", "Shutdown extended to 45 days")), 200);
        assertThat(sent.get("status").asString()).isEqualTo("pending_hod");
        assertThat(sent.get("attachment_name").asString()).isEqualTo("Revised_AOP.pdf");
        assertThat(sent.get("kras").get(0).get("revised_target").decimalValue().intValue()).isEqualTo(5450);
        assertThat(sent.get("kras").get(0).get("weight").decimalValue().intValue()).isEqualTo(60);

        assertThat(ok(call("GET", "/pms-mid-year/get/team" + yq, f.manager(), null), 200).get(0).get("status")
                .asString()).isEqualTo("revision_requested");
        assertThat(code(call("POST", "/pms-mid-year/submit/revision-request", f.manager(),
                revision.formatted(f.employeeId(), FY, f.kpi1(), "5400", "Again please")), 409))
                .isEqualTo("PMS_REVISION_PENDING");
    }

    @Test
    void midYearWithNoRevisionCompletesTheReview() throws Exception {
        Fixture f = fixture(true);
        report(f, "h1", "5900", "93", "");
        ok(call("POST", "/pms-mid-year/submit/review", f.manager(), """
                {"employee_user_id": "%s", "financial_year": "%s", "discussion_notes": "", "revision_required": false}
                """.formatted(f.employeeId(), FY)), 200);
        JsonNode row = ok(call("GET", "/pms-mid-year/get/team?financial_year=" + FY, f.manager(), null), 200).get(0);
        assertThat(row.get("status").asString()).isEqualTo("completed_no_revision");
        assertThat(row.get("rag").asString()).isEqualTo("on_track");
    }

    // ── 3.13 - 3.14 appraisal ────────────────────────────────────────────────

    @Test
    void selfAppraisalThenManagerRatingFollowsTheValidationRules() throws Exception {
        Fixture f = fixture(true);
        report(f, "fy", "5540", "93", "");
        String yq = "?financial_year=" + FY;

        // before the self appraisal: waiting
        assertThat(ok(call("GET", "/pms-appraisal/get/team" + yq, f.manager(), null), 200).get(0)
                .get("manager_status").asString()).isEqualTo("waiting");
        String appraisalQuery = "?employee_user_id=" + f.employeeId() + "&financial_year=" + FY;
        assertThat(code(call("GET", "/pms-appraisal/get/manager-appraisal" + appraisalQuery, f.manager(), null), 409))
                .isEqualTo("PMS_SELF_APPRAISAL_PENDING");

        // incomplete self appraisal cannot be submitted
        String self = """
                {"financial_year": "%s", "submit": %s, "kras": [
                   {"kpi_id": "%s", "rating": 4, "attachment_name": "DCS_Report.pdf"}, {"kpi_id": "%s", "rating": %s}],
                 "competencies": [{"competency_id": "%s", "rating": 4}, {"competency_id": "%s", "rating": 5}]}
                """;
        assertThat(code(call("PUT", "/pms-appraisal/update/self-appraisal", f.employee(),
                self.formatted(FY, "true", f.kpi1(), f.kpi2(), "null", f.comp(), f.hrComp())), 422))
                .isEqualTo("PMS_APPRAISAL_INCOMPLETE");
        assertThat(code(call("PUT", "/pms-appraisal/update/self-appraisal", f.employee(),
                self.formatted(FY, "false", f.kpi1(), f.kpi2(), "9", f.comp(), f.hrComp())), 422))
                .isEqualTo("PMS_RATING_INVALID");
        JsonNode submitted = ok(call("PUT", "/pms-appraisal/update/self-appraisal", f.employee(),
                self.formatted(FY, "true", f.kpi1(), f.kpi2(), "3", f.comp(), f.hrComp())), 200);
        assertThat(submitted.get("status").asString()).isEqualTo("submitted");
        assertThat(submitted.get("self_score").decimalValue().doubleValue()).isEqualTo(4.0); // (4+3+4+5)/4

        JsonNode row = ok(call("GET", "/pms-appraisal/get/team" + yq, f.manager(), null), 200).get(0);
        assertThat(row.get("self_status").asString()).isEqualTo("submitted");
        assertThat(row.get("manager_status").asString()).isEqualTo("pending");

        // the manager's view: KRA actuals and self ratings, one manager competency and one HR competency
        JsonNode view = ok(call("GET", "/pms-appraisal/get/manager-appraisal" + appraisalQuery, f.manager(), null),
                200);
        assertThat(view.get("kras").size()).isEqualTo(2);
        assertThat(view.get("kras").get(0).get("actual").decimalValue().intValue()).isEqualTo(5540);
        assertThat(view.get("kras").get(0).get("self_rating").asInt()).isEqualTo(4);
        assertThat(view.get("kras").get(0).get("attachment_name").asString()).isEqualTo("DCS_Report.pdf");
        assertThat(view.get("competencies").size()).isEqualTo(1);
        assertThat(view.get("hr_competencies").size()).isEqualTo(1);
        assertThat(view.get("hr_competencies").get(0).get("self_rating").asInt()).isEqualTo(5);

        // draft: ratings may be left out
        String rate = """
                {"employee_user_id": "%s", "financial_year": "%s", "recommended_rating": %s, "comments": "Strong year",
                 "kras": [{"kpi_id": "%s", "rating": %s, "comment": "%s"}, {"kpi_id": "%s", "rating": 3}],
                 "competencies": [{"id": "%s", "rating": 4}]}
                """;
        JsonNode draft = ok(call("PUT", "/pms-appraisal/update/manager-appraisal", f.manager(),
                rate.formatted(f.employeeId(), FY, "null", f.kpi1(), "null", "", f.kpi2(), f.comp())), 200);
        assertThat(draft.get("status").asString()).isEqualTo("draft");

        // submit: missing KRA rating, then a wide gap without a comment, then valid
        assertThat(code(call("POST", "/pms-appraisal/submit/manager-appraisal", f.manager(),
                rate.formatted(f.employeeId(), FY, "4", f.kpi1(), "null", "", f.kpi2(), f.comp())), 422))
                .isEqualTo("PMS_APPRAISAL_INVALID");
        assertThat(code(call("POST", "/pms-appraisal/submit/manager-appraisal", f.manager(),
                rate.formatted(f.employeeId(), FY, "4", f.kpi1(), "1", "", f.kpi2(), f.comp())), 422))
                .isEqualTo("PMS_APPRAISAL_INVALID"); // 4 -> 1 needs an explanation
        assertThat(code(call("POST", "/pms-appraisal/submit/manager-appraisal", f.manager(),
                rate.formatted(f.employeeId(), FY, "null", f.kpi1(), "4", "", f.kpi2(), f.comp())), 422))
                .isEqualTo("PMS_APPRAISAL_INVALID"); // no recommended rating
        JsonNode done = ok(call("POST", "/pms-appraisal/submit/manager-appraisal", f.manager(),
                rate.formatted(f.employeeId(), FY, "4", f.kpi1(), "4", "", f.kpi2(), f.comp())), 200);
        assertThat(done.get("status").asString()).isEqualTo("submitted_to_hod");
        assertThat(done.get("recommended_rating").asInt()).isEqualTo(4);

        assertThat(ok(call("GET", "/pms-appraisal/get/team" + yq, f.manager(), null), 200).get(0)
                .get("manager_status").asString()).isEqualTo("submitted_to_hod");
        assertThat(code(call("PUT", "/pms-appraisal/update/manager-appraisal", f.manager(),
                rate.formatted(f.employeeId(), FY, "4", f.kpi1(), "4", "", f.kpi2(), f.comp())), 409))
                .isEqualTo("PMS_INVALID_GOAL_STATE");
    }

    // ── 1.7 - 1.9 Corporate HR: HR competency rating and calibration ─────────

    @Test
    void corporateHrRatesHrCompetenciesThenCalibratesAndPublishes() throws Exception {
        Fixture f = fixture(true);
        report(f, "fy", "5540", "93", "");
        String yq = "?financial_year=" + FY;
        String hrUser = login(uuid(), "rating_normalization");

        // an employee whose manager has not submitted is not in HR's queue; HR needs the right permission
        assertThat(ok(call("GET", "/pms-hr-appraisal/get/queue" + yq, hrUser, null), 200).size()).isZero();
        assertThat(call("GET", "/pms-hr-appraisal/get/queue" + yq, f.manager(), null).statusCode()).isEqualTo(403);

        // self appraisal, then the manager submits to the HOD
        ok(call("PUT", "/pms-appraisal/update/self-appraisal", f.employee(), """
                {"financial_year": "%s", "submit": true, "kras": [
                   {"kpi_id": "%s", "rating": 4}, {"kpi_id": "%s", "rating": 3}],
                 "competencies": [{"competency_id": "%s", "rating": 4}, {"competency_id": "%s", "rating": 5}]}
                """.formatted(FY, f.kpi1(), f.kpi2(), f.comp(), f.hrComp())), 200);
        ok(call("POST", "/pms-appraisal/submit/manager-appraisal", f.manager(), """
                {"employee_user_id": "%s", "financial_year": "%s", "recommended_rating": 4, "comments": "Strong",
                 "kras": [{"kpi_id": "%s", "rating": 4}, {"kpi_id": "%s", "rating": 3}],
                 "competencies": [{"id": "%s", "rating": 4}]}
                """.formatted(f.employeeId(), FY, f.kpi1(), f.kpi2(), f.comp())), 200);

        // 1.7: the employee is now in the queue, pending, with the snapshot details
        JsonNode queue = ok(call("GET", "/pms-hr-appraisal/get/queue" + yq, hrUser, null), 200);
        assertThat(queue.size()).isEqualTo(1);
        assertThat(queue.get(0).get("employee_user_id").asString()).isEqualTo(f.employeeId());
        assertThat(queue.get(0).get("status").asString()).isEqualTo("pending");
        assertThat(queue.get(0).get("department_id").asString()).isEqualTo(DEPT);
        assertThat(ok(call("GET", "/pms-hr-appraisal/get/queue" + yq + "&status=rated", hrUser, null), 200).size())
                .isZero();

        // 1.8: the HR competency is rateable, the manager competency is view only
        String employeeQuery = "?employee_user_id=" + f.employeeId() + "&financial_year=" + FY;
        JsonNode detail = ok(call("GET", "/pms-hr-appraisal/get/employee" + employeeQuery, hrUser, null), 200);
        assertThat(detail.get("hr_competencies").size()).isEqualTo(1);
        assertThat(detail.get("hr_competencies").get(0).get("self_rating").asInt()).isEqualTo(5);
        assertThat(detail.get("manager_competencies").size()).isEqualTo(1);
        assertThat(detail.get("manager_competencies").get(0).get("manager_rating").asInt()).isEqualTo(4);

        String save = """
                {"employee_user_id": "%s", "financial_year": "%s", "remarks": "Good attendance",
                 "competencies": [{"id": "%s", "rating": %s, "comment": "No lapses"}]}
                """;
        assertThat(code(call("PUT", "/pms-hr-appraisal/update/hr-ratings", hrUser,
                save.formatted(f.employeeId(), FY, f.hrComp(), "9")), 422)).isEqualTo("PMS_RATING_INVALID");
        assertThat(code(call("PUT", "/pms-hr-appraisal/update/hr-ratings", hrUser,
                save.formatted(f.employeeId(), FY, f.comp(), "4")), 422)).isEqualTo("PMS_COMPETENCY_NOT_IN_TEMPLATE");
        JsonNode draft = ok(call("PUT", "/pms-hr-appraisal/update/hr-ratings", hrUser,
                save.formatted(f.employeeId(), FY, f.hrComp(), "null")), 200);
        assertThat(draft.get("status").asString()).isEqualTo("draft");
        assertThat(code(call("POST", "/pms-hr-appraisal/submit/hr-ratings", hrUser,
                save.formatted(f.employeeId(), FY, f.hrComp(), "null")), 422)).isEqualTo("PMS_HR_RATING_INCOMPLETE");

        // sending before the ratings are submitted is refused
        String send = "{\"financial_year\": \"%s\", \"employee_user_ids\": [\"%s\"]}".formatted(FY, f.employeeId());
        assertThat(code(call("POST", "/pms-hr-appraisal/send/calibration", hrUser, send), 409))
                .isEqualTo("PMS_HR_RATING_PENDING");

        JsonNode rated = ok(call("POST", "/pms-hr-appraisal/submit/hr-ratings", hrUser,
                save.formatted(f.employeeId(), FY, f.hrComp(), "4")), 200);
        assertThat(rated.get("status").asString()).isEqualTo("rated");
        assertThat(rated.get("locked").asBoolean()).isFalse();
        assertThat(ok(call("GET", "/pms-hr-appraisal/get/queue" + yq + "&status=rated", hrUser, null), 200).size())
                .isEqualTo(1);

        // calibration is empty until the employee is sent
        String calibration = "/pms-calibration/get/calibration" + yq;
        assertThat(ok(call("GET", calibration, hrUser, null), 200).get("employees").size()).isZero();
        assertThat(ok(call("POST", "/pms-hr-appraisal/send/calibration", hrUser, send), 200).get("sent").asInt())
                .isEqualTo(1);
        assertThat(ok(call("GET", "/pms-hr-appraisal/get/employee" + employeeQuery, hrUser, null), 200)
                .get("locked").asBoolean()).isTrue();
        assertThat(code(call("PUT", "/pms-hr-appraisal/update/hr-ratings", hrUser,
                save.formatted(f.employeeId(), FY, f.hrComp(), "3")), 409)).isEqualTo("PMS_INVALID_GOAL_STATE");

        // 1.9: the distribution starts at the guideline target; the one employee sits at the manager's rating 4
        JsonNode view = ok(call("GET", calibration, hrUser, null), 200);
        assertThat(view.get("published").asBoolean()).isFalse();
        assertThat(view.get("employees").size()).isEqualTo(1);
        JsonNode emp = view.get("employees").get(0);
        assertThat(emp.get("manager_rating").asInt()).isEqualTo(4);
        assertThat(emp.get("hr_competency_avg").decimalValue().doubleValue()).isEqualTo(4.0);
        assertThat(emp.get("current_rating").asInt()).isEqualTo(4);
        JsonNode levels = view.get("distribution");
        assertThat(levels.size()).isEqualTo(5);
        assertThat(levels.get(0).get("rating").asInt()).isEqualTo(5);
        assertThat(levels.get(1).get("current_percent").asInt()).isEqualTo(100);
        assertThat(levels.get(2).get("hr_percent").asInt()).isEqualTo(60);

        // HR targets that do not total 100 block publishing
        String saveCal = """
                {"financial_year": "%s", "distribution": [
                   {"rating": 5, "hr_percent": 5}, {"rating": 4, "hr_percent": 20}, {"rating": 3, "hr_percent": %s},
                   {"rating": 2, "hr_percent": 10}, {"rating": 1, "hr_percent": 5}],
                 "employees": [{"employee_user_id": "%s", "calibrated_rating": %s, "reason": "%s"}]}
                """;
        JsonNode saved = ok(call("PUT", "/pms-calibration/update/calibration", hrUser,
                saveCal.formatted(FY, "55", f.employeeId(), "3", "Peer consistency")), 200);
        assertThat(saved.get("distribution").get(2).get("hr_percent").asInt()).isEqualTo(55);
        String publish = "{\"financial_year\": \"%s\"}".formatted(FY);
        assertThat(code(call("POST", "/pms-calibration/publish/calibration", hrUser, publish), 422))
                .isEqualTo("PMS_CALIBRATION_TOTAL");

        // a changed rating needs a reason, then it publishes and locks
        ok(call("PUT", "/pms-calibration/update/calibration", hrUser,
                saveCal.formatted(FY, "60", f.employeeId(), "3", "")), 200);
        assertThat(code(call("POST", "/pms-calibration/publish/calibration", hrUser, publish), 422))
                .isEqualTo("PMS_CALIBRATION_INVALID");
        ok(call("PUT", "/pms-calibration/update/calibration", hrUser,
                saveCal.formatted(FY, "60", f.employeeId(), "3", "Peer consistency")), 200);
        JsonNode published = ok(call("POST", "/pms-calibration/publish/calibration", hrUser, publish), 200);
        assertThat(published.get("published").asBoolean()).isTrue();
        assertThat(published.get("employees").get(0).get("calibrated_rating").asInt()).isEqualTo(3);
        assertThat(code(call("PUT", "/pms-calibration/update/calibration", hrUser,
                saveCal.formatted(FY, "60", f.employeeId(), "4", "")), 409)).isEqualTo("PMS_CALIBRATION_PUBLISHED");
    }

    // ── 3.8 transferred employee ─────────────────────────────────────────────

    @Test
    void transferredEmployeeGetsPeriodTwoKrasFromTheRoleTemplate() throws Exception {
        Fixture f = fixture(false);
        String q = "?financial_year=" + FY;

        // HR records the transfer; the earlier KRAs are frozen as period 1
        String record = """
                {"employee_user_id": "%s", "new_manager_user_id": "%s", "financial_year": "%s",
                 "joined_on": "2026-10-01", "from_department": "Raw Mill", "to_department": "Kiln Ops",
                 "previous_manager_name": "Srinivasa Rao B.", "previous_period_label": "Apr - Sep 2026",
                 "period_1_kras": [{"kra_name": "Raw mill output", "weight": 60, "target": 4200, "unit": "TPD"},
                                   {"kra_name": "Raw meal residue", "weight": 40, "target": 12, "unit": "%%"}]}
                """.formatted(f.employeeId(), f.managerId(), FY);
        ok(call("POST", "/pms-goal-transfer/create/transfer", f.hr(), record), 201);
        assertThat(code(call("POST", "/pms-goal-transfer/create/transfer", f.hr(), record), 409))
                .isEqualTo("PMS_TRANSFER_EXISTS");

        JsonNode notices = ok(call("GET", "/pms-goal-transfer/get/notices" + q, f.manager(), null), 200);
        assertThat(notices.size()).isEqualTo(1);
        assertThat(notices.get(0).get("from_department").asString()).isEqualTo("Raw Mill");
        assertThat(ok(call("GET", "/pms-goal-assignment/get/team" + q, f.manager(), null), 200).get(0)
                .get("goal_status").asString()).isEqualTo("transferred_in");

        // the sheet: two frozen period 1 rows, then the template's KPIs as period 2
        String sheetQuery = "?employee_user_id=" + f.employeeId() + "&financial_year=" + FY;
        JsonNode sheet = ok(call("GET", "/pms-goal-transfer/get/transfer-sheet" + sheetQuery, f.manager(), null), 200);
        JsonNode kras = sheet.get("kras");
        assertThat(kras.size()).isEqualTo(4);
        assertThat(kras.get(0).get("locked").asBoolean()).isTrue();
        assertThat(kras.get(0).get("period").asInt()).isEqualTo(1);
        assertThat(kras.get(2).get("locked").asBoolean()).isFalse();
        assertThat(kras.get(2).get("period_label").asString()).isEqualTo("Period 2 · Oct 2026 – Mar 2027");
        assertThat(sheet.get("previous").get("manager").asString()).isEqualTo("Srinivasa Rao B.");

        // someone else's transfer is not visible
        String other = login(uuid(), "team_goals");
        assertThat(code(call("GET", "/pms-goal-transfer/get/transfer-sheet" + sheetQuery, other, null), 403))
                .isEqualTo("PMS_NOT_IN_TEAM");

        // send validation: weights must total 100, targets set, reason and remarks given
        String period2 = """
                {"employee_user_id": "%s", "financial_year": "%s", "reason": "%s", "remarks": "%s", "kras": [
                  {"kra_name": "Clinker production", "weight": %s, "target": 5650, "unit": "TPD", "source": "template"},
                  {"kra_name": "Kiln-2 commissioning support", "weight": 40, "target": 100, "unit": "%% complete",
                   "source": "manager", "source_label": "Added by manager"}]}
                """;
        assertThat(code(call("POST", "/pms-goal-transfer/send/transfer-sheet", f.manager(),
                period2.formatted(f.employeeId(), FY, "Department transfer", "Transferred from Raw Mill", "50")), 422))
                .isEqualTo("PMS_TRANSFER_INVALID"); // 50 + 40 = 90
        assertThat(code(call("POST", "/pms-goal-transfer/send/transfer-sheet", f.manager(),
                period2.formatted(f.employeeId(), FY, "", "short", "60")), 422)).isEqualTo("PMS_TRANSFER_INVALID");

        JsonNode saved = ok(call("PUT", "/pms-goal-transfer/update/transfer-sheet", f.manager(),
                period2.formatted(f.employeeId(), FY, "", "", "50")), 200);
        assertThat(saved.get("status").asString()).isEqualTo("draft");
        JsonNode sentSheet = ok(call("POST", "/pms-goal-transfer/send/transfer-sheet", f.manager(),
                period2.formatted(f.employeeId(), FY, "Department transfer", "Transferred from Raw Mill to Kiln Ops",
                        "60")), 200);
        assertThat(sentSheet.get("status").asString()).isEqualTo("sent");
        assertThat(sentSheet.get("kras").size()).isEqualTo(4);
        assertThat(ok(call("GET", "/pms-goal-transfer/get/notices" + q, f.manager(), null), 200).size()).isZero();
        assertThat(code(call("PUT", "/pms-goal-transfer/update/transfer-sheet", f.manager(),
                period2.formatted(f.employeeId(), FY, "", "", "60")), 422)).isEqualTo("PMS_INVALID_GOAL_STATE");
    }

    // ── template status and duplicate ────────────────────────────────────────

    @Test
    void templateStatusAndDuplicate() throws Exception {
        Fixture f = fixture(false);
        String list = ok(call("GET", "/pms-goal-template/get/goal-templates?financial_year=" + FY + "&department_id="
                + DEPT, f.hr(), null), 200).toString();
        assertThat(list).contains(f.roleId());
        JsonNode templates = ok(call("GET", "/pms-goal-template/get/goal-templates?financial_year=" + FY
                + "&department_id=" + DEPT, f.hr(), null), 200);
        String id = null;
        for (JsonNode t : templates) {
            if (f.roleId().equals(t.get("role_id").asString())) {
                id = t.get("id").asString();
            }
        }
        assertThat(id).isNotNull();

        // duplicate: a draft "(Copy)" with the same rows
        JsonNode copy = ok(call("POST", "/pms-goal-template/duplicate/goal-template/" + id, f.hr(), null), 201);
        assertThat(copy.get("status").asString()).isEqualTo("draft");
        assertThat(copy.get("template_name").asString()).endsWith(" (Copy)");
        assertThat(copy.get("kras").get(0).get("kpis").size()).isEqualTo(2);
        assertThat(copy.get("competencies").size()).isEqualTo(2);
        String copyId = copy.get("id").asString();

        // the original is active, so the copy cannot be activated for the same role and year
        assertThat(code(call("PUT", "/pms-goal-template/update/goal-template/" + copyId + "/status", f.hr(),
                "{\"status\": \"active\"}"), 409)).isEqualTo("PMS_TEMPLATE_ROLE_CONFLICT");

        // deactivate the original, then the copy can take over
        assertThat(ok(call("PUT", "/pms-goal-template/update/goal-template/" + id + "/status", f.hr(),
                "{\"status\": \"inactive\"}"), 200).get("status").asString()).isEqualTo("inactive");
        assertThat(ok(call("PUT", "/pms-goal-template/update/goal-template/" + copyId + "/status", f.hr(),
                "{\"status\": \"active\"}"), 200).get("status").asString()).isEqualTo("active");

        // an incomplete template cannot be activated, and an unknown status is refused
        String bare = ok(call("POST", "/pms-goal-template/create/goal-template", f.hr(), """
                {"financial_year": "%s", "template_name": "Bare %s", "department_id": "%s", "role_id": "%s",
                 "effective_from": "2026-04-01"}
                """.formatted(FY, unique(), DEPT, uuid())), 201).get("id").asString();
        assertThat(code(call("PUT", "/pms-goal-template/update/goal-template/" + bare + "/status", f.hr(),
                "{\"status\": \"active\"}"), 409)).isEqualTo("PMS_TEMPLATE_INCOMPLETE");
        assertThat(code(call("PUT", "/pms-goal-template/update/goal-template/" + bare + "/status", f.hr(),
                "{\"status\": \"retired\"}"), 422)).isEqualTo("VALIDATION_ERROR");
    }

    // ── 4.1 / 4.5 my goals ───────────────────────────────────────────────────

    @Test
    void myGoalsCarriesWhoSentWhenAndTheRespondByDate() throws Exception {
        Fixture f = fixture(false);
        String q = "/pms-goal-assignment/get/my-goals?financial_year=" + FY;

        // nothing sent yet
        assertThat(code(call("GET", q, f.employee(), null), 404)).isEqualTo("PMS_GOALS_NOT_FOUND");

        // the active cycle's acknowledgement stage sets the respond-by date
        PmsCycleEntity cycle = cycles.save(PmsCycleEntity.builder()
                .organisationId(ORG)
                .cycleCode("MG-" + unique())
                .name("Annual " + unique())
                .type(PmsCycleType.ANNUAL)
                .periodStart(LocalDate.of(2026, 4, 1))
                .periodEnd(LocalDate.of(2027, 3, 31))
                .status(PmsCycleStatus.ACTIVE)
                .build());
        cycleStages.save(PmsCycleStageEntity.builder()
                .cycle(cycle)
                .stage(PmsCycleStageType.EMPLOYEE_ACKNOWLEDGEMENT)
                .startDate(LocalDate.of(2026, 5, 1))
                .endDate(LocalDate.of(2026, 5, 25))
                .build());

        sendTargets(f, "5800", "92");
        JsonNode goals = ok(call("GET", q, f.employee(), null), 200);
        assertThat(goals.get("goal_status").asString()).isEqualTo("sent_to_employee");
        assertThat(goals.get("manager_name").asString()).isNotBlank();
        assertThat(goals.get("sent_on").asString()).isEqualTo(LocalDate.now().toString());
        assertThat(goals.get("acknowledge_by").asString()).isEqualTo("2026-05-25");
        assertThat(goals.get("kpis").size()).isEqualTo(2);
        assertThat(goals.get("kpis").get(0).get("target").decimalValue().intValue()).isEqualTo(5800);
        assertThat(goals.get("kpis").get(0).get("revised_target").isNull()).isTrue();
        assertThat(goals.get("revision").isNull()).isTrue();
    }

    @Test
    void myGoalsShowsAnApprovedRevisionUntilTheEmployeeAcknowledgesIt() throws Exception {
        Fixture f = fixture(true);
        String q = "/pms-goal-assignment/get/my-goals?financial_year=" + FY;

        // no HOD approval endpoint yet: the approved revision is stored directly
        PmsGoalAssignmentEntity sheet = assignments
                .findByOrganisationIdAndEmployeeUserIdAndFinancialYear(ORG, f.employeeId(), FY).orElseThrow();
        PmsGoalAssignmentTargetEntity clinker = assignmentTargets.findByAssignmentId(sheet.getId()).stream()
                .filter(t -> t.getKpi().getId().toString().equals(f.kpi1())).findFirst().orElseThrow();
        PmsTargetRevisionEntity revision = revisions.save(PmsTargetRevisionEntity.builder()
                .assignment(sheet)
                .status(PmsRevisionStatus.APPROVED)
                .requestedBy(f.managerId())
                .requestedOn(LocalDateTime.now().minusDays(2))
                .approvedBy("K. Venkata Ramana")
                .approvedOn(LocalDateTime.now().minusDays(1))
                .build());
        revisionRows.save(PmsTargetRevisionRowEntity.builder()
                .revision(revision)
                .kpi(clinker.getKpi())
                .previousTarget(clinker.getTarget())
                .revisedTarget(new java.math.BigDecimal("5450"))
                .reason("Shutdown extended to 45 days")
                .build());

        JsonNode before = ok(call("GET", q, f.employee(), null), 200);
        assertThat(before.get("goal_status").asString()).isEqualTo("approved");
        assertThat(before.get("revision").get("kpi_id").asString()).isEqualTo(f.kpi1());
        assertThat(before.get("revision").get("approved_by").asString()).isEqualTo("K. Venkata Ramana");
        assertThat(before.get("revision").get("reason").asString()).isEqualTo("Shutdown extended to 45 days");
        assertThat(before.get("kpis").get(0).get("target").decimalValue().intValue()).isEqualTo(5800);
        assertThat(before.get("kpis").get(0).get("revised_target").decimalValue().intValue()).isEqualTo(5450);
        assertThat(before.get("kpis").get(1).get("revised_target").isNull()).isTrue();

        // acknowledging applies the revised target and clears the notice
        JsonNode after = ok(call("POST", "/pms-goal-assignment/acknowledge", f.employee(),
                "{\"financial_year\": \"" + FY + "\"}"), 200);
        assertThat(after.get("revision").isNull()).isTrue();
        assertThat(after.get("goal_status").asString()).isEqualTo("approved");
        assertThat(after.get("kpis").get(0).get("target").decimalValue().intValue()).isEqualTo(5450);

        // nothing left to acknowledge
        assertThat(code(call("POST", "/pms-goal-assignment/acknowledge", f.employee(),
                "{\"financial_year\": \"" + FY + "\"}"), 422)).isEqualTo("PMS_INVALID_GOAL_STATE");
    }

    @Test
    void managerRevisionAfterAChangeRequestIsShownToTheEmployeeToAcknowledge() throws Exception {
        Fixture f = fixture(false);
        String q = "/pms-goal-assignment/get/my-goals?financial_year=" + FY;
        sendTargets(f, "5800", "92");

        // an ordinary sent sheet has nothing to compare
        JsonNode first = ok(call("GET", q, f.employee(), null), 200);
        assertThat(first.get("revision").isNull()).isTrue();

        // the employee asks for a lower clinker target; the manager revises it and resends with a remark
        ok(call("POST", "/pms-goal-assignment/request-change", f.employee(), """
                {"financial_year": "%s", "kpi_id": "%s", "proposed_target": 5500,
                 "reason": "Kiln-1 shutdown planned in Q3"}
                """.formatted(FY, f.kpi1())), 200);
        // Save Draft first: it rewrites the targets, which must not lose what the employee last saw
        ok(call("PUT", "/pms-goal-assignment/update/employee-targets", f.manager(), """
                {"employee_user_id": "%s", "financial_year": "%s", "targets": [
                  {"kpi_id": "%s", "weight": 60, "target": 5450}, {"kpi_id": "%s", "weight": 40, "target": 92}]}
                """.formatted(f.employeeId(), FY, f.kpi1(), f.kpi2())), 200);
        ok(call("POST", "/pms-goal-assignment/send", f.manager(), """
                {"employee_user_id": "%s", "financial_year": "%s", "targets": [
                  {"kpi_id": "%s", "weight": 60, "target": 5450, "remarks": "Partially accepted: shutdown is 45 days"},
                  {"kpi_id": "%s", "weight": 40, "target": 92}]}
                """.formatted(f.employeeId(), FY, f.kpi1(), f.kpi2())), 200);

        JsonNode goals = ok(call("GET", q, f.employee(), null), 200);
        assertThat(goals.get("goal_status").asString()).isEqualTo("sent_to_employee");
        JsonNode revision = goals.get("revision");
        assertThat(revision.get("source").asString()).isEqualTo("manager_revised");
        assertThat(revision.get("kpi_id").asString()).isEqualTo(f.kpi1());
        assertThat(revision.get("reason").asString()).isEqualTo("Partially accepted: shutdown is 45 days");
        assertThat(revision.get("approved_by").asString()).isNotBlank();
        assertThat(goals.get("kpis").get(0).get("previous_target").decimalValue().intValue()).isEqualTo(5800);
        assertThat(goals.get("kpis").get(0).get("revised_target").decimalValue().intValue()).isEqualTo(5450);
        assertThat(goals.get("kpis").get(1).get("revised_target").isNull()).isTrue();

        // the manager's own view already holds the new target
        JsonNode managerView = ok(call("GET", "/pms-goal-assignment/get/employee-targets?employee_user_id="
                + f.employeeId() + "&financial_year=" + FY, f.manager(), null), 200);
        assertThat(managerView.get("kpis").get(0).get("target").decimalValue().intValue()).isEqualTo(5450);

        // acknowledging the sheet clears the revision notice
        JsonNode done = ok(call("POST", "/pms-goal-assignment/acknowledge", f.employee(),
                "{\"financial_year\": \"" + FY + "\"}"), 200);
        assertThat(done.get("goal_status").asString()).isEqualTo("acknowledged");
        assertThat(done.get("revision").isNull()).isTrue();
        assertThat(done.get("kpis").get(0).get("target").decimalValue().intValue()).isEqualTo(5450);
    }

    // ── manager -> HOD -> manager -> employee -> manager -> HOD loop ─────────

    @Test
    void hodReturnLoopRunsFromTheManagerThroughTheEmployeeAndBackToTheHod() throws Exception {
        Fixture f = fixture(false);
        String teamPath = "/pms-goal-assignment/get/team?financial_year=" + FY;
        String reviewPath = "/pms-goal-assignment/get/hod-review?employee_user_id=" + f.employeeId()
                + "&financial_year=" + FY + "&request_type=goal_sheet";
        sendTargets(f, "5800", "92");
        // a revised KRA is what makes the sheet go to the HOD
        ok(call("POST", "/pms-goal-assignment/request-change", f.employee(), """
                {"financial_year": "%s", "kpi_id": "%s", "proposed_target": 5500, "reason": "Shutdown"}
                """.formatted(FY, f.kpi1())), 200);
        sendTargets(f, "5700", "92");
        ok(call("POST", "/pms-goal-assignment/acknowledge", f.employee(), "{\"financial_year\": \"" + FY + "\"}"), 200);

        // the manager sends it on, with a note; the note is limited and the HOD sees nothing before this
        String toHod = "{\"financial_year\": \"%s\", \"employee_user_ids\": [\"%s\"], \"remarks\": \"%s\"}";
        assertThat(code(call("POST", "/pms-goal-assignment/send-to-hod", f.manager(),
                toHod.formatted(FY, f.employeeId(), "x".repeat(501))), 422)).isEqualTo("VALIDATION_ERROR");
        ok(call("POST", "/pms-goal-assignment/send-to-hod", f.manager(),
                toHod.formatted(FY, f.employeeId(), "Employee agreed the clinker target")), 200);
        assertThat(ok(call("GET", reviewPath, f.hod(), null), 200).get("goal_status").asString()).isEqualTo("with_hod");

        // the HOD returns it: a remark is required
        String act = "{\"employee_user_id\": \"%s\", \"financial_year\": \"%s\", \"request_type\": "
                + "\"goal_sheet\", \"remarks\": \"%s\"}";
        assertThat(code(call("POST", "/pms-goal-assignment/hod/return", f.hod(),
                act.formatted(f.employeeId(), FY, "")), 422)).isEqualTo("PMS_REMARKS_REQUIRED");
        ok(call("POST", "/pms-goal-assignment/hod/return", f.hod(),
                act.formatted(f.employeeId(), FY, "Clinker target is too low for the plan")), 200);
        assertThat(ok(call("GET", teamPath, f.manager(), null), 200).get(0).get("goal_status").asString())
                .isEqualTo("returned_by_hod");
        assertThat(code(call("POST", "/pms-goal-assignment/send-to-hod", f.manager(),
                toHod.formatted(FY, f.employeeId(), "")), 422)).isEqualTo("PMS_INVALID_GOAL_STATE");

        // the manager revises the target and resends it to the employee
        ok(call("POST", "/pms-goal-assignment/send", f.manager(), """
                {"employee_user_id": "%s", "financial_year": "%s", "targets": [
                  {"kpi_id": "%s", "weight": 60, "target": 6000, "remarks": "HOD asked for a higher target"},
                  {"kpi_id": "%s", "weight": 40, "target": 92}]}
                """.formatted(f.employeeId(), FY, f.kpi1(), f.kpi2())), 200);
        assertThat(ok(call("GET", teamPath, f.manager(), null), 200).get(0).get("goal_status").asString())
                .isEqualTo("sent_to_employee");
        JsonNode mine = ok(call("GET", "/pms-goal-assignment/get/my-goals?financial_year=" + FY, f.employee(), null),
                200);
        assertThat(mine.get("kpis").get(0).get("previous_target").decimalValue().intValue()).isEqualTo(5700);
        assertThat(mine.get("kpis").get(0).get("revised_target").decimalValue().intValue()).isEqualTo(6000);
        assertThat(mine.get("hod_remarks").isNull()).isTrue();

        // the employee asks for a change; the manager's draft is not mistaken for a sheet the HOD returned
        ok(call("POST", "/pms-goal-assignment/request-change", f.employee(), """
                {"financial_year": "%s", "kpi_id": "%s", "proposed_target": 5900, "reason": "Plan allows 5,900"}
                """.formatted(FY, f.kpi1())), 200);
        ok(call("PUT", "/pms-goal-assignment/update/employee-targets", f.manager(), """
                {"employee_user_id": "%s", "financial_year": "%s", "targets": [
                  {"kpi_id": "%s", "weight": 60, "target": 5950}, {"kpi_id": "%s", "weight": 40, "target": 92}]}
                """.formatted(f.employeeId(), FY, f.kpi1(), f.kpi2())), 200);
        assertThat(ok(call("GET", teamPath, f.manager(), null), 200).get(0).get("goal_status").asString())
                .isEqualTo("draft");

        // resend, the employee acknowledges, the manager sends to the HOD again, the HOD approves
        ok(call("POST", "/pms-goal-assignment/send", f.manager(), """
                {"employee_user_id": "%s", "financial_year": "%s", "targets": [
                  {"kpi_id": "%s", "weight": 60, "target": 5950, "remarks": "Settled between both asks"},
                  {"kpi_id": "%s", "weight": 40, "target": 92}]}
                """.formatted(f.employeeId(), FY, f.kpi1(), f.kpi2())), 200);
        ok(call("POST", "/pms-goal-assignment/acknowledge", f.employee(), "{\"financial_year\": \"" + FY + "\"}"), 200);
        ok(call("POST", "/pms-goal-assignment/send-to-hod", f.manager(),
                toHod.formatted(FY, f.employeeId(), "")), 200);
        JsonNode second = ok(call("GET", reviewPath, f.hod(), null), 200);
        assertThat(second.get("revision_rounds").asInt()).isGreaterThanOrEqualTo(2);
        ok(call("POST", "/pms-goal-assignment/hod/approve", f.hod(), act.formatted(f.employeeId(), FY, "")), 200);
        assertThat(ok(call("GET", teamPath, f.manager(), null), 200).get(0).get("goal_status").asString())
                .isEqualTo("approved");
    }

    @Test
    void aSheetNobodyRevisedIsApprovedByAcknowledgingAndNeverReachesTheHod() throws Exception {
        Fixture f = fixture(false);
        String q = "?financial_year=" + FY;
        sendTargets(f, "5800", "92");
        ok(call("POST", "/pms-goal-assignment/acknowledge", f.employee(), "{\"financial_year\": \"" + FY + "\"}"), 200);

        assertThat(ok(call("GET", "/pms-goal-assignment/get/my-goals" + q, f.employee(), null), 200)
                .get("goal_status").asString()).isEqualTo("approved");
        assertThat(ok(call("GET", "/pms-goal-assignment/get/hod-queue" + q, f.hod(), null), 200).size()).isZero();
        // nothing to send: the manager's button is refused and progress tracking is open
        assertThat(code(call("POST", "/pms-goal-assignment/send-to-hod", f.manager(),
                "{\"financial_year\": \"%s\", \"employee_user_ids\": [\"%s\"]}".formatted(FY, f.employeeId())), 422))
                .isEqualTo("PMS_INVALID_GOAL_STATE");
        report(f, "current", "5000", "93", "");
    }

    // ── security ─────────────────────────────────────────────────────────────

    @Test
    void manager_screens_require_their_permission() throws Exception {
        String nobody = login(uuid(), "my_goals");
        String yq = "?financial_year=" + FY;
        for (String path : List.of("/pms-progress/get/team" + yq, "/pms-mid-year/get/team" + yq,
                "/pms-appraisal/get/team" + yq, "/pms-goal-transfer/get/notices" + yq)) {
            assertThat(code(call("GET", path, nobody, null), 403)).as(path).isEqualTo("FORBIDDEN");
        }
        assertThat(call("GET", "/pms-progress/get/team" + yq, null, null).statusCode()).isEqualTo(401);
    }
}
