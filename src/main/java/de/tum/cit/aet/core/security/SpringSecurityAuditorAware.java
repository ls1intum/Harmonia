package de.tum.cit.aet.core.security;

import jakarta.annotation.Nonnull;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of {@link AuditorAware} based on Spring Security.
 */
@Component
public class SpringSecurityAuditorAware implements AuditorAware<UUID> {

    @Override
    @Nonnull
    public Optional<UUID> getCurrentAuditor() {
        // Temporary default UUID until authentication is added
        return Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000000"));
    }
}
