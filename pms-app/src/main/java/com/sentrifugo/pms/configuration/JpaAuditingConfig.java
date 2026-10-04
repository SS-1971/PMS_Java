package com.sentrifugo.pms.configuration;

import com.sentrifugo.security.context.PmsUserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

/**
 * Supplies the auditor for {@code created_by} / {@code modified_by}. Auditing
 * itself is enabled by pms-db's {@code PmsDbAutoConfiguration}, which looks this
 * bean up by name.
 *
 * <p>The audit columns are UUIDs, so only a caller whose IAM id parses as a UUID is
 * recorded. Background work with no authenticated caller (the IAM domain-events
 * consumer, scheduled jobs), or an id that is not a UUID, leaves the columns null —
 * nothing is hard-coded or taken from request data.
 */
@Configuration
public class JpaAuditingConfig {

    private static final Logger log = LoggerFactory.getLogger(JpaAuditingConfig.class);

    @Bean
    public AuditorAware<UUID> pmsAuditorAware() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof PmsUserPrincipal user) {
                try {
                    return Optional.of(UUID.fromString(user.id()));
                } catch (IllegalArgumentException e) {
                    log.debug("Caller id '{}' is not a UUID; audit columns left null", user.id());
                }
            }
            return Optional.empty();
        };
    }
}
