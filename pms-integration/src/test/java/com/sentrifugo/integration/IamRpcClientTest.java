package com.sentrifugo.integration;

import com.sentrifugo.common.exception.DomainException;
import com.sentrifugo.integration.rpc.RpcTimeoutException;
import com.sentrifugo.integration.rpc.ServiceRpcClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IamRpcClientTest {

    private final ServiceRpcClient rpc = mock(ServiceRpcClient.class);
    private final IamRpcClient client = new IamRpcClient(rpc, 8000);

    @Test
    void reportsTrueWhenIamGrantsThePermission() {
        when(rpc.call(eq("iam"), eq("permission_check"), any(), any(Duration.class), eq(Boolean.class)))
                .thenReturn(true);

        assertThat(client.hasPermission("u-1", "o-1", "expense_management", "expense_l2_approval")).isTrue();
    }

    @Test
    void reportsFalseWhenIamDeniesThePermission() {
        when(rpc.call(eq("iam"), eq("permission_check"), any(), any(Duration.class), eq(Boolean.class)))
                .thenReturn(false);

        assertThat(client.hasPermission("u-1", "o-1", "expense_management", "expense_l2_approval")).isFalse();
    }

    @Test
    void iamUnavailableIsReportedAsServiceUnavailable() {
        when(rpc.call(eq("iam"), eq("permission_check"), any(), any(Duration.class), eq(Boolean.class)))
                .thenThrow(new RpcTimeoutException("timed out"));

        assertThatThrownBy(() -> client.hasPermission("u-1", "o-1", "expense_management", "expense_l2_approval"))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("IAM_UNAVAILABLE");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
    }
}
