package com.aegis.operations;

import com.aegis.market.SyntheticDataLoader;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;

/** Separate short-lived process: serving processes need no migration credentials. */
public final class DatabaseMaintenance {
  private DatabaseMaintenance() {}

  public static void migrate(Map<String, String> environment) {
    String url = required(environment, "MIGRATION_DATABASE_URL"),
        user = required(environment, "MIGRATION_USERNAME"),
        password = required(environment, "MIGRATION_PASSWORD");
    var source = new DriverManagerDataSource(url, user, password);
    Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
    new TransactionTemplate(new DataSourceTransactionManager(source))
        .executeWithoutResult(
            status ->
                new SyntheticDataLoader(new JdbcTemplate(source))
                    .run(new DefaultApplicationArguments(new String[0])));
  }

  private static String required(Map<String, String> environment, String key) {
    String value = environment.get(key);
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("Missing required migration configuration: " + key);
    return value;
  }
}
