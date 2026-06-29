package com.github.milez42.featureflags;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableConfigurationProperties(LocalSecurityProperties.class)
public class SecurityConfig {
  private static final String FLAG_READER = "FLAG_READER";
  private static final String FLAG_OPERATOR = "FLAG_OPERATOR";
  private static final String FLAG_APPROVER = "FLAG_APPROVER";

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
                    .requestMatchers(HttpMethod.GET, "/api/flags/*")
                    .hasAnyRole(FLAG_READER, FLAG_OPERATOR)
                    .requestMatchers(HttpMethod.GET, "/api/flags/*/audit-events")
                    .hasAnyRole(FLAG_READER, FLAG_OPERATOR)
                    .requestMatchers(HttpMethod.POST, "/api/evaluate")
                    .hasAnyRole(FLAG_READER, FLAG_OPERATOR)
                    .requestMatchers(HttpMethod.POST, "/api/flags/*/preview")
                    .hasAnyRole(FLAG_READER, FLAG_OPERATOR)
                    .requestMatchers(HttpMethod.POST, "/api/flags/*/validate-change")
                    .hasAnyRole(FLAG_READER, FLAG_OPERATOR)
                    .requestMatchers(HttpMethod.POST, "/api/flags/*/approval-requests")
                    .hasRole(FLAG_OPERATOR)
                    .requestMatchers(HttpMethod.POST, "/api/flags/*/approval-requests/*/approve")
                    .hasRole(FLAG_APPROVER)
                    .requestMatchers(HttpMethod.POST, "/api/flags/*/approval-requests/*/reject")
                    .hasRole(FLAG_APPROVER)
                    .requestMatchers(HttpMethod.GET, "/api/flags/*/approval-requests/*")
                    .hasAnyRole(FLAG_OPERATOR, FLAG_APPROVER)
                    .requestMatchers(HttpMethod.POST, "/api/flags")
                    .hasRole(FLAG_OPERATOR)
                    .requestMatchers(HttpMethod.PATCH, "/api/flags/*")
                    .hasRole(FLAG_OPERATOR)
                    .requestMatchers("/actuator/prometheus")
                    .authenticated()
                    .requestMatchers("/api/**")
                    .denyAll()
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

  @Bean
  PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Bean
  UserDetailsService userDetailsService(
      LocalSecurityProperties properties, PasswordEncoder passwordEncoder) {
    UserDetails reader =
        User.withUsername(properties.readerUsername())
            .password(passwordEncoder.encode(properties.readerPassword()))
            .roles(FLAG_READER)
            .build();
    UserDetails operator =
        User.withUsername(properties.operatorUsername())
            .password(passwordEncoder.encode(properties.operatorPassword()))
            .roles(FLAG_OPERATOR)
            .build();
    UserDetails approver =
        User.withUsername(properties.approverUsername())
            .password(passwordEncoder.encode(properties.approverPassword()))
            .roles(FLAG_APPROVER)
            .build();

    return new InMemoryUserDetailsManager(reader, operator, approver);
  }
}
