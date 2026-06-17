# Phase 2 Implementation Plan: Split Read and Write API Authorization

> **Status:** Accepted by the repository owner. This is the final revision adopted for implementation.

## Summary

Split authenticated API access into two local service roles: `FLAG_READER` and
`FLAG_OPERATOR`. Reader credentials can evaluate, preview, validate, and read
flag data. Operator credentials can additionally create and update flags.
Keep HTTP Basic as the local identity source for now, but make authorization
depend on Spring Security roles so a future OIDC migration can map token claims
to the same authorities.

## Review Notes Incorporated

- Keep route-level authorization in `SecurityConfig`; the Java and Kotlin
  controllers expose one API surface, so central route rules are easier to audit
  than scattered method annotations.
- Do not add a `RoleHierarchy`; explicitly list reader-or-operator routes with
  `hasAnyRole("FLAG_READER", "FLAG_OPERATOR")`.
- Keep `/actuator/prometheus` as `authenticated()` only. Metrics access remains
  separate from API read/write roles so future management-port, service-account,
  network-policy, mTLS, or OIDC-based controls can replace it cleanly.

## Key Changes

- Replace the single Spring Boot default user with two app-specific local users:
  - `FEATURE_FLAGS_SECURITY_READER_USERNAME`
  - `FEATURE_FLAGS_SECURITY_READER_PASSWORD`
  - `FEATURE_FLAGS_SECURITY_OPERATOR_USERNAME`
  - `FEATURE_FLAGS_SECURITY_OPERATOR_PASSWORD`
- Add validated security configuration properties and an
  `InMemoryUserDetailsManager` that creates users with
  `roles("FLAG_READER")` and `roles("FLAG_OPERATOR")`. Encode passwords at
  startup with a Spring Security `PasswordEncoder`, preferably
  `PasswordEncoderFactories.createDelegatingPasswordEncoder()`; do not use
  `{noop}` passwords.
- Update `SecurityConfig` route authorization with method-aware matchers:
  - public health and OpenAPI/Swagger routes stay public;
  - `GET /api/flags/*`, `GET /api/flags/*/audit-events`, `POST /api/evaluate`,
    `POST /api/flags/*/preview`, and `POST /api/flags/*/validate-change` require
    reader or operator;
  - `POST /api/flags` and `PATCH /api/flags/*` require operator;
  - unmatched `/api/**` routes use `denyAll()` so new API endpoints must be
    deliberately classified;
  - `/actuator/prometheus` remains accessible to any authenticated caller and is
    not coupled to API reader/operator roles;
  - `anyRequest().authenticated()` remains the final fallback for non-public,
    non-API routes.
- Implement the matcher rules in this order: public routes, read-style API
  routes, write API routes, `/actuator/prometheus`, `/api/**` deny-all, then
  `anyRequest().authenticated()`. Spring Security uses the first matching rule,
  so the specific API rules must appear before the `/api/**` catch-all.
- Use method-specific matchers such as
  `requestMatchers(HttpMethod.POST, "/api/flags")`; path-only matchers are not
  sufficient because read-style preview/validation endpoints and write endpoints
  are all `POST`.
- Use single-segment patterns for flag-key routes and a multi-segment catch-all
  only where intended: `/api/flags/*` does not cover
  `/api/flags/*/audit-events`, while `/api/**` intentionally covers the whole
  API namespace.
- Update local/dev configuration and docs:
  - replace `SPRING_SECURITY_USER_*` examples in `README.md`, `README.ja.md`,
    `docs/observability.md`, `service/src/test/resources/application.properties`,
    and the dev Kubernetes Secret with the new reader/operator variables;
  - update Prometheus local scrape credentials to one of the configured local
    users and document that metrics auth remains separate from API role split;
  - explain that local Basic users are a replaceable identity source for a future
    OIDC authority mapping;
  - avoid implying that startup password encoding protects environment variables
    or Kubernetes Secrets themselves.
- Add an `AGENTS.md` security rule only if implementation confirms the reusable
  principle. The new rule should be an addition to the existing authenticated
  fallback rule, not a replacement: protected API namespaces should deny
  unclassified routes before the final `anyRequest().authenticated()` fallback.

## Public API / Interface Impact

- No request or response DTO schemas change.
- HTTP behavior changes for protected API routes:
  - unauthenticated callers receive `401`;
  - authenticated readers receive `403` for create and update operations;
  - authenticated operators can use both read-style and write operations;
  - authenticated callers receive `403` for unclassified `/api/**` routes.
- OpenAPI and Swagger UI remain public. They do not need to generate per-role
  schemas in this phase.

## Test Plan

- Update existing service integration tests to use operator credentials by
  default where they create or update flags.
- Add or update security boundary integration tests for:
  - unauthenticated API access returns `401`;
  - reader can get a flag, list audit events, evaluate, preview, and validate a
    proposed change;
  - reader can call `POST /api/flags/*/preview` and
    `POST /api/flags/*/validate-change`, proving method-specific matcher
    ordering does not accidentally classify all `POST /api/flags/**` routes as
    operator-only;
  - reader cannot call `POST /api/flags` or `PATCH /api/flags/*` and receives
    `403`;
  - operator can create and update a flag;
  - authenticated access to an unclassified, nonexistent `/api/**` path returns
    `403`, proving the security filter denies it before request dispatch could
    produce `404`;
  - `/actuator/prometheus` still accepts any authenticated configured user;
  - public health, Swagger UI, and OpenAPI docs remain public.
- Keep OpenAPI tests unauthenticated to preserve the public API documentation
  behavior.
- Because `service/` changes, run in order:
  1. `./gradlew :service:spotlessCheck`
  2. `./gradlew :service:compileJava`
  3. `./gradlew :service:test`

## Assumptions

- Use real local Basic-auth reader/operator credentials rather than test-only
  mock users, because this exercises the role model through the same path used
  by local configuration.
- Startup password encoding is standard Spring Security practice for the
  in-memory user store, but it does not replace proper secret storage,
  rotation, or environment protection.
- Keep Prometheus authorization as `authenticated()` only; future production
  hardening should handle metrics through a management port, service account,
  network policy, mTLS, or equivalent platform control rather than API roles.
- OAuth2/OIDC, JWT resource server support, dynamic role storage, user
  management APIs, tenant-aware authorization, approval workflows, and
  environment-specific permissions remain out of scope.
