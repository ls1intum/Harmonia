package de.tum.cit.aet.core.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * JPA and transaction configuration.
 * Enables repository scanning, entity auditing, and declarative transaction management.
 */
@Configuration
@EnableJpaRepositories(basePackages = "de.tum.cit.aet")
@EnableJpaAuditing(auditorAwareRef = "springSecurityAuditorAware")
@EnableTransactionManagement
public class DatabaseConfiguration {}
