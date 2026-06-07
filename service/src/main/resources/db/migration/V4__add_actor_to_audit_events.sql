-- Use a temporary default to backfill existing audit events while adding a non-null
-- column, then remove it so future inserts must provide the authenticated actor.
alter table audit_events add column actor text not null default 'unknown';

alter table audit_events alter column actor drop default;
