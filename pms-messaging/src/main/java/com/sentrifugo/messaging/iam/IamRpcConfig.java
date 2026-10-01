package com.sentrifugo.messaging.iam;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IamRpcConfig {

    /** Dedicated template for request/reply RPC to IAM, separate from any
     * template used for one-way publishing — {@code replyTimeout} only makes
     * sense on a send-and-receive template. */
    @Bean
    public RabbitTemplate iamRpcTemplate(ConnectionFactory connectionFactory,
                                         @Value("${pms.iam-rpc.reply-timeout-ms:5000}") long replyTimeoutMs) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setReplyTimeout(replyTimeoutMs);
        // Direct Reply-To (amq.rabbitmq.reply-to) is RabbitTemplate's default reply
        // mode once a reply timeout is set and no explicit reply queue is configured
        // — the same pseudo-queue IAM's RpcServer and every other RPC client in this
        // suite already use, so no further setup is needed here.
        return template;
    }
}
