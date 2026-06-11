---
status: "accepted"
date: 2026-06-11
decision-makers: ["Akihiro TAKASE"]
consulted: []
informed: []
---

# Use HTTP Basic for Local Portfolio Security Boundary

## Context and Problem Statement

This project is a portfolio-oriented feature flag service. It needs a visible
security boundary so API behavior, authority mapping, audit actor attribution,
and protected metrics can be reviewed locally without introducing a full
identity platform.

The service is not operated as a public production system from this repository.
Its local and kind deployment paths should therefore demonstrate the application
security contract while keeping authentication setup small enough to inspect in
one codebase.

Which authentication boundary should this repository use for the portfolio-local
service?

## Decision Drivers

* The security behavior must be easy to run locally and in kind.
* Reader and operator roles should be explicit in the Spring Security rules.
* Probe and API documentation routes should remain easy to inspect locally.
* Prometheus metrics should not be public once exposed over HTTP.
* Credentials should be supplied through configuration rather than hardcoded in
  the security implementation.
* The decision must document the CSRF trade-off for browser clients.
* The portfolio should avoid pretending to include a production identity system.

## Considered Options

* HTTP Basic with local reader/operator users
* OIDC or another organization-managed identity provider
* API keys
* No authentication

## Decision Outcome

Chosen option: **HTTP Basic with local reader/operator users**, because it gives
the portfolio a concrete Spring Security boundary while keeping local execution
and kind smoke testing lightweight.

The service defines two configured users:

* a reader that can use read-style feature flag APIs, evaluation, preview,
  validation, and audit reads;
* an operator that can also create and update feature flags.

Health endpoints and API documentation are public so Kubernetes probes and
local portfolio exploration remain straightforward. Application APIs require
the reader or operator role depending on the route. Prometheus metrics require
authentication from any configured local user. Unclassified `/api/**` routes are
denied before the final authenticated fallback so newly added API paths fail
closed until explicitly classified.

Credentials are injected through Spring Boot configuration properties and
environment variables. The security configuration creates an in-memory user
store at startup; it does not embed production credentials in source code and it
does not store users in the application PostgreSQL database.

HTTP Basic is not a production browser-client recommendation. Browsers can
automatically resend Basic credentials, so stateless Basic authentication is
still CSRF-sensitive when used from browser contexts. This repository disables
CSRF token handling only for the local portfolio JSON API so command-line and
scripted clients can call it directly. A production browser-facing deployment
must re-enable CSRF protection or replace Basic authentication.

A real production deployment should use OIDC or another organization-managed
identity provider and map trusted claims or groups to authorities equivalent to
`FLAG_READER` and `FLAG_OPERATOR`.

### Consequences

* Good, because the portfolio has an inspectable authentication and
  authorization boundary without requiring a separate identity provider.
* Good, because local Swagger, curl, kind, and CI smoke-test workflows stay
  simple.
* Good, because protected metrics and protected application APIs can be
  demonstrated with the same configured local users.
* Good, because audit actor identity can come from Spring Security instead of
  request payloads.
* Bad, because HTTP Basic is not sufficient as a production browser
  authentication model unless CSRF and credential handling are redesigned.
* Bad, because the in-memory user store does not provide centralized identity,
  lifecycle management, MFA, federation, or organization policy enforcement.
* Neutral, because Swagger UI and OpenAPI docs are deliberately public for local
  portfolio review; that exposure must be revisited before routing the service
  outside a trusted local or internal boundary.
* Neutral, because startup password encoding protects only the in-memory
  `UserDetailsService` representation; environment variables and Kubernetes
  Secrets still need platform-level protection.

### Confirmation

* `SecurityConfig` enables HTTP Basic and disables form login so unauthorized API
  requests receive 401 responses instead of browser login redirects.
* `LocalSecurityProperties` binds `feature-flags.security.*` values and validates
  non-blank, distinct reader and operator usernames.
* `application.yaml` maps local security values to
  `FEATURE_FLAGS_SECURITY_READER_USERNAME`,
  `FEATURE_FLAGS_SECURITY_READER_PASSWORD`,
  `FEATURE_FLAGS_SECURITY_OPERATOR_USERNAME`, and
  `FEATURE_FLAGS_SECURITY_OPERATOR_PASSWORD`.
* The Kubernetes app workload injects those values from
  `feature-flag-platform-secret`.
* Health endpoints, Swagger UI, and OpenAPI docs are explicitly public in
  `SecurityConfig`.
* Read-style flag APIs, evaluation, preview, validation, and audit reads accept
  `FLAG_READER` or `FLAG_OPERATOR`.
* Create and update routes require `FLAG_OPERATOR`.
* `/actuator/prometheus` requires authentication.
* `/api/**` has an explicit deny-all fallback before `anyRequest().authenticated()`.
* The CSRF disable comment records that HTTP Basic remains CSRF-sensitive in
  browsers and must be replaced or protected before production browser access.
* Session management is configured with `SessionCreationPolicy.STATELESS`, so
  each API request must present credentials instead of relying on an HTTP
  session.
* `CurrentActorProvider` derives audit actor identity from the trusted Spring
  Security context.

## Pros and Cons of the Options

### HTTP Basic with Local Reader/Operator Users

* Good, because it is easy to exercise with curl, Swagger, MockMvc tests, and
  kind.
* Good, because Spring Security route rules make the reader/operator split
  visible in one place.
* Good, because credentials can be supplied through configuration and local
  Kubernetes Secrets instead of being embedded in `SecurityConfig`.
* Bad, because Basic authentication is not enough for production browser clients
  without CSRF protection or a different authentication model.
* Bad, because it lacks centralized user lifecycle and organization-managed
  identity controls.

### OIDC or Another Organization-Managed Identity Provider

* Good, because it is the expected production direction for organization-managed
  users, groups, MFA, audit trails, and token-based service boundaries.
* Good, because trusted claims can map to the same reader/operator authorities.
* Bad, because it would add provider setup, token configuration, local issuer
  choices, and operational assumptions before this portfolio repository has a
  real production identity environment.

### API Keys

* Good, because API keys are easy for machine clients to send.
* Bad, because they would still require key issuance, rotation, storage,
  revocation, and audit policy that this repository does not implement.
* Bad, because a coarse key model would make the reader/operator and audit actor
  story less explicit than named authenticated principals.

### No Authentication

* Good, because it would be the simplest local setup.
* Bad, because it would make protected API behavior, protected metrics, and audit
  actor attribution impossible to demonstrate.
* Bad, because new API routes would be public unless separately protected.

## More Information

* [`SecurityConfig.java`](../../service/src/main/java/com/github/milez42/featureflags/SecurityConfig.java)
* [`LocalSecurityProperties.java`](../../service/src/main/java/com/github/milez42/featureflags/LocalSecurityProperties.java)
* [`application.yaml`](../../service/src/main/resources/application.yaml)
* [`deployment.yaml`](../../deploy/k8s/base/app/deployment.yaml)
* [`CurrentActorProvider.java`](../../service/src/main/java/com/github/milez42/featureflags/audit/CurrentActorProvider.java)
* [`AuditEventService.java`](../../service/src/main/java/com/github/milez42/featureflags/audit/AuditEventService.java)
* [`SecurityBoundaryIntegrationTest.java`](../../service/src/test/java/com/github/milez42/featureflags/SecurityBoundaryIntegrationTest.java)
* [Observability documentation](../observability.md)
* [`README.md`](../../README.md)
