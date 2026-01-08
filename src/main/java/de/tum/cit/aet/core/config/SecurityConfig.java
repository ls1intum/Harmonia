package de.tum.cit.aet.core.config;

import de.tum.cit.aet.core.security.SpaWebFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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


@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final HarmoniaProperties harmoniaProperties;

    public SecurityConfig(HarmoniaProperties harmoniaProperties) {
        this.harmoniaProperties = harmoniaProperties;
    }

    /**
     * Security filter chain configuration.
     * All requests require authentication via HTTP Basic.
     *
     * @param http the HttpSecurity to configure
     * @param corsConfigurationSource the CORS configuration source
     * @return the configured SecurityFilterChain
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                // Adds a CORS (Cross-Origin Resource Sharing) filter before the
                // username/password authentication to handle cross-origin requests.
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                // Disables CSRF (Cross-Site Request Forgery) protection; useful in stateless
                // APIs where the token management is unnecessary.
                .csrf(CsrfConfigurer::disable)
                // Adds a custom filter for Single Page Applications (SPA), i.e. the client,
                // after the basic authentication filter.
                .addFilterAfter(new SpaWebFilter(), BasicAuthenticationFilter.class)
                // Configures sessions to be stateless; appropriate for REST APIs where no
                // session is required.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        // Health check endpoint
                        .requestMatchers("/actuator/**")
                        .permitAll()
                        // Public Endpoints
                        .requestMatchers("/api/auth/**")
                        .permitAll()
                        // Openapi Endpoints
                        .requestMatchers("/api-docs", "/api-docs.yaml")
                        .permitAll()
                        // TODO: After we have real user management, restrict access to authenticated users only
                        .anyRequest().permitAll()
                );
        return http.build();
    }

    /**
     * In-memory user details service configuration.
     * Creates a single user with credentials from application properties.
     *
     * @return the configured UserDetailsService
     */
    @Bean
    public UserDetailsService userDetailsService() {
        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        var builder = User.builder().passwordEncoder(encoder::encode);
        UserDetails user = builder
                .username(harmoniaProperties.getUser())
                .password(harmoniaProperties.getPassword())
                .roles("USER")
                .build();

        return new InMemoryUserDetailsManager(user);
    }
}
