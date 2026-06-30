# Rollout Policy: Risk Classification and Approval Verification

This note records the implemented server-side rollout risk classification and
update approval workflow. It complements the rollout policy validator,
validation-preview API, update API, and update approval endpoints.

## Philosophy: Policy Inputs Must Be Verifiable

Rollout policy depends on facts the server can verify. Clients may describe a
proposed flag change and provide a business `reason`, but they do not decide
whether the change is high risk or whether approval is satisfied.

The server derives risk from the resolved current flag state and the proposed
state, verifies any referenced approval against durable approval records, and
enforces the result again on the write path before persisting a flag update.

## Risk Classification

The rollout risk classifier compares the complete resolved current flag state
with the proposed state. A proposed update is high risk when it expands
production exposure in a way that needs human review, such as increasing a
production rollout percentage or opening production access without a tenant
allowlist.

Existing hard-block rules remain independent from approval. A direct `0 -> 100%`
production rollout and production access without a required `reason` remain
policy violations rather than approvable changes. Approval can satisfy the
high-risk review requirement only after terminal validation blockers have been
handled.

Create requests are still validated from a synthetic non-serving baseline:
disabled, no target environments, kill switch active, empty tenant allowlist,
and 0% rollout. The approval workflow is update-only; there is no create
approval flow.

## Approval Scope and Binding

An update approval is bound to:

- the flag key;
- the requesting actor;
- a structural snapshot of the complete resolved current flag state;
- a structural snapshot of the complete proposed flag state.

The structural snapshots are the freshness comparison inputs. The request
`reason` and classified risk reasons are stored for review, audit, and display
context, but they are not part of the approval freshness comparison. Changing
the proposed flag state requires a new approval; changing only review context
does not make an otherwise identical approval stale.

Only the requester may use an approval on an update. This prevents another
operator from reusing a valid approval id for a different actor's update.

Invalid approval ids collapse to the same fail-closed behavior as a missing
approval. Callers receive the policy violation for a high-risk update that still
requires approval, rather than a distinct signal that would let clients probe
approval identifiers.

## Approval Decisions

Approval decisions require the approver role. The requester cannot approve or
reject their own request, even if they also have approver credentials.

Approval status reads are available to the requester or to an approver. Other
operators cannot inspect approvals they did not request.

Approvals are single-use. Consuming an approval marks it used under optimistic
locking so concurrent update attempts cannot reuse the same approved record.

The current workflow does not expire approvals. Stale approvals are rejected
only when their structural snapshots no longer match the current and proposed
flag states.

## Audit Behavior

The workflow writes audit events for these approval lifecycle moments:

- approval requested;
- approval approved;
- approval rejected;
- approval used by a flag update.

Flag update audit events still record the authenticated actor that performed
the update. Approval audit details retain the requester, approver when present,
approval id, and review context needed to reconstruct the decision path.

## Validation Preview

Validation preview classifies rollout risk for the proposed change and returns
the same policy semantics used by the update path. It does not verify approval
ids, persist flags, write audit events, or consume approvals.

This keeps preview useful for client UX while preserving the write path as the
final enforcement point.

## Known Limitations

- There is no create approval workflow.
- A direct `0 -> 100%` production rollout remains non-approvable.
- Approvals do not expire by time.
- The local API keeps CSRF disabled for stateless JSON clients. HTTP Basic is
  still CSRF-sensitive in browser contexts, so production browser access should
  re-enable CSRF protection or replace Basic authentication.
