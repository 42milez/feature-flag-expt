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

do $$
declare
    constraint_name text;
begin
    select con.conname
    into constraint_name
    from pg_constraint con
    join pg_class rel on rel.oid = con.conrelid
    join pg_namespace nsp on nsp.oid = rel.relnamespace
    where nsp.nspname = 'public'
      and rel.relname = 'audit_events'
      and con.contype = 'c'
      and pg_get_constraintdef(con.oid) like '%event_type%';

    if constraint_name is not null then
        execute format('alter table audit_events drop constraint %I', constraint_name);
    end if;
end $$;

alter table audit_events add constraint audit_events_event_type_check check (
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
