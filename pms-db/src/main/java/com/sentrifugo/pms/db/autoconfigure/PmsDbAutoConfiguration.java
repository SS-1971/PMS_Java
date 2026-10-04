package com.sentrifugo.pms.db.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Registers PMS persistence: entities, repositories and Spring Data auditing.
 *
 * <p>The {@code AuditorAware<UUID>} bean named {@code pmsAuditorAware} is supplied by the
 * application (it knows the security context); this module only wires auditing to it, so
 * {@code created_by}/{@code modified_by} are never taken from request data.
 */
@AutoConfiguration
@EntityScan(basePackages = "com.sentrifugo.pms.db.entity")
@EnableJpaRepositories(basePackages = "com.sentrifugo.pms.db.repository")
@EnableJpaAuditing(auditorAwareRef = "pmsAuditorAware")
public class PmsDbAutoConfiguration {
}
