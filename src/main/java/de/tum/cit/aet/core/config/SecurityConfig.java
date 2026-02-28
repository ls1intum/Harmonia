package de.tum.cit.aet.core.config;

import de.tum.cit.aet.core.security.SpaWebFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Spring Security configuration.
 * Configures stateless HTTP Basic authentication, CORS, CSRF, and public endpoint access.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final HarmoniaProperties harmoniaProperties;

    public SecurityConfig(HarmoniaProperties harmoniaProperties) {
        this.harmoniaProperties = harmoniaProperties;
    }

    /**
     * Defines the security filter chain with CORS, CSRF, session policy,
     * SPA routing filter, and endpoint authorization rules.
     *
     * @param http                    the {@link HttpSecurity} to configure
     * @param corsConfigurationSource the CORS configuration source
     * @return the configured {@link SecurityFilterChain}
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(CsrfConfigurer::disable)
                .addFilterAfter(new SpaWebFilter(), BasicAuthenticationFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api-docs", "/api-docs.yaml").permitAll()
                        // TODO: After we have real user management, restrict access to authenticated users only
                        .anyRequest().permitAll()
                );
        return http.build();
    }

    /**
     * In-memory user details service with credentials from application properties.
     *
     * @return the configured {@link UserDetailsService}
     */
    @Bean
    public UserDetailsService userDetailsService() {
        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        UserDetails user = User.builder()
                .passwordEncoder(encoder::encode)
                .username(harmoniaProperties.getUser())
                .password(harmoniaProperties.getPassword())
                .roles("USER")
                .build();

        return new InMemoryUserDetailsManager(user);
    }
}
