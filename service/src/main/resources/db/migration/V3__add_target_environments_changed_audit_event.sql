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
            'KILL_SWITCH_DISABLED'
        )
    );
