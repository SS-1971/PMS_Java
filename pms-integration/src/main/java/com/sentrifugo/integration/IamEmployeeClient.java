package com.sentrifugo.integration;

import com.sentrifugo.common.exception.DomainException;
import com.sentrifugo.integration.rpc.RpcUnavailableException;
import com.sentrifugo.integration.rpc.ServiceRpcClient;
import com.sentrifugo.integration.model.DirectReportsRequest;
import com.sentrifugo.integration.model.IamEmployee;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

import java.time.Duration;
import java.util.List;

/**
 * Reads a manager's direct reports from IAM over RabbitMQ RPC — IAM's
 * {@code direct_reports} Direct Reply-To queue — instead of the REST
 * {@code GET /employees/?l1_manager_id=} route this used to call.
 *
 * <p>IAM resolves the team itself from {@code manager_id} + {@code organisation_id},
 * so there is no caller token to forward or check here: the same trust boundary
 * every other service's IAM RPC call relies on (see {@code permission_check},
 * {@code reporting_line}, {@code employee_lookup} and their Python clients).
 * {@link ServiceRpcClient} does the Direct Reply-To plumbing; this class only
 * knows IAM's {@code direct_reports} procedure name and payload/reply shape.
 */
@Slf4j
@Component
public class IamEmployeeClient {

    private final ServiceRpcClient rpc;
    private final Duration timeout;

    @Autowired
    public IamEmployeeClient(ServiceRpcClient rpc, @Value("${pms.iam-rpc.reply-timeout-ms:8000}") long timeoutMs) {
        this.rpc = rpc;
        this.timeout = Duration.ofMillis(timeoutMs);
    }

    /** Direct reports of {@code managerUserId}, scoped to {@code organisationId}. */
    public List<IamEmployee> directReports(String organisationId, String managerUserId) {
        var request = new DirectReportsRequest(managerUserId, organisationId);
        try {
            List<IamEmployee> rows = rpc.call("iam", "direct_reports", request, timeout,
                    new TypeReference<List<IamEmployee>>() {
                    });
            return rows == null ? List.of() : rows;
        } catch (RpcUnavailableException e) {
            log.warn("IAM direct_reports RPC failed: {}", e.getMessage());
            throw new DomainException("The employee directory is unavailable. Try again shortly.",
                    "IAM_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}
