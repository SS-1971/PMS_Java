package com.sentrifugo.messaging.iam;

import com.sentrifugo.common.event.DepartmentDeletedEvent;
import com.sentrifugo.common.event.DepartmentSyncedEvent;
import com.sentrifugo.common.event.DesignationDeletedEvent;
import com.sentrifugo.common.event.DesignationSyncedEvent;
import com.sentrifugo.common.event.PlantDeletedEvent;
import com.sentrifugo.common.event.PlantSyncedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Consumes IAM's {@code business_unit.*} / {@code department.*} /
 * {@code designation.*} domain events and republishes them as typed Spring
 * application events — persistence of the replica tables is pms-app's
 * concern (see its {@code ReplicaSyncListener}), kept out of pms-messaging so
 * this module stays transport-only and never depends on pms-db.
 *
 * A message this handler can't even parse is logged and dropped (acked) —
 * requeuing a permanently-malformed message loops it forever. A failure
 * further downstream (the {@code @EventListener} persisting it) propagates
 * back through Spring AMQP's default error handling, which nacks and
 * requeues — appropriate since that failure is more likely transient (a DB
 * blip) than a bad message.
 */
@Component
public class IamDomainEventsListener {

    private static final Logger log = LoggerFactory.getLogger(IamDomainEventsListener.class);

    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;

    public IamDomainEventsListener(ObjectMapper objectMapper, ApplicationEventPublisher events) {
        this.objectMapper = objectMapper;
        this.events = events;
    }

    @RabbitListener(queues = "#{@pmsDomainEventsQueue}")
    public void onMessage(@Payload byte[] body, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        JsonNode payload;
        try {
            payload = objectMapper.readTree(body);
        } catch (Exception e) {
            log.warn("domain_events: dropping unparseable message routingKey={} error={}", routingKey, e.getMessage());
            return;
        }

        switch (routingKey) {
            case "business_unit.created", "business_unit.updated" -> events.publishEvent(new PlantSyncedEvent(
                    text(payload, "business_unit_id"), text(payload, "organisation_id"),
                    text(payload, "name"), bool(payload, "is_active", true)));
            case "business_unit.deleted" -> events.publishEvent(new PlantDeletedEvent(text(payload, "business_unit_id")));

            case "department.created", "department.updated" -> events.publishEvent(new DepartmentSyncedEvent(
                    text(payload, "department_id"), text(payload, "organisation_id"),
                    text(payload, "name"), bool(payload, "is_active", true)));
            case "department.deleted" -> events.publishEvent(new DepartmentDeletedEvent(text(payload, "department_id")));

            case "designation.created", "designation.updated" -> events.publishEvent(new DesignationSyncedEvent(
                    text(payload, "designation_id"), text(payload, "organisation_id"),
                    text(payload, "name"), text(payload, "department_id"), bool(payload, "is_active", true)));
            case "designation.deleted" -> events.publishEvent(new DesignationDeletedEvent(text(payload, "designation_id")));

            default -> log.warn("domain_events: unhandled routing key '{}' (queue is over-bound?)", routingKey);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asText();
    }

    private static boolean bool(JsonNode node, String field, boolean fallback) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? fallback : value.asBoolean(fallback);
    }
}
