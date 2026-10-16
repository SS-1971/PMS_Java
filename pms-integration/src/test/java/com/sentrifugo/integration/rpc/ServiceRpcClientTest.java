package com.sentrifugo.integration.rpc;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpIOException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceRpcClientTest {

    // Mirrors the app's real config (spring.jackson.property-naming-strategy=SNAKE_CASE)
    // so payload/envelope field mapping is tested the way it actually runs.
    private final JsonMapper jsonMapper =
            JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final ServiceRpcClient client = new ServiceRpcClient(rabbitTemplate, jsonMapper, "staging");

    private static Message envelope(String json) {
        return new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }

    @Test
    void publishesToTheEnvironmentQualifiedQueueAndDecodesTheData() {
        when(rabbitTemplate.sendAndReceive(eq(""), anyString(), any(Message.class)))
                .thenReturn(envelope("""
                        {"correlation_id": "abc", "data": true, "error": null}
                        """));

        Boolean result = client.call(
                "iam", "permission_check", Map.of("module", "expense_management"),
                Duration.ofSeconds(5), Boolean.class);

        assertThat(result).isTrue();
        verify(rabbitTemplate).sendAndReceive(
                eq(""), eq("staging.iam.rpc.permission_check.queue"), any(Message.class));
        verify(rabbitTemplate).setReplyTimeout(5000L);
    }

    @Test
    void aNullReplyMeansTheCalleeNeverAnswered() {
        when(rabbitTemplate.sendAndReceive(eq(""), anyString(), any(Message.class))).thenReturn(null);

        assertThatThrownBy(() -> client.call("iam", "employee_lookup", Map.of(),
                Duration.ofSeconds(1), Boolean.class))
                .isInstanceOf(RpcTimeoutException.class);
    }

    @Test
    void anErrorEnvelopeIsNeverFlattenedIntoAnEmptyResult() {
        when(rabbitTemplate.sendAndReceive(eq(""), anyString(), any(Message.class)))
                .thenReturn(envelope("""
                        {"correlation_id": "abc", "data": null, "error": "db unavailable"}
                        """));

        assertThatThrownBy(() -> client.call("iam", "employee_lookup", Map.of(),
                Duration.ofSeconds(1), Boolean.class))
                .isInstanceOf(RpcErrorException.class)
                .hasMessageContaining("db unavailable");
    }

    @Test
    void aBrokerTransportFailureIsReportedAsUnavailable() {
        when(rabbitTemplate.sendAndReceive(eq(""), anyString(), any(Message.class)))
                .thenThrow(new AmqpIOException(new java.io.IOException("connection refused")));

        assertThatThrownBy(() -> client.call("iam", "employee_lookup", Map.of(),
                Duration.ofSeconds(1), Boolean.class))
                .isInstanceOf(RpcUnavailableException.class)
                .isNotInstanceOf(RpcTimeoutException.class);
    }
}
