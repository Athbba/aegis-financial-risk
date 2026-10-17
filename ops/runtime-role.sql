\set ON_ERROR_STOP on
\getenv runtime_user AEGIS_RUNTIME_USER
\getenv runtime_password AEGIS_RUNTIME_PASSWORD
-- Refuse to repurpose an owner/admin role. Identifiers and passwords are SQL-quoted by format.
SELECT :'runtime_user' !~ '^aegis_runtime[a-z0-9_]*$' OR EXISTS(
  SELECT 1 FROM pg_roles WHERE rolname=:'runtime_user' AND
  (rolsuper OR rolcreatedb OR rolcreaterole OR rolbypassrls OR rolreplication)
) OR EXISTS(SELECT 1 FROM pg_tables WHERE schemaname='public' AND tableowner=:'runtime_user') AS unsafe \gset
\if :unsafe
  \echo 'Refusing unsafe runtime role: use a dedicated non-owner aegis_runtime name.'
  \quit 1
\endif
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'runtime_user', :'runtime_password')
  WHERE NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname=:'runtime_user') \gexec
-- Existing role passwords are preserved; rotation is an explicit operator task.
SELECT format('GRANT CONNECT ON DATABASE %I TO %I',current_database(),:'runtime_user') \gexec
SELECT format('GRANT USAGE ON SCHEMA public TO %I',:'runtime_user') \gexec
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
SELECT format('REVOKE ALL ON ALL TABLES IN SCHEMA public FROM %I',:'runtime_user') \gexec
SELECT format('GRANT SELECT ON ALL TABLES IN SCHEMA public TO %I',:'runtime_user') \gexec
SELECT format('GRANT INSERT ON app_user,portfolio,executed_order,position_lot,sell_allocation,cash_ledger,audit_event TO %I',:'runtime_user') \gexec
SELECT format('GRANT UPDATE(cash,last_trade_date) ON portfolio TO %I',:'runtime_user') \gexec
SELECT format('GRANT UPDATE(remaining_quantity,remaining_cost) ON position_lot TO %I',:'runtime_user') \gexec
SELECT format('GRANT INSERT,UPDATE,DELETE ON auth_attempt_window TO %I',:'runtime_user') \gexec
SELECT format('GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO %I',:'runtime_user') \gexec
