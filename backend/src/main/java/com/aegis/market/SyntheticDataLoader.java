package com.aegis.market;

import org.slf4j.*;
import org.springframework.boot.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SyntheticDataLoader implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(SyntheticDataLoader.class);
  private final JdbcTemplate jdbc;

  public SyntheticDataLoader(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    jdbc.queryForList("select pg_advisory_xact_lock(73129)");
    for (var asset : SyntheticPrices.ASSETS) {
      jdbc.update(
          "insert into asset(symbol,name,sector,currency,dataset_version) values(?,?,?,'USD',?) on"
              + " conflict do nothing",
          asset.symbol(),
          asset.name(),
          asset.sector(),
          SyntheticPrices.VERSION);
    }
    var prices = SyntheticPrices.generate();
    jdbc.batchUpdate(
        "insert into historical_price(asset_symbol,price_date,close_price) values(?,?,?) on"
            + " conflict do nothing",
        prices,
        500,
        (ps, p) -> {
          ps.setString(1, p.symbol());
          ps.setObject(2, p.date());
          ps.setBigDecimal(3, p.close());
        });
    log.info(
        "Synthetic dataset available: version={}, generated_rows={}",
        SyntheticPrices.VERSION,
        prices.size());
  }
}
