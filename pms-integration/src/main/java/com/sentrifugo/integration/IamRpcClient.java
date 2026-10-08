package com.sentrifugo.integration;

import com.sentrifugo.common.exception.DomainException;
import com.sentrifugo.integration.model.PermissionCheckRequest;
import com.sentrifugo.integration.rpc.RpcUnavailableException;
import com.sentrifugo.integration.rpc.ServiceRpcClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Calls IAM over RabbitMQ RPC instead of HTTP — the same shape as
 * {@link IamEmployeeClient}, but for IAM procedures that are only exposed as a
 * Direct Reply-To queue, not a REST route.
 *
 * <p>This is the Java side of the suite's shared RPC pattern (see every Python
 * service's {@code rabbitmq/iam_rpc.py} / {@code clients/iam_rpc.py} plus IAM's
 * own {@code rpc/*.py} handlers) — {@link ServiceRpcClient} does the Direct
 * Reply-To plumbing; this class only knows IAM's procedure names and payload
 * shapes, same division of labour as the Python {@code iam_rpc.py} wrappers.
 */
@Slf4j
@Component
public class IamRpcClient {

    private final ServiceRpcClient rpc;
    private final Duration timeout;

    @Autowired
    public IamRpcClient(ServiceRpcClient rpc, @Value("${pms.iam-rpc.reply-timeout-ms:8000}") long timeoutMs) {
        this.rpc = rpc;
        this.timeout = Duration.ofMillis(timeoutMs);
    }

    /**
     * Does {@code employeeId} hold {@code permissionCode} under {@code module}?
     *
     * <p>Mirrors IAM's {@code rpc/permission_check.py} exactly: deny by default for
     * an unknown user or one with no matching policy; org/super admins answer true,
     * because they bypass permission checks at the enforcing endpoint too.
     *
     * @throws DomainException {@code IAM_UNAVAILABLE} (503) if the broker is down,
     *         IAM's handler raised, or no reply arrived within the configured timeout
     */
    public boolean hasPermission(String employeeId, String organisationId, String module, String permissionCode) {
        var request = new PermissionCheckRequest(employeeId, organisationId, module, permissionCode);
        try {
            Boolean result = rpc.call("iam", "permission_check", request, timeout, Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (RpcUnavailableException e) {
            log.warn("IAM permission_check RPC failed: {}", e.getMessage());
            throw new DomainException("Could not verify permissions right now. Try again shortly.",
                    "IAM_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}
