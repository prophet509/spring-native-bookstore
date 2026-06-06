-- Outbox retention: delete rows older than the configured window.
--
-- Context: Debezium tails outbox_event via the WAL, but does NOT delete the rows it has
-- published. Without retention the table grows linearly with throughput. This script keeps
-- it bounded; intended to be run on a schedule (cron, k8s CronJob, or pg_cron in production).
--
-- Caller MUST pass: psql -v retention_days=N ...  (the `make outbox-cleanup` target passes 7
-- by default; override with `OUTBOX_RETENTION_DAYS=14 make outbox-cleanup`).
--
-- Why not pg_cron in this stack: the local docker-compose runs postgres:13.4 (stock image)
-- which does not bundle pg_cron. A custom postgres image with pg_cron is preferable in
-- production but adds infra surface here; an external scheduler hitting this script is the
-- least-invasive working alternative.
--
-- Safety: the WHERE clause uses created_at < (now() - interval), so any row not yet picked
-- up by Debezium (slot lag, broker outage) is preserved.

DELETE FROM outbox_event
WHERE created_at < now() - (:'retention_days' || ' days')::interval;

\echo Retention complete on outbox_event (window: :retention_days days)
