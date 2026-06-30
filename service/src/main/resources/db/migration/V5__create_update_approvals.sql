create table feature_flag_update_approvals (
    id uuid primary key,
    flag_key text not null,
    requester text not null,
    approver text,
    status text not null check (status in ('PENDING', 'APPROVED', 'REJECTED', 'USED')),
    version bigint not null,
    current_snapshot jsonb not null,
    proposed_snapshot jsonb not null,
    risk_reasons jsonb not null,
    reason text,
    created_at timestamptz not null,
    decided_at timestamptz,
    used_at timestamptz
);

alter table audit_events
    drop constraint audit_events_event_type_check;

alter table audit_events
    add constraint audit_events_event_type_check check (
        event_type in (
            'FLAG_CREATED',
            'FLAG_ENABLED',
            'FLAG_DISABLED',
            'ROLLOUT_PERCENTAGE_CHANGED',
            'TARGET_ENVIRONMENTS_CHANGED',
            'TENANT_ALLOWLIST_CHANGED',
            'KILL_SWITCH_ENABLED',
            'KILL_SWITCH_DISABLED',
            'APPROVAL_REQUESTED',
            'APPROVAL_APPROVED',
            'APPROVAL_REJECTED',
            'APPROVAL_USED'
        )
    );
