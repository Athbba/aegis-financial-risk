\set ON_ERROR_STOP on
\getenv runtime_user AEGIS_RUNTIME_USER
\getenv runtime_password AEGIS_RUNTIME_PASSWORD
-- New deployment databases deny PUBLIC temporary/schema creation before provisioning.
REVOKE TEMP ON DATABASE :"DBNAME" FROM PUBLIC;
-- Refuse to repurpose an owner/admin role. Identifiers and passwords are SQL-quoted by format.
SELECT :'runtime_user' !~ '^aegis_runtime[a-z0-9_]*$' OR EXISTS(
 SELECT 1 FROM pg_roles r WHERE r.rolname=:'runtime_user' AND (
 r.rolsuper OR r.rolcreatedb OR r.rolcreaterole OR r.rolbypassrls OR r.rolreplication
 OR EXISTS(SELECT 1 FROM pg_auth_members m WHERE m.member=r.oid)
 OR EXISTS(SELECT 1 FROM pg_database WHERE datdba=r.oid)
 OR EXISTS(SELECT 1 FROM pg_namespace WHERE nspowner=r.oid)
 OR EXISTS(SELECT 1 FROM pg_class WHERE relowner=r.oid)
 OR EXISTS(SELECT 1 FROM pg_proc WHERE proowner=r.oid)
 OR has_database_privilege(r.oid,current_database(),'CREATE')
 OR has_database_privilege(r.oid,current_database(),'TEMP')
 OR EXISTS(SELECT 1 FROM pg_namespace n WHERE n.nspname NOT LIKE 'pg_temp_%' AND has_schema_privilege(r.oid,n.oid,'CREATE'))
 OR EXISTS(SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
   WHERE n.nspname='public' AND c.relkind IN ('r','p') AND (
    has_table_privilege(r.oid,c.oid,'TRIGGER,TRUNCATE,REFERENCES')
    OR (c.relname<>'auth_attempt_window' AND has_table_privilege(r.oid,c.oid,'DELETE'))
    OR (c.relname<>'auth_attempt_window' AND has_table_privilege(r.oid,c.oid,'UPDATE'))
    OR (c.relname IN ('asset','historical_price','flyway_schema_history') AND has_table_privilege(r.oid,c.oid,'INSERT'))))
 OR EXISTS(SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace JOIN pg_attribute a ON a.attrelid=c.oid
   WHERE c.relkind IN ('r','p') AND n.nspname NOT IN ('pg_catalog','information_schema') AND a.attnum>0 AND NOT a.attisdropped
   AND has_column_privilege(r.oid,c.oid,a.attnum,'UPDATE') AND NOT (n.nspname='public' AND
    (c.relname='auth_attempt_window' OR (c.relname='portfolio' AND a.attname IN ('cash','last_trade_date'))
     OR (c.relname='position_lot' AND a.attname IN ('remaining_quantity','remaining_cost')))))
 OR EXISTS(SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE c.relkind IN ('r','p')
   AND n.nspname NOT IN ('public','pg_catalog','information_schema')
   AND has_table_privilege(r.oid,c.oid,'INSERT,UPDATE,DELETE,TRIGGER,TRUNCATE,REFERENCES'))
 OR EXISTS(SELECT 1 FROM information_schema.role_table_grants g WHERE g.grantee=r.rolname AND g.is_grantable='YES')
 OR EXISTS(SELECT 1 FROM information_schema.column_privileges g WHERE g.grantee=r.rolname AND g.is_grantable='YES')
 OR EXISTS(SELECT 1 FROM information_schema.column_privileges cp WHERE cp.grantee=r.rolname AND cp.privilege_type='UPDATE'
   AND NOT (cp.table_name='auth_attempt_window' OR (cp.table_name='portfolio' AND cp.column_name IN ('cash','last_trade_date'))
   OR (cp.table_name='position_lot' AND cp.column_name IN ('remaining_quantity','remaining_cost'))))
 )) AS unsafe \gset
\if :unsafe
  \echo 'Refusing unsafe runtime role: use a dedicated non-owner aegis_runtime name.'
  -- psql 17 \quit does not propagate a requested failure status. ON_ERROR_STOP must fail.
  SELECT 1/0;
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
