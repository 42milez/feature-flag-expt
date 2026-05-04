create table feature_flags (
    flag_key text primary key,
    status text not null,
    kill_switch_active boolean not null,
    rollout_percentage integer not null check (rollout_percentage between 0 and 100)
);

create table feature_flag_target_environments (
    flag_key text not null references feature_flags(flag_key) on delete cascade,
    environment text not null,
    primary key (flag_key, environment)
);

create table feature_flag_tenant_allowlist (
    flag_key text not null references feature_flags(flag_key) on delete cascade,
    tenant_id text not null,
    primary key (flag_key, tenant_id)
);
