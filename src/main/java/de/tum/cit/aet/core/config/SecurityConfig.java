package de.tum.cit.aet.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
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
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api-docs", "/api-docs.yaml", "/actuator/health")
                        .permitAll()
                        .anyRequest().authenticated()
                )
                .httpBasic(_ -> {});
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