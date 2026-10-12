package com.sentrifugo.integration.config;

import com.rabbitmq.client.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

/**
 * Connects PMS to the same office-LAN broker IAM uses, from a single
 * {@code RABBITMQ_URL} (host, port, credentials and vhost all in one), exactly
 * the form {@code Sentrifugo-IAM-Admin-BE/.env} already holds, e.g.
 * {@code amqp://sentrifugo:admin@172.16.2.111:5772/dev}. Replaces the previous
 * {@code spring.rabbitmq.host/port/username/password/virtual-host} properties,
 * which required four separate env vars to agree instead of one.
 */
@Configuration
public class RabbitMqConfig {

    @Bean
    public org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory(
            @Value("${RABBITMQ_URL:amqp://guest:guest@localhost:5672/}") String rabbitMqUrl)
            throws URISyntaxException, NoSuchAlgorithmException, KeyManagementException {

        ConnectionFactory rabbitClientFactory = new ConnectionFactory();
        rabbitClientFactory.setUri(rabbitMqUrl);

        return new org.springframework.amqp.rabbit.connection.CachingConnectionFactory(rabbitClientFactory);
    }
}
