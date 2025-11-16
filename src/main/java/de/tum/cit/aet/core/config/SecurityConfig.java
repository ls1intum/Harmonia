package de.tum.cit.aet.core.config;

import org.springframework.beans.factory.annotation.Value;
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

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${harmonia.user}")
    private String harmoniaUser;

    @Value("${harmonia.password}")
    private String harmoniaPassword;

    /**
     * Security filter chain configuration.
     * All requests require authentication via HTTP Basic.
     *
     * @param http the HttpSecurity to configure
     * @return the configured SecurityFilterChain
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(("/api-docs"))
                        .permitAll()
                        .requestMatchers(("/api-docs.yaml"))
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
                .username(harmoniaUser)
                .password(harmoniaPassword)
                .roles("USER")
                .build();

        return new InMemoryUserDetailsManager(user);
    }
}
