# Rollout Policy: Risk Classification and Approval Verification

This note captures the rationale and evolving thinking behind high-risk rollout
classification and approval verification. It complements the current
`RolloutPolicyValidationRequest` API, where `highRisk` and `approvalGranted` are
temporary caller-supplied policy context fields. Unlike an ADR, this document may be
updated as the approval workflow evolves.

## Philosophy: Policy Inputs Must Be Verifiable

Rollout policy should depend on facts the server can verify, not only on booleans the
caller controls.

The current request shape is useful while the project lacks flag metadata,
change-request state, actor identity, and an approval workflow. It lets the policy
validator model the intended rule:

```
high-risk change + no approval = policy violation
```

That shape should not become the long-term trust boundary. Client-supplied policy
context is only a placeholder until the server has durable sources of truth.

## Current Create Enforcement Mitigation

Flag creation now enforces rollout policy before persistence. The create request
accepts `reason` so callers can provide business context when enabling production
access without a tenant allowlist, but it does not accept `highRisk` or
`approvalGranted` while those values remain caller-controlled placeholders.

Create validation uses the shared rollout policy semantics from a synthetic
non-serving baseline: disabled, no target environments, kill switch active, empty
tenant allowlist, and 0% rollout. As a result, a direct 0% to 100% production
rollout configuration is rejected even when the requested flag is disabled or
kill-switched. Tests that need otherwise-invalid historical or precondition states
seed those states directly instead of routing setup through `POST /api/flags`.

Server-derived risk classification and server-verified approval state remain
follow-up work.

## Why highRisk Should Be Derived Server-Side

`highRisk` decides whether additional approval requirements apply. If a caller can
set this value directly, the caller can suppress approval requirements by sending
`false`.

The field also does not prove that the risk assessment considered the relevant
business and operational inputs. Prefer deriving risk from server-side state such as:

- Flag criticality metadata and owning service tier.
- Production exposure and whether the proposed change expands access.
- Rollout size, especially large jumps or full production rollouts.
- Tenant impact, including large customers or sensitive cohorts.
- Actor permissions and whether the actor owns the affected surface.
- Release freezes, incident state, or other operational constraints.
- Change-request risk classification once change requests exist.

## Why approvalGranted Should Be Verified Server-Side

`approvalGranted` is also a temporary placeholder. A caller-controlled boolean can
bypass high-risk approval policy by sending `true`.

Even when sent honestly, a boolean does not prove:

- Who approved the change.
- Whether the approver had permission to approve it.
- Whether the approval belongs to this exact proposed change.
- Whether the approval is still valid.
- Whether the approval is captured in audit history.

The server must also verify that the calling actor is allowed to use the referenced
approval, so an approval identifier cannot be reused by another actor as an IDOR
bypass.

Prefer a `changeRequestId` or `approvalId` that the server can verify against approval
state, approver permissions, calling-actor binding, expiry, proposal identity, and
audit records.

## Migration Path

Move from caller-supplied booleans to server-verified policy context in stages:

```
Caller-supplied booleans
  |
  +- add flag metadata and actor identity
  |
  +- introduce change requests and approval records
  |
  +- derive risk and verify approval on the server
```

The endpoint can keep accepting proposed flag changes, but the risk and approval
facts should come from durable server-side state. This keeps preview and validation
useful for clients while preventing alternate clients from bypassing rollout controls.

When this becomes implementation work, prefer a typed approval context over raw
booleans or unverified identifiers so states such as approval required, approval not
required, and verified approval are explicit at the policy boundary.

## Relationship to Rollout Policy Enforcement

Validation endpoints are a UX convenience, not the final enforcement point. The
write path must continue to enforce rollout policy before persisting changes.

Once risk classification and approval verification are server-backed, both the
validation endpoint and the write path should use the same policy context builder so
the previewed decision and the enforced decision stay aligned.
