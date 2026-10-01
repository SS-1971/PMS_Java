package com.sentrifugo.messaging.iam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.NullNode;

/**
 * Direct Reply-To RPC client for IAM — Java counterpart of the Python
 * services' {@code iam_rpc.py} / {@code lms_rpc.py} clients, built on Spring
 * AMQP's native Direct Reply-To support instead of hand-rolling the
 * consume+publish dance those modules do.
 *
 * IAM replies with {@code {"correlation_id", "data": <result|null>, "error": <str|null>}};
 * {@code error} is distinguished from an empty {@code data} so a broker/handler
 * failure never silently reads as "there is nothing".
 */
@Component
public class IamRpcClient {

    private static final Logger log = LoggerFactory.getLogger(IamRpcClient.class);

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final IamMessagingNames names;

    public IamRpcClient(RabbitTemplate iamRpcTemplate, ObjectMapper objectMapper, IamMessagingNames names) {
        this.rabbitTemplate = iamRpcTemplate;
        this.objectMapper = objectMapper;
        this.names = names;
    }

    /**
     * Calls IAM's {@code {queueName}} RPC queue (unqualified, e.g.
     * {@code "eligible_employees_preview"}) and returns the envelope's
     * {@code data} node.
     *
     * @throws IamUnavailableException the broker rejected the publish
     * @throws IamTimeoutException     no reply within the configured timeout
     * @throws IamErrorException       IAM replied with an {@code error}, or an
     *                                 undecodable / non-object envelope
     */
    public JsonNode call(String queueName, Object payload) {
        String routingKey = names.iamRpcQueue(queueName);
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(payload);
        } catch (Exception e) {
            throw new IamErrorException("Failed to serialise RPC payload: " + e.getMessage());
        }

        MessageProperties properties = new MessageProperties();
        properties.setContentType("application/json");
        Message request = new Message(body, properties);

        Message reply;
        try {
            reply = rabbitTemplate.sendAndReceive("", routingKey, request);
        } catch (AmqpException e) {
            throw new IamUnavailableException("IAM RPC transport error on " + routingKey + ": " + e.getMessage(), e);
        }

        if (reply == null) {
            throw new IamTimeoutException("IAM RPC to " + routingKey + " timed out");
        }

        JsonNode envelope;
        try {
            envelope = objectMapper.readTree(reply.getBody());
        } catch (Exception e) {
            throw new IamErrorException("IAM RPC to " + routingKey + " returned an undecodable reply");
        }
        if (envelope == null || !envelope.isObject()) {
            throw new IamErrorException("IAM RPC to " + routingKey + " returned a non-object envelope");
        }
        JsonNode error = envelope.get("error");
        if (error != null && !error.isNull()) {
            throw new IamErrorException("IAM RPC to " + routingKey + " failed: " + error.asText());
        }
        JsonNode data = envelope.get("data");
        return data == null ? NullNode.getInstance() : data;
    }
}
