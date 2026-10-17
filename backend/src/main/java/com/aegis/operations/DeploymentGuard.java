package com.aegis.operations;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Fail closed in the distinct deployment profile; local-development defaults are unchanged. */
@Component
@Profile("deployment")
public class DeploymentGuard {
  private final Environment env;
  private final JdbcTemplate jdbc;

  public DeploymentGuard(Environment env, JdbcTemplate jdbc) {
    this.env = env;
    this.jdbc = jdbc;
  }

  @PostConstruct
  public void verify() {
    for (String key : new String[] {"spring.flyway.enabled", "aegis.seed-data"})
      if (!"false".equals(env.getProperty(key)))
        throw new IllegalStateException("Unsafe deployment setting: " + key);
    if (!env.getProperty("server.servlet.session.cookie.secure", "false").equals("true")
        || !env.getProperty("aegis.trusted-proxy", "").matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")
        || !java.util.Set.of("closed", "open").contains(env.getProperty("aegis.enrollment", "")))
      throw new IllegalStateException(
          "Deployment requires secure cookies, exact private proxy and explicit enrollment");
    for (String key :
        new String[] {"MIGRATION_PASSWORD", "POSTGRES_PASSWORD", "MIGRATION_USERNAME"})
      if (env.containsProperty(key))
        throw new IllegalStateException(
            "Migration/admin credentials must not enter serving process");
    Boolean unsafe =
        jdbc.queryForObject(
            """
               select not (current_user ~ '^aegis_runtime[a-z0-9_]*$') or exists(
               select 1 from pg_roles r where r.rolname=current_user and
                (rolsuper or rolcreatedb or rolcreaterole or rolreplication or rolbypassrls
                or exists(select 1 from pg_roles x where x.oid<>r.oid and pg_has_role(r.oid,x.oid,'MEMBER'))
                or exists(select 1 from pg_database where datdba=r.oid)
                or exists(select 1 from pg_namespace where nspowner=r.oid)
                or exists(select 1 from pg_class where relowner=r.oid)
                or exists(select 1 from pg_proc where proowner=r.oid)
                or has_database_privilege(r.oid,current_database(),'CREATE,TEMP')
                or exists(select 1 from pg_namespace n where has_schema_privilege(r.oid,n.oid,'CREATE'))
                or exists(select 1 from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relkind in ('r','p') and
                 (has_table_privilege(r.oid,c.oid,'TRIGGER,TRUNCATE,REFERENCES')
                 or (c.relname<>'auth_attempt_window' and has_table_privilege(r.oid,c.oid,'UPDATE,DELETE'))
                 or (c.relname in ('asset','historical_price','flyway_schema_history') and has_table_privilege(r.oid,c.oid,'INSERT'))))
                or exists(select 1 from pg_class c join pg_namespace n on n.oid=c.relnamespace join pg_attribute a on a.attrelid=c.oid
             where c.relkind in ('r','p') and n.nspname not in ('pg_catalog','information_schema') and a.attnum>0 and not a.attisdropped
             and has_column_privilege(r.oid,c.oid,a.attnum,'UPDATE') and not (n.nspname='public' and
              (c.relname='auth_attempt_window' or (c.relname='portfolio' and a.attname in ('cash','last_trade_date'))
               or (c.relname='position_lot' and a.attname in ('remaining_quantity','remaining_cost')))))
            or exists(select 1 from pg_class c join pg_namespace n on n.oid=c.relnamespace where c.relkind in ('r','p')
             and n.nspname not in ('public','pg_catalog','information_schema') and has_table_privilege(r.oid,c.oid,'INSERT,UPDATE,DELETE,TRIGGER,TRUNCATE,REFERENCES'))
            or exists(select 1 from information_schema.role_table_grants g where g.grantee=current_user and g.is_grantable='YES')
                or exists(select 1 from information_schema.column_privileges g where g.grantee=current_user and g.is_grantable='YES')
                or exists(select 1 from information_schema.column_privileges cp where cp.grantee=current_user and cp.privilege_type='UPDATE' and not
                 (cp.table_name='auth_attempt_window' or (cp.table_name='portfolio' and cp.column_name in ('cash','last_trade_date'))
                  or (cp.table_name='position_lot' and cp.column_name in ('remaining_quantity','remaining_cost'))))))
            """,
            Boolean.class);
    if (!Boolean.FALSE.equals(unsafe))
      throw new IllegalStateException("Unsafe effective runtime database privileges");
  }
}
