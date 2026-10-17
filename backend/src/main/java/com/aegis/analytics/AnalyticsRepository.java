package com.aegis.analytics;

import com.aegis.shared.DomainException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AnalyticsRepository {
  public static final int MAX_ORDERS = 10_000;
  public static final int MAX_ASSETS = 100;
  private final JdbcTemplate jdbc;
  private final NamedParameterJdbcTemplate named;

  public AnalyticsRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
    this.named = new NamedParameterJdbcTemplate(jdbc);
  }

  public BigDecimal funding(UUID portfolio) {
    var amounts =
        jdbc.query(
            "select amount from cash_ledger where portfolio_id=? and kind='FUNDING'",
            (rs, n) -> rs.getBigDecimal(1),
            portfolio);
    if (amounts.size() != 1)
      throw DomainException.invalid(
          "FUNDING_UNAVAILABLE", "Exactly one initial funding record is required.");
    return amounts.getFirst();
  }

  public List<ValuationEngine.Event> events(UUID portfolio, LocalDate to) {
    var result =
        jdbc.query(
            """
            select o.*, case when side='BUY' then gross_amount else
                (select sum(a.cost) from sell_allocation a where a.sell_order_id=o.id) end as allocated_cost
            from executed_order o where portfolio_id=? and price_date<=?
            order by price_date, recorded_at, id limit ?
            """,
            (rs, n) -> {
              boolean buy = rs.getString("side").equals("BUY");
              BigDecimal quantity = rs.getBigDecimal("quantity");
              BigDecimal gross = rs.getBigDecimal("gross_amount");
              BigDecimal cost = rs.getBigDecimal("allocated_cost");
              BigDecimal realized = rs.getBigDecimal("realized_pnl");
              if (cost == null || (!buy && gross.subtract(cost).compareTo(realized) != 0))
                throw DomainException.invalid(
                    "ACCOUNTING_DATA_INCONSISTENT",
                    "An execution has missing or inconsistent FIFO allocations.");
              return new ValuationEngine.Event(
                  rs.getObject("price_date", LocalDate.class),
                  rs.getString("asset_symbol"),
                  buy ? quantity : quantity.negate(),
                  buy ? gross.negate() : gross,
                  buy ? cost : cost.negate(),
                  realized);
            },
            portfolio,
            to,
            MAX_ORDERS + 1);
    if (result.size() > MAX_ORDERS) throw limit();
    if (result.stream().map(ValuationEngine.Event::symbol).distinct().count() > MAX_ASSETS)
      throw limit();
    return result;
  }

  public Map<ValuationEngine.PriceKey, BigDecimal> prices(
      Set<String> symbols, LocalDate from, LocalDate to) {
    if (symbols.isEmpty()) return Map.of();
    Map<ValuationEngine.PriceKey, BigDecimal> prices = new HashMap<>();
    named.query(
        "select asset_symbol,price_date,close_price from historical_price where asset_symbol in"
            + " (:symbols) and price_date between :from and :to",
        Map.of("symbols", symbols, "from", from, "to", to),
        rs -> {
          prices.put(
              new ValuationEngine.PriceKey(rs.getString(1), rs.getObject(2, LocalDate.class)),
              rs.getBigDecimal(3));
        });
    return Map.copyOf(prices);
  }

  private static DomainException limit() {
    return DomainException.invalid(
        "ANALYTICS_LIMIT",
        "Analytics supports at most 10,000 executions and 100 traded assets per portfolio.");
  }
}
