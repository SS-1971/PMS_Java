package com.sentrifugo.integration.rpc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

/**
 * Java-side counterpart to the Python suite's Direct Reply-To RPC client (each
 * Python service's {@code rpc_call()} / {@code iam_rpc.py} / {@code lms_rpc.py}
 * all hand-roll the identical pattern).
 *
 * <p>Calls a request/reply queue named {@code {environment}.{service}.rpc.{procedure}.queue}
 * over the default exchange — no binding needed, the callee already declared a
 * durable queue with exactly this name — and decodes the envelope every Python
 * {@code RpcServer} replies with:
 * {@code {"correlation_id": "...", "data": <result | null>, "error": <string | null>}}.
 *
 * <p>Correlation and the reply address are handled by
 * {@link RabbitTemplate#sendAndReceive(String, String, Message)} itself: with no
 * reply queue configured on the template, it publishes to the broker's
 * {@code amq.rabbitmq.reply-to} pseudo-queue and matches the reply by the AMQP
 * {@code correlation-id} property — exactly what the Python clients build by hand
 * with {@code basic_consume} on that same pseudo-queue and an {@code asyncio.Future}.
 * Spring blocks the calling thread instead of awaiting a future, which is the one
 * real difference: this is a synchronous call, not a fire-and-await-later one.
 *
 * <p>Uses Jackson 3 ({@code tools.jackson.*}), not the classic Jackson 2
 * {@code com.fasterxml.jackson.databind.ObjectMapper}: this is a Spring Boot 4
 * app, and {@code JacksonAutoConfiguration} only registers a {@link JsonMapper}
 * bean by default (a plain Jackson 2 {@code ObjectMapper} bean does not exist
 * here unless {@code spring.http.converters.preferred-json-mapper=jackson2} is
 * set), so this is also the mapper every REST endpoint in the app already
 * serialises through — RPC payloads get the same
 * {@code spring.jackson.property-naming-strategy=SNAKE_CASE} behaviour for free.
 */
@Slf4j
@Component
public class ServiceRpcClient {

    private final RabbitTemplate rabbitTemplate;
    private final JsonMapper jsonMapper;
    private final String environment;

    public ServiceRpcClient(
            RabbitTemplate rabbitTemplate,
            JsonMapper jsonMapper,
            @Value("${pms.rpc.environment:production}") String environment) {
        this.rabbitTemplate = rabbitTemplate;
        this.jsonMapper = jsonMapper;
        this.environment = environment;
    }

    /** Calls {@code {environment}.{service}.rpc.{procedure}.queue} and decodes {@code data} as {@code dataType}. */
    public <T> T call(String service, String procedure, Object payload, Duration timeout, Class<T> dataType) {
        return call(service, procedure, payload, timeout, jsonMapper.getTypeFactory().constructType(dataType));
    }

    /** Overload for a generic reply shape, e.g. {@code new TypeReference<Map<String, Boolean>>() {}}. */
    public <T> T call(String service, String procedure, Object payload, Duration timeout, TypeReference<T> dataType) {
        return call(service, procedure, payload, timeout, jsonMapper.getTypeFactory().constructType(dataType));
    }

    private <T> T call(String service, String procedure, Object payload, Duration timeout, JavaType dataType) {
        String routingKey = "%s.%s.rpc.%s.queue".formatted(environment, service, procedure);

        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        Message request = new Message(serialise(payload, routingKey), properties);

        Message reply;
        // RabbitTemplate's reply timeout lives on the template instance, not as a
        // sendAndReceive() argument, so concurrent callers sharing this client must
        // not race on it between the set and the call. The RPC itself already
        // blocks on a network round trip, so serialising this pair costs nothing
        // extra in practice.
        synchronized (rabbitTemplate) {
            rabbitTemplate.setReplyTimeout(timeout.toMillis());
            try {
                reply = rabbitTemplate.sendAndReceive("", routingKey, request);
            } catch (AmqpException e) {
                throw new RpcUnavailableException("RPC transport error calling " + routingKey, e);
            }
        }

        if (reply == null) {
            throw new RpcTimeoutException("RPC to " + routingKey + " timed out after " + timeout);
        }
        return decode(reply, routingKey, dataType);
    }

    private byte[] serialise(Object payload, String routingKey) {
        try {
            return jsonMapper.writeValueAsBytes(payload);
        } catch (JacksonException e) {
            throw new RpcUnavailableException("Could not serialise RPC payload for " + routingKey, e);
        }
    }

    private <T> T decode(Message reply, String routingKey, JavaType dataType) {
        RpcEnvelope envelope;
        try {
            envelope = jsonMapper.readValue(reply.getBody(), RpcEnvelope.class);
        } catch (JacksonException e) {
            throw new RpcErrorException("RPC to " + routingKey + " returned an undecodable reply", e);
        }
        if (envelope.error() != null) {
            throw new RpcErrorException("RPC to " + routingKey + " failed: " + envelope.error());
        }

        JsonNode data = envelope.data();
        if (data == null || data.isNull()) {
            return null;
        }
        try {
            return jsonMapper.convertValue(data, dataType);
        } catch (IllegalArgumentException e) {
            throw new RpcErrorException(
                    "RPC to " + routingKey + " returned a reply shaped unlike " + dataType, e);
        }
    }
}
