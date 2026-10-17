package com.aegis.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Append-only history writes. Database triggers prohibit updates/deletes by ordinary application
 * SQL.
 */
@Repository
public class HistoryRepository {
  private final JdbcTemplate jdbc;

  public HistoryRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record Ledger(
      UUID id,
      UUID orderId,
      String kind,
      BigDecimal amount,
      BigDecimal balanceAfter,
      Instant recordedAt) {}

  public record Audit(UUID id, String eventType, UUID referenceId, Instant recordedAt) {}

  public record Holding(String symbol, BigDecimal quantity, BigDecimal costBasis) {}

  public void ledger(
      UUID portfolio, UUID order, String kind, BigDecimal amount, BigDecimal balance, Instant now) {
    jdbc.update(
        "insert into cash_ledger(id,portfolio_id,order_id,kind,amount,balance_after,recorded_at)"
            + " values(?,?,?,?,?,?,?)",
        UUID.randomUUID(),
        portfolio,
        order,
        kind,
        amount,
        balance,
        java.sql.Timestamp.from(now));
  }

  public void audit(UUID portfolio, UUID user, String type, UUID reference, Instant now) {
    jdbc.update(
        "insert into audit_event(id,portfolio_id,actor_id,event_type,reference_id,recorded_at)"
            + " values(?,?,?,?,?,?)",
        UUID.randomUUID(),
        portfolio,
        user,
        type,
        reference,
        java.sql.Timestamp.from(now));
  }

  public void allocation(UUID order, UUID lot, BigDecimal quantity, BigDecimal cost) {
    jdbc.update(
        "insert into sell_allocation(id,sell_order_id,lot_id,quantity,cost) values(?,?,?,?,?)",
        UUID.randomUUID(),
        order,
        lot,
        quantity,
        cost);
  }

  public List<Holding> holdings(UUID portfolio) {
    return jdbc.query(
        "select asset_symbol, sum(remaining_quantity) quantity, sum(remaining_cost) cost from"
            + " position_lot where portfolio_id=? and remaining_quantity>0 group by asset_symbol"
            + " order by asset_symbol",
        (rs, n) -> new Holding(rs.getString(1), rs.getBigDecimal(2), rs.getBigDecimal(3)),
        portfolio);
  }

  public List<Ledger> ledger(UUID portfolio, int page, int size) {
    return jdbc.query(
        "select * from cash_ledger where portfolio_id=? order by recorded_at desc,id limit ? offset"
            + " ?",
        (rs, n) ->
            new Ledger(
                rs.getObject("id", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getString("kind"),
                rs.getBigDecimal("amount"),
                rs.getBigDecimal("balance_after"),
                rs.getTimestamp("recorded_at").toInstant()),
        portfolio,
        size,
        (long) page * size);
  }

  public List<Audit> audit(UUID portfolio, int page, int size) {
    return jdbc.query(
        "select * from audit_event where portfolio_id=? order by recorded_at desc,id limit ? offset"
            + " ?",
        (rs, n) ->
            new Audit(
                rs.getObject("id", UUID.class),
                rs.getString("event_type"),
                rs.getObject("reference_id", UUID.class),
                rs.getTimestamp("recorded_at").toInstant()),
        portfolio,
        size,
        (long) page * size);
  }

  public long ledgerCount(UUID portfolio) {
    return jdbc.queryForObject(
        "select count(*) from cash_ledger where portfolio_id=?", Long.class, portfolio);
  }

  public long auditCount(UUID portfolio) {
    return jdbc.queryForObject(
        "select count(*) from audit_event where portfolio_id=?", Long.class, portfolio);
  }
}
