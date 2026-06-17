# Implementation Plans

This directory holds planning artifacts that illustrate the workflow described in the README's
[Development Approach](../../README.md#development-approach): a roadmap that organizes
production-readiness work into independently reviewable phases, and a representative phase design
document that was produced and reviewed before implementation. Each document is the revision accepted
for implementation, not an exhaustive set of every phase.

| Plan | Description |
|---|---|
| [roadmap-production-readiness.md](roadmap-production-readiness.md) | Roadmap organizing the production-readiness refinement — security, monitoring, container workloads, CI, and documentation — into independently reviewable phases |
| [phase2-api-authorization.md](phase2-api-authorization.md) | Phase 2 design: split read and write API access into `FLAG_READER` and `FLAG_OPERATOR` roles |
