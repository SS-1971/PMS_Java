package com.sentrifugo.messaging.iam;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares PMS's inbound queue on IAM's {@code domain_events} topic exchange,
 * bound to the business-unit / department / designation routing keys IAM
 * already publishes (see IAM's {@code businessunit,department,designation}
 * {@code utils/tools.py}) — the same replica-sync pattern leave-management's
 * {@code declare_iam_infrastructure} uses, minus the two legacy "synced"
 * queues that only exist on IAM's side for a consumer nothing subscribes to
 * anymore.
 *
 * Declaration is idempotent and parameter-for-parameter matches IAM's own
 * {@code declare_domain_events_infrastructure} (durable topic exchange,
 * durable queue) — a mismatch here would fail the channel with
 * PRECONDITION_FAILED on every connect.
 */
@Configuration
public class IamDomainEventsConfig {

    private static final String[] ROUTING_KEYS = {
            "business_unit.created", "business_unit.updated", "business_unit.deleted",
            "department.created", "department.updated", "department.deleted",
            "designation.created", "designation.updated", "designation.deleted",
    };

    @Bean
    public TopicExchange domainEventsExchange() {
        return new TopicExchange(IamMessagingNames.DOMAIN_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue pmsDeadLetterQueue(IamMessagingNames names) {
        return QueueBuilder.durable(names.deadLetterQueue()).build();
    }

    @Bean
    public Queue pmsDomainEventsQueue(IamMessagingNames names) {
        return QueueBuilder.durable(names.domainEventsQueue())
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", names.deadLetterQueue())
                .build();
    }

    @Bean
    public Declarables pmsDomainEventsBindings(Queue pmsDomainEventsQueue, TopicExchange domainEventsExchange) {
        Binding[] bindings = new Binding[ROUTING_KEYS.length];
        for (int i = 0; i < ROUTING_KEYS.length; i++) {
            bindings[i] = BindingBuilder.bind(pmsDomainEventsQueue).to(domainEventsExchange).with(ROUTING_KEYS[i]);
        }
        return new Declarables(bindings);
    }
}
