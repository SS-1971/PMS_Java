package com.sentrifugo.integration.rpc;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Exercises {@link RpcServer#handleMessage} / {@code reply} directly — the part
 * that needs no real broker — the same way Python's {@code RpcServer} is tested
 * by calling its handler, not by standing up RabbitMQ.
 */
class RpcServerTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);

    private Message requestWithReplyTo(String json, String replyTo, String correlationId) {
        MessageProperties props = new MessageProperties();
        props.setReplyTo(replyTo);
        props.setCorrelationId(correlationId);
        return new Message(json.getBytes(StandardCharsets.UTF_8), props);
    }

    @Test
    void aSuccessfulHandlerProducesADataEnvelopeOnTheReplyToAddress() {
        RpcServer server = new RpcServer(connectionFactory, rabbitTemplate, jsonMapper,
                "staging.pms.rpc.echo.queue", payload -> payload.path("n").asInt() * 2, 5, "echo");

        server.handleMessage(requestWithReplyTo("""
                {"n": 21}
                """, "amq.rabbitmq.reply-to", "corr-1"));

        var bodyCaptor = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq(""), eq("amq.rabbitmq.reply-to"), bodyCaptor.capture());
        Message reply = bodyCaptor.getValue();
        assertThat(reply.getMessageProperties().getCorrelationId()).isEqualTo("corr-1");

        JsonNode envelope = jsonMapper.readTree(reply.getBody());
        assertThat(envelope.path("correlation_id").asString()).isEqualTo("corr-1");
        assertThat(envelope.path("data").asInt()).isEqualTo(42);
        assertThat(envelope.path("error").isNull()).isTrue();
    }

    @Test
    void aHandlerExceptionProducesAnErrorEnvelopeInsteadOfANullDataResult() {
        RpcServer server = new RpcServer(connectionFactory, rabbitTemplate, jsonMapper,
                "staging.pms.rpc.boom.queue", payload -> {
            throw new IllegalStateException("db unavailable");
        }, 5, "boom");

        server.handleMessage(requestWithReplyTo("{}", "amq.rabbitmq.reply-to", "corr-2"));

        var bodyCaptor = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq(""), eq("amq.rabbitmq.reply-to"), bodyCaptor.capture());
        JsonNode envelope = jsonMapper.readTree(bodyCaptor.getValue().getBody());
        assertThat(envelope.path("data").isNull()).isTrue();
        assertThat(envelope.path("error").asString()).contains("db unavailable");
    }

    @Test
    void aMessageWithNoReplyToIsDroppedRatherThanRepliedTo() {
        RpcServer server = new RpcServer(connectionFactory, rabbitTemplate, jsonMapper,
                "staging.pms.rpc.echo.queue", payload -> "unreachable", 5, "echo");

        MessageProperties props = new MessageProperties(); // no reply-to set
        server.handleMessage(new Message("{}".getBytes(StandardCharsets.UTF_8), props));

        verify(rabbitTemplate, never()).send(any(), any(), any(Message.class));
    }

    @Test
    void anUndecodableRequestBodyStillProducesAnErrorEnvelope() {
        RpcServer server = new RpcServer(connectionFactory, rabbitTemplate, jsonMapper,
                "staging.pms.rpc.echo.queue", payload -> "unreachable", 5, "echo");

        server.handleMessage(requestWithReplyTo("not json", "amq.rabbitmq.reply-to", "corr-3"));

        verify(rabbitTemplate, times(1)).send(eq(""), eq("amq.rabbitmq.reply-to"), any(Message.class));
    }
}
