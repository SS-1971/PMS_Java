package com.sentrifugo.messaging.iam;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Queue / exchange names, built the same way the Python services build theirs
 * (env-prefixed queue names; the shared, unprefixed {@code domain_events}
 * exchange IAM already owns and publishes to).
 */
@Component
public class IamMessagingNames {

    public static final String DOMAIN_EVENTS_EXCHANGE = "domain_events";

    private final String environment;

    public IamMessagingNames(@Value("${ENVIRONMENT:development}") String environment) {
        this.environment = environment;
    }

    public String domainEventsQueue() {
        return environment + ".pms.inbound.domain_events.queue";
    }

    public String deadLetterQueue() {
        return environment + ".pms.dlq.queue";
    }

    /** An IAM Direct Reply-To RPC queue name, e.g. {@code eligible_employees_preview}. */
    public String iamRpcQueue(String name) {
        return environment + ".iam.rpc." + name + ".queue";
    }
}
