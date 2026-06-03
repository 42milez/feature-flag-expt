package com.github.milez42.featureflags;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http.authorizeHttpRequests(
            authorization ->
                authorization
                    .requestMatchers(
                        "/actuator/health",
                        "/actuator/health/liveness",
                        "/actuator/health/readiness",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/v3/api-docs.yaml")
                    .permitAll()
                    // Keep sensitive routes explicit even though the fallback also requires
                    // authentication, so their intended security boundary remains visible.
                    .requestMatchers("/api/**", "/actuator/prometheus")
                    .authenticated()
                    .anyRequest()
                    .authenticated())
        .httpBasic(withDefaults())
        // This is a stateless REST API, so unauthenticated requests should receive 401 responses
        // instead of being redirected to a browser-oriented login form.
        .formLogin(formLogin -> formLogin.disable())
        // Disable CSRF token handling for this local portfolio API so stateless JSON clients can
        // call it directly. HTTP Basic is still CSRF-sensitive in browsers, so production browser
        // access must re-enable CSRF protection or replace Basic authentication.
        .csrf(csrf -> csrf.disable())
        // Do not store authentication in an HTTP session; each API request must present its own
        // credentials so service instances remain stateless and interchangeable.
        .sessionManagement(
            sessionManagement ->
                sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .build();
  }
}
