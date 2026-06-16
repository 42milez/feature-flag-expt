---
status: "accepted"
date: 2026-06-16
decision-makers: ["Akihiro TAKASE"]
consulted: []
informed: []
---

# Use Dependabot alerts and Renovate for SCA

## Context and Problem Statement

The repository already has several supply-chain controls. CI runs Trivy secret
scanning, the service image workflow builds `service/Dockerfile` and scans the
resulting image for high and critical vulnerabilities, GitHub Actions are pinned
to full commit SHAs with version comments, workflow checkouts disable persisted
credentials, Docker build and runtime base images are pinned by digest, and
Gradle dependencies are centralized in `gradle/libs.versions.toml`.

The remaining gap is source dependency vulnerability detection and organized
dependency remediation. The project needs Software Composition Analysis coverage
without expanding the Codacy quality rollout or introducing SonarQube Advanced
Security for this responsibility.

Which tool should own source dependency vulnerability detection and dependency
update pull requests?

## Decision Drivers

* Source dependency vulnerability detection should appear in GitHub's native
  Security tab and alert lifecycle.
* Dependency update policy should support fine-grained package and ecosystem
  rules for the repository's Gradle, GitHub Actions, and Docker dependencies.
* Vulnerability remediation should be able to take priority over routine version
  updates when fixed versions are available.
* Dependabot version-update pull requests and Renovate version-update pull
  requests must not compete for the same ecosystems.
* Existing Trivy repository secret scanning and image vulnerability gates should
  remain authoritative for their current responsibilities.
* GitHub Actions and Docker base image updates must preserve the repository's
  existing SHA and digest pinning posture.
* Temporary Tomcat and PostgreSQL JDBC dependency pins must stay manually owned
  until Spring Boot manages fixed baselines.
* The first rollout should keep automerge disabled and rely on existing CI and
  manual review.

## Considered Options

* Dependabot alerts for detection with Renovate for update pull requests
* Dependabot alerts and Dependabot version updates
* Renovate only with OSV vulnerability alerts
* Existing Trivy gates only
* SonarQube Advanced Security for SCA

## Decision Outcome

Chosen option: **Dependabot alerts for detection with Renovate for update pull
requests**, because this keeps GitHub-native vulnerability visibility while
giving dependency update pull requests to the tool with the finer package rules,
digest-pinning policy, and committed configuration control this repository
needs.

Enable GitHub dependency graph and Dependabot alerts in repository or
organization settings. Do not add `.github/dependabot.yml` for scheduled
Dependabot version updates while Renovate owns version-update pull requests.
Dependabot alerts and Dependabot version updates are separate features; this
decision uses only the alerting side of Dependabot.

Use Renovate for Gradle, Gradle wrapper, GitHub Actions, and Dockerfile
dependency update pull requests. The initial repository config lives in
`.github/renovate.json5`, uses JSON5 so policy comments can stay close to the
rules, enables the Dependency Dashboard, runs scheduled update discovery before
9am on Mondays in the Asia/Tokyo timezone, limits concurrent and hourly pull
requests, labels dependency PRs with `dependencies`, and keeps automerge
disabled.

Renovate vulnerability remediation uses GitHub vulnerability alerts through
`vulnerabilityAlerts`. Security remediation PRs receive both `dependencies` and
`security` labels and use the lowest fixed version strategy. These remediation
PRs are allowed to bypass the configured schedule and PR limits for
Renovate-managed packages.

Use the Mend-hosted Renovate GitHub App for the first rollout unless repository
policy blocks third-party write access. The hosted app is allowed to create
branches and pull requests, but it must not merge them automatically. Existing
CI checks and branch protection remain the merge gate, and the first generated
Renovate PRs should be reviewed manually before any future automerge policy is
considered. Self-host Renovate only if the hosted app cannot be installed with
acceptable repository permissions or Dependabot-alert read access.

The temporary Spring Boot-managed Tomcat and PostgreSQL JDBC pins are excluded
from Renovate by package rule. The intended behavior is that `enabled: false`
suppresses both routine Renovate update PRs and Renovate vulnerability-fix PRs
for those packages; confirm that behavior during the first Renovate cycle. Their
compensating controls are Dependabot alerts for detection, the daily Trivy image
vulnerability scan for fixed high and critical findings in built artifacts, and
manual remediation through direct pin updates or removal of the pins once Spring
Boot manages the fixed baselines.

Do not enable `osvVulnerabilityAlerts` in the initial config. OSV can be added
later if the team wants a fallback vulnerability source or does not want
Renovate to read GitHub Dependabot alerts, but it does not replace GitHub's
native Security tab, alert states, or organization-level overview.

Do not add a Dependency Review Action workflow in this decision. It can be added
later as a separate pull request if GitHub dependency review is available for
the repository and the team wants a pull-request gate for changed manifests.

Prometheus `promtool` updates remain manually owned. The CI workflow downloads
`promtool` with `PROMETHEUS_VERSION` and
`PROMETHEUS_LINUX_AMD64_SHA256`; Renovate should not manage that download unless
a future custom manager is added.

### Consequences

* Good, because dependency alerts remain visible through GitHub's native
  Security tab and alert workflow.
* Good, because Renovate can group related updates, rate-limit PRs, and manage
  Gradle, Gradle wrapper, GitHub Actions, and Dockerfile dependencies from one
  committed policy file.
* Good, because security remediation PRs for Renovate-managed packages are
  labeled and prioritized without enabling competing Dependabot version-update
  PRs.
* Good, because GitHub Actions SHA pinning and Docker digest pinning remain part
  of the dependency update policy.
* Good, because existing Trivy secret and image vulnerability gates remain
  unchanged and continue to cover repository secrets and built artifacts.
* Bad, because hosted Renovate introduces a third-party app with write access to
  create branches and pull requests.
* Bad, because Dependabot alerts, Renovate app permissions, the Dependency
  Dashboard, and vulnerability remediation PR behavior require repository-side
  setup outside the codebase.
* Bad, because the temporary Tomcat and PostgreSQL JDBC pins require manual
  remediation while their Renovate package rule remains disabled.
* Neutral, because dependency review, license policy, SBOM publishing, and
  custom update managers remain separate future decisions.

### Confirmation

* `.github/renovate.json5` enables the Gradle, Gradle wrapper, GitHub Actions,
  and Dockerfile managers.
* `.github/renovate.json5` enables the Dependency Dashboard and labels
  dependency PRs with `dependencies`.
* `.github/renovate.json5` sets the Asia/Tokyo timezone, a Monday morning
  schedule, `prConcurrentLimit`, and `prHourlyLimit`.
* `.github/renovate.json5` sets `pinDigests: true` and `rangeStrategy: "bump"`.
* `.github/renovate.json5` configures `vulnerabilityAlerts` with the
  `dependencies` and `security` labels and the `lowest` vulnerability fix
  strategy.
* `.github/renovate.json5` disables Renovate for the temporary
  `org.apache.tomcat.embed:tomcat-embed-core`,
  `org.apache.tomcat.embed:tomcat-embed-el`,
  `org.apache.tomcat.embed:tomcat-embed-websocket`, and
  `org.postgresql:postgresql` pins.
* `.github/renovate.json5` groups Spring platform, Testcontainers, Kotlin,
  Gradle build tooling, GitHub Actions, and Docker base image updates.
* No `.github/dependabot.yml` file exists, so Dependabot version-update PRs are
  not configured in the repository.
* Repository settings must be manually confirmed: the dependency graph and
  Dependabot alerts are enabled, and the Mend Renovate GitHub App has the
  permissions needed to create PRs and read Dependabot alerts.
* `.github/workflows/ci.yaml`, `.github/workflows/image-vulnerability-scan.yaml`,
  and `.github/workflows/kind-smoke-test.yaml` pin GitHub Actions by full commit
  SHA and use `persist-credentials: false` for checkout.
* `.github/workflows/ci.yaml` still runs Trivy repository secret scanning.
* `.github/workflows/image-vulnerability-scan.yaml` still builds the service
  image and gates fixed high and critical vulnerabilities with Trivy.
* `service/Dockerfile` pins build and runtime base images by digest.
* `gradle/libs.versions.toml` centralizes Gradle dependency versions and
  documents the temporary Tomcat and PostgreSQL JDBC pins.

## Pros and Cons of the Options

### Dependabot alerts for detection with Renovate for update pull requests

* Good, because GitHub remains the source for dependency alert visibility,
  alert state, and Security tab workflows.
* Good, because Renovate provides grouped and scheduled update PRs across
  Gradle, Gradle wrapper, GitHub Actions, and Dockerfile dependencies.
* Good, because the repository can express package-specific policy in a
  committed JSON5 config file.
* Good, because it avoids duplicate version-update PRs by keeping Dependabot
  version updates disabled.
* Bad, because hosted Renovate requires trusting a third-party app to create
  branches and pull requests.
* Bad, because the repository must validate the first generated Renovate PRs to
  confirm SHA comment updates, digest pinning, and disabled-package behavior.

### Dependabot alerts and Dependabot version updates

* Good, because all detection and update behavior would stay GitHub-native.
* Good, because no third-party PR-writing app would be needed.
* Bad, because the repository still needs more expressive package rules for the
  temporary Spring Boot-managed runtime pins, grouped platform updates, and
  Docker digest pinning policy than this rollout wants to encode in
  `dependabot.yml`.
* Bad, because enabling Dependabot version updates would compete with Renovate
  for the same ecosystems if both tools were active.

### Renovate only with OSV vulnerability alerts

* Good, because Renovate could provide both version updates and an additional
  vulnerability source from one tool.
* Good, because it could avoid granting Renovate read access to GitHub
  Dependabot alerts.
* Bad, because OSV vulnerability alerts are experimental and direct-dependency
  focused.
* Bad, because OSV alerts do not replace GitHub's native Security tab, alert
  lifecycle, or organization-level dependency alert overview.

### Existing Trivy gates only

* Good, because Trivy is already configured for repository secret scanning and
  built-image vulnerability scanning.
* Good, because this avoids adding a hosted dependency-update service.
* Bad, because source dependency vulnerability alerts and organized dependency
  update PRs remain missing.
* Bad, because image scanning catches built artifact risk but is not a complete
  replacement for manifest-level source dependency alerting.

### SonarQube Advanced Security for SCA

* Good, because it could provide SCA features in a broader code-quality and
  security platform.
* Bad, because SCA is intentionally outside the Codacy quality visibility
  rollout and does not need a paid SonarQube Advanced Security adoption for this
  repository.
* Bad, because the repository already has focused Trivy gates and only needs the
  missing source dependency alerting and remediation lane.

## More Information

* [Renovate config](../../.github/renovate.json5)
* [CI workflow](../../.github/workflows/ci.yaml)
* [Image vulnerability scan workflow](../../.github/workflows/image-vulnerability-scan.yaml)
* [Kind smoke test workflow](../../.github/workflows/kind-smoke-test.yaml)
* [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml)
* [`service/Dockerfile`](../../service/Dockerfile)
* [ADR-0012: Use Codacy for Quality Visibility and New-Change Gates](0012-use-codacy-for-quality-visibility-and-new-change-gates.md)
* [Renovate configuration options](https://docs.renovatebot.com/configuration-options/)
* [Renovate GitHub App](https://docs.renovatebot.com/install-github-app/)
* [GitHub Dependabot alerts](https://docs.github.com/en/code-security/dependabot/dependabot-alerts)
* [GitHub Dependabot options reference](https://docs.github.com/en/code-security/reference/supply-chain-security/dependabot-options-reference)
