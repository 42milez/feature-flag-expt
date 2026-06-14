---
status: "accepted"
date: 2026-05-29
decision-makers: ["Akihiro TAKASE"]
consulted: []
informed: []
---

# Use kind for local Kubernetes development and CI validation

## Context and Problem Statement

This project is a portfolio-oriented feature flag management system. It is meant
to show not only CRUD API behavior, but also Platform Engineering, CI/CD,
Kubernetes deployment, OpenAPI, Testcontainers-backed testing, and operational
readiness practices.

The repository includes Kubernetes manifests and a scheduled smoke-test workflow.
Local development and CI should therefore be able to validate the same
deployment shape without requiring a cloud Kubernetes cluster such as EKS.

Which local execution environment should the project use for Kubernetes manifest
validation and smoke testing?

## Decision Drivers

* CI reproducibility
* Ease of Kubernetes manifest validation
* Lightweight local setup
* Similarity to production Kubernetes
* Learning cost
* Low dependency on Docker Desktop-specific Kubernetes behavior
* Explainability as a portfolio project

## Considered Options

* kind
* minikube
* k3d
* Docker Desktop Kubernetes
* Docker Compose as a local execution alternative, not as a Kubernetes cluster

## Decision Outcome

Chosen option: **kind**, because it is the best fit for this project's goal of
validating Kubernetes manifests and deployment steps in both local development
and GitHub Actions.

kind runs Kubernetes nodes as Docker containers. That keeps the local and CI
execution model small and explicit, and it makes the expected workflow easy to
express:

* Build the Spring Boot jar.
* Build the service Docker image.
* Load the local image into the kind cluster with `kind load docker-image`.
* Apply the `dev` Kubernetes overlay with `kubectl apply -k`.
* Wait for the PostgreSQL StatefulSet and application Deployment rollouts to
  complete.
* Print Kubernetes status and collect diagnostics on failure.

This does not fully reproduce a production Kubernetes platform, and it is not
intended to. For this repository, the primary goal is to verify that the
manifests, image wiring, readiness behavior, and local deployment procedure work
in a real Kubernetes API server. kind is sufficient for that purpose while being
easier to run in a personal portfolio repository than a managed cloud cluster.

The `dev` overlay is explicitly local-only. It includes in-cluster PostgreSQL,
placeholder credentials, and the local image tag used by `kind load`; it should
not be treated as a production security baseline for secrets, RBAC,
NetworkPolicy, ingress, TLS, IAM, or cloud-managed dependencies.

### Consequences

* Good, because GitHub Actions can create a disposable Kubernetes cluster with a
  small amount of explicit setup.
* Good, because local and CI validation can share the same image-build,
  `kind load`, `kubectl apply`, rollout-wait, and status flow.
* Good, because Kubernetes manifests are exercised through a real Kubernetes API
  server rather than only rendered or linted.
* Good, because the repository can demonstrate Platform Engineering, CI/CD, and
  Kubernetes deployment practices without provisioning cloud infrastructure.
* Good, because kind covers Kubernetes deployment validation without adding
  Docker Compose as a second local runtime; database-dependent application tests
  remain covered by Testcontainers.
* Bad, because kind does not reproduce cloud-provider integrations, managed load
  balancers, persistent volume behavior, IAM integration, production network
  policies, or production secret-management controls.
* Bad, because developers still need Docker, kind, kubectl, and the project
  Gradle tasks or scripts installed locally.
* Neutral, because Docker Desktop may still provide the local Docker engine on
  macOS, but the project does not depend on Docker Desktop's Kubernetes feature.

### Confirmation

* The scheduled `Kind Smoke Test` GitHub Actions workflow creates a kind cluster.
* The smoke-test workflow builds the service image, loads it into kind, applies
  the dev overlay, waits for readiness, and prints Kubernetes status.
* The scheduled smoke test passes when the Docker image build, `kind load
  docker-image`, `k8sApplyDev`, `k8sWaitDev`, and `k8sStatusDev` all exit
  successfully. `k8sWaitDev` requires successful
  `kubectl rollout status` results for `statefulset/feature-flag-postgres` and
  `deployment/feature-flag-platform`.
* Local scripts and Gradle tasks expose the same cluster creation, image loading,
  manifest application, rollout wait, status, port-forward, health-check, and
  deletion operations.
* Local health verification passes when `scripts/app-health.sh` can `curl` the
  Actuator health, liveness, and readiness endpoints through the port-forwarded
  service without a non-zero exit.
* The `deploy/kind/cluster.yaml` configuration defines the local kind cluster
  shape used by CI and local development.
* The Kubernetes `dev` overlay supplies local-only dependencies such as
  in-cluster PostgreSQL, placeholder credentials, and the local image tag.

### Amendment: supplementary Docker Compose runtime

Docker Compose is now provided as a supplementary local application runtime for
the Quick Start path. It starts the Spring Boot application and PostgreSQL
without requiring a Kubernetes cluster, which keeps first-run verification small
while preserving kind as the accepted path for validating Kubernetes manifests,
service discovery, probes, and the `kubectl apply` workflow.

The Compose stack is intentionally scoped to local application execution. It
uses its own `feature-flag-platform:compose-local` image tag, binds application
and PostgreSQL ports to loopback only, and treats database state as disposable.
This amendment does not change the accepted decision that kind is the local and
CI environment for Kubernetes deployment validation.

The Compose Quick Start now builds the Spring Boot jar inside the multi-stage
Docker build, so portfolio reviewers can start the stack with Docker and Docker
Compose only. The service image ships the Spring Boot fat jar as a single layer
instead of using layered-jar extraction because images are loaded into kind via
`kind load image-archive` and are not pushed to a registry; revisit this if a
registry-push workflow is added.

## Pros and Cons of the Options

### kind

* Good, because it runs Kubernetes nodes as Docker containers and is easy to
  recreate in GitHub Actions.
* Good, because it supports the image build, `kind load docker-image`,
  `kubectl apply`, and smoke-test flow used by this repository.
* Good, because it validates standard Kubernetes manifests through a real API
  server.
* Good, because it uses an upstream Kubernetes API without adding k3s-specific or
  Docker Desktop Kubernetes-specific behavior to the validation path.
* Good, because the workflow uses Docker, kubectl, and Kubernetes primitives that
  contributors already need for this repository, keeping the local learning path
  small.
* Good, because it is easy to explain as a local and CI validation layer for a
  portfolio project.
* Bad, because it is not a full production Kubernetes replacement and does not
  model cloud-provider-specific integrations.

### minikube

* Good, because it is a common local Kubernetes environment and is strong for
  learning and hands-on validation.
* Good, because it supports multiple drivers, including a Docker driver, and can
  be useful for interactive local Kubernetes work.
* Good, because minikube has documented GitHub Actions usage, so it is a viable
  CI option rather than a local-only tool.
* Bad, because its broader driver, runtime, addon, and service-access surface
  gives the project more choices to pin and explain than kind requires for this
  manifest-validation workflow.
* Bad, because this project prioritizes one small local workflow that can also
  run the same way in CI over the richest single-developer local Kubernetes
  experience.

### k3d

* Good, because it runs k3s on Docker and is lightweight.
* Good, because k3s is attractive as an operation-oriented lightweight
  Kubernetes distribution.
* Bad, because k3s intentionally packages a Kubernetes distribution with its own
  operational defaults; using it as the primary local cluster would make the
  repository's generic Kubernetes manifest-validation signal less direct.

### Docker Desktop Kubernetes

* Good, because it is convenient on macOS when Docker Desktop is already used.
* Good, because it gives a quick local Kubernetes cluster for manual testing.
* Bad, because it strongly depends on Docker Desktop and its local Kubernetes
  implementation.
* Bad, because it is harder to reproduce in GitHub Actions and third-party
  contributor environments.
* Bad, because it adds a Docker Desktop-specific Kubernetes learning path, while
  the project only needs Docker as the container engine plus explicit Kubernetes
  tooling.

### Docker Compose

* Good, because it is convenient for starting an application and its local
  dependencies.
* Good, because it can be useful as a supplementary local tool if a future task
  needs a non-Kubernetes runtime.
* Bad, because it does not validate Kubernetes manifests, Kubernetes service
  discovery, probes, deployment configuration, or the `kubectl apply` workflow.
* Bad, because database integration tests are already handled by Testcontainers,
  so Compose is not needed as the main local dependency-management layer.
* Bad, because making Compose the primary runtime would not satisfy the
  portfolio goal of showing Platform Engineering and Kubernetes deployment
  practices.

## More Information

* [Kind Smoke Test workflow](../../.github/workflows/kind-smoke-test.yaml)
* [`deploy/kind/cluster.yaml`](../../deploy/kind/cluster.yaml)
* [`deploy/k8s/base`](../../deploy/k8s/base)
* [`deploy/k8s/overlays/dev`](../../deploy/k8s/overlays/dev)
* [`scripts/dev-deploy.sh`](../../scripts/dev-deploy.sh)
* [`scripts/kind-load-image.sh`](../../scripts/kind-load-image.sh)
* [`scripts/k8s-wait-dev.sh`](../../scripts/k8s-wait-dev.sh)
* [`scripts/app-health.sh`](../../scripts/app-health.sh)
* [`compose.yaml`](../../compose.yaml)
* [minikube Docker driver](https://minikube.sigs.k8s.io/docs/drivers/docker/)
* [minikube in GitHub Actions](https://minikube.sigs.k8s.io/docs/tutorials/setup_minikube_in_github_actions/)
* [`README.md`](../../README.md)
