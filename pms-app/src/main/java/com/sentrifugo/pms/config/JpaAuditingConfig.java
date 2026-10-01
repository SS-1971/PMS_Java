package com.sentrifugo.pms.config;

import com.sentrifugo.security.context.PmsUserPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Resolves {@code created_by} / {@code modified_by} (via Spring Data JPA
 * auditing) from the authenticated caller — Java equivalent of the Python
 * services' {@code get_actor_id()}, including its "system" fallback for
 * background work (the IAM domain-events consumer, scheduled jobs) that runs
 * with no request / no {@link SecurityContextHolder} principal.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "pmsAuditorAware")
public class JpaAuditingConfig {

    private static final String SYSTEM_ACTOR = "system";

    @Bean
    public AuditorAware<String> pmsAuditorAware() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof PmsUserPrincipal user) {
                return Optional.of(user.id());
            }
            return Optional.of(SYSTEM_ACTOR);
        };
    }
}
