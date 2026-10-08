package com.sentrifugo.integration;

import com.sentrifugo.common.exception.DomainException;
import com.sentrifugo.integration.rpc.RpcTimeoutException;
import com.sentrifugo.integration.rpc.ServiceRpcClient;
import com.sentrifugo.integration.model.DirectReportsRequest;
import com.sentrifugo.integration.model.IamEmployee;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.core.type.TypeReference;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IamEmployeeClientTest {

    private final ServiceRpcClient rpc = mock(ServiceRpcClient.class);
    private final IamEmployeeClient client = new IamEmployeeClient(rpc, 8000);

    @Test
    void directReportsAreRequestedByManagerAndOrganisationAndReturned() {
        IamEmployee suresh = new IamEmployee(
                "u-1", "SCIL-3", "Suresh Babu", "N.", "suresh@example.com", "dep-1", "d-1", "Engineer");
        when(rpc.call(eq("iam"), eq("direct_reports"), any(), any(Duration.class), any(TypeReference.class)))
                .thenReturn(List.of(suresh));

        List<IamEmployee> rows = client.directReports("org-1", "m-1");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).userId()).isEqualTo("u-1");
        assertThat(rows.get(0).empCode()).isEqualTo("SCIL-3");
        assertThat(rows.get(0).firstName()).isEqualTo("Suresh Babu");
        assertThat(rows.get(0).workEmail()).isEqualTo("suresh@example.com");

        var requestCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(rpc).call(eq("iam"), eq("direct_reports"), requestCaptor.capture(), any(Duration.class),
                any(TypeReference.class));
        assertThat(requestCaptor.getValue()).isEqualTo(new DirectReportsRequest("m-1", "org-1"));
    }

    @Test
    void anEmptyTeamIsReturnedAsAnEmptyListNotNull() {
        when(rpc.call(eq("iam"), eq("direct_reports"), any(), any(Duration.class), any(TypeReference.class)))
                .thenReturn(null);

        assertThat(client.directReports("org-1", "m-1")).isEmpty();
    }

    @Test
    void iamRpcFailingIsReportedAsServiceUnavailable() {
        when(rpc.call(eq("iam"), eq("direct_reports"), any(), any(Duration.class), any(TypeReference.class)))
                .thenThrow(new RpcTimeoutException("timed out"));

        assertThatThrownBy(() -> client.directReports("org-1", "m-1"))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("IAM_UNAVAILABLE");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
    }
}
