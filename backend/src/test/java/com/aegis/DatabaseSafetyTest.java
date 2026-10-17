package com.aegis;

import static org.assertj.core.api.Assertions.*;

import com.aegis.auth.*;
import com.aegis.operations.*;
import com.aegis.portfolio.*;
import com.aegis.trading.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

@SpringBootTest(properties = {"spring.flyway.enabled=false", "aegis.seed-data=false"})
@Testcontainers
class DatabaseSafetyTest {
  @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
  static final String RUNTIME = "aegis_runtime_test", PASSWORD = UUID.randomUUID().toString();

  static JdbcTemplate admin() {
    return new JdbcTemplate(
        new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
  }

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) throws Exception {
    DatabaseMaintenance.migrate(
        Map.of(
            "MIGRATION_DATABASE_URL",
            postgres.getJdbcUrl(),
            "MIGRATION_USERNAME",
            postgres.getUsername(),
            "MIGRATION_PASSWORD",
            postgres.getPassword()));
    postgres.copyFileToContainer(
        MountableFile.forHostPath(Path.of("../ops/runtime-role.sql").toAbsolutePath()),
        "/tmp/runtime-role.sql");
    var provision =
        postgres.execInContainer(
            "env",
            "AEGIS_RUNTIME_USER=" + RUNTIME,
            "AEGIS_RUNTIME_PASSWORD=" + PASSWORD,
            "psql",
            "-U",
            postgres.getUsername(),
            "-d",
            postgres.getDatabaseName(),
            "-f",
            "/tmp/runtime-role.sql");
    assertThat(provision.getExitCode()).describedAs("Disposable runtime grants").isZero();
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", () -> RUNTIME);
    r.add("spring.datasource.password", () -> PASSWORD);
  }

  @Autowired AuthService auth;
  @Autowired PortfolioService portfolios;
  @Autowired TradingService trading;
  @Autowired ReconciliationService reconciliation;
  @Autowired JdbcTemplate runtime;

  record Fixture(UUID user, UUID portfolio) {}

  Fixture fixture() {
    var u = auth.register(UUID.randomUUID() + "@example.test", "database-safety-password");
    var p = portfolios.create(u.id(), "Disposable database fixture", new BigDecimal("10000"));
    var day = LocalDate.of(2024, 1, 2);
    trading.execute(
        u.id(),
        p.id(),
        "database-buy",
        new TradingService.Request("ALTA", TradingService.Side.BUY, new BigDecimal("10"), day));
    trading.execute(
        u.id(),
        p.id(),
        "database-sell",
        new TradingService.Request("ALTA", TradingService.Side.SELL, new BigDecimal("4"), day));
    return new Fixture(u.id(), p.id());
  }

  static Map<String, String> fingerprint(JdbcTemplate jdbc) {
    Map<String, String> result = new TreeMap<>();
    for (String table :
        List.of(
            "app_user",
            "portfolio",
            "asset",
            "historical_price",
            "executed_order",
            "position_lot",
            "sell_allocation",
            "cash_ledger",
            "audit_event"))
      result.put(
          table,
          jdbc.queryForObject(
              "select md5(coalesce(string_agg(to_jsonb(t)::text,',' order by "
                  + (table.equals("asset") ? "symbol" : "id::text")
                  + "),'')) from "
                  + table
                  + " t",
              String.class));
    return result;
  }

  @Test
  void leastPrivilegeCanRunExistingTradingAuthAndReconciliation() {
    var f = fixture();
    assertThat(runtime.queryForObject("select current_user", String.class)).isEqualTo(RUNTIME);
    assertThat(reconciliation.reconcile(f.user(), f.portfolio()).status()).isEqualTo("CONSISTENT");
    assertThat(
            auth.authenticate(usersEmail(f.user()), "database-safety-password").isAuthenticated())
        .isTrue();
    for (String sql :
        List.of(
            "delete from cash_ledger",
            "update executed_order set quantity=quantity",
            "update sell_allocation set cost=cost",
            "update historical_price set close_price=close_price",
            "update app_user set email=email",
            "alter table cash_ledger disable trigger all",
            "create table forbidden(id int)"))
      assertThatThrownBy(() -> runtime.execute(sql))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThat(reconciliation.reconcile(f.user(), f.portfolio()).status()).isEqualTo("CONSISTENT");
  }

  String usersEmail(UUID user) {
    return runtime.queryForObject("select email from app_user where id=?", String.class, user);
  }

  @Test
  void customBackupRestoresOnlyIntoNewDisposableDatabaseAndPreservesEveryFinancialRow()
      throws Exception {
    fixture();
    var before = fingerprint(admin());
    for (String script : List.of("backup-db.sh", "restore-disposable-db.sh"))
      postgres.copyFileToContainer(
          MountableFile.forHostPath(Path.of("../scripts/" + script).toAbsolutePath()),
          "/tmp/" + script);
    var backup =
        postgres.execInContainer(
            "env",
            "PGUSER=" + postgres.getUsername(),
            "PGDATABASE=" + postgres.getDatabaseName(),
            "bash",
            "/tmp/backup-db.sh",
            "/tmp/engineering.dump");
    assertThat(backup.getExitCode()).isZero();
    var restore =
        postgres.execInContainer(
            "env",
            "PGUSER=" + postgres.getUsername(),
            "PGDATABASE=" + postgres.getDatabaseName(),
            "bash",
            "/tmp/restore-disposable-db.sh",
            "/tmp/engineering.dump",
            "aegis_restore_engineering");
    assertThat(restore.getExitCode()).isZero();
    String url =
        postgres
            .getJdbcUrl()
            .replace("/" + postgres.getDatabaseName(), "/aegis_restore_engineering");
    var restored =
        new JdbcTemplate(
            new DriverManagerDataSource(url, postgres.getUsername(), postgres.getPassword()));
    assertThat(fingerprint(restored)).isEqualTo(before);
    assertThat(fingerprint(admin())).isEqualTo(before);
    var refusal =
        postgres.execInContainer(
            "env",
            "PGUSER=" + postgres.getUsername(),
            "PGDATABASE=" + postgres.getDatabaseName(),
            "bash",
            "/tmp/restore-disposable-db.sh",
            "/tmp/engineering.dump",
            postgres.getDatabaseName());
    assertThat(refusal.getExitCode()).isNotZero();
    var repeat =
        postgres.execInContainer(
            "env",
            "PGUSER=" + postgres.getUsername(),
            "PGDATABASE=" + postgres.getDatabaseName(),
            "bash",
            "/tmp/restore-disposable-db.sh",
            "/tmp/engineering.dump",
            "aegis_restore_engineering");
    assertThat(repeat.getExitCode()).isNotZero();
  }

  @Test
  void v2ToV3MigrationPreservesRealFinancialFixture() {
    admin().execute("create database aegis_restore_v2");
    String url =
        postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName(), "/aegis_restore_v2");
    var source = new DriverManagerDataSource(url, postgres.getUsername(), postgres.getPassword());
    var jdbc = new JdbcTemplate(source);
    Flyway.configure().dataSource(source).target("2").load().migrate();
    UUID user = UUID.randomUUID(), portfolio = UUID.randomUUID(), order = UUID.randomUUID();
    jdbc.update(
        "insert into app_user values(?,?,'test-fixture-only',now())",
        user,
        "migration@example.test");
    jdbc.update("insert into asset values('MIG','Migration fixture','Test','USD','test-only')");
    jdbc.update(
        "insert into historical_price(asset_symbol,price_date,close_price)"
            + " values('MIG','2024-01-02',10)");
    jdbc.update(
        "insert into portfolio(id,owner_id,name,currency,cash,last_trade_date,created_at)"
            + " values(?,?,'Preserve','USD',990,'2024-01-02',now())",
        portfolio,
        user);
    jdbc.update(
        "insert into executed_order"
            + " values(?,?,'MIG','BUY',1,10,10,0,'2024-01-02',now(),now(),'migration-key')",
        order,
        portfolio);
    jdbc.update(
        "insert into"
            + " position_lot(id,portfolio_id,asset_symbol,buy_order_id,original_quantity,remaining_quantity,remaining_cost,acquired_at)"
            + " values(?,?,'MIG',?,1,1,10,now())",
        UUID.randomUUID(),
        portfolio,
        order);
    jdbc.update(
        "insert into cash_ledger values(?,?,null,'FUNDING',1000,1000,now())",
        UUID.randomUUID(),
        portfolio);
    jdbc.update(
        "insert into cash_ledger values(?,?,?,'BUY',-10,990,now())",
        UUID.randomUUID(),
        portfolio,
        order);
    jdbc.update(
        "insert into audit_event values(?,?,?,'SIMULATED_BUY',?,now())",
        UUID.randomUUID(),
        portfolio,
        user,
        order);
    var before = fingerprint(jdbc);
    Flyway.configure().dataSource(source).load().migrate();
    assertThat(fingerprint(jdbc)).isEqualTo(before);
    assertThat(jdbc.queryForObject("select count(*) from auth_attempt_window", Long.class))
        .isZero();
  }
}
