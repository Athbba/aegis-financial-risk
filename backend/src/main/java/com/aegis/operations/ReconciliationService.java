package com.aegis.operations;

import com.aegis.analytics.*;
import com.aegis.portfolio.*;
import com.aegis.shared.SimulationCalendar;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 15)
public class ReconciliationService {
  public record Check(String name, boolean passed) {}

  public record Report(String status, Instant checkedAt, List<Check> checks) {}

  private final PortfolioService portfolios;
  private final AnalyticsService analytics;
  private final HistoryRepository history;
  private final JdbcTemplate jdbc;
  private final Clock clock;

  public ReconciliationService(
      PortfolioService portfolios,
      AnalyticsService analytics,
      HistoryRepository history,
      JdbcTemplate jdbc,
      Clock clock) {
    this.portfolios = portfolios;
    this.analytics = analytics;
    this.history = history;
    this.jdbc = jdbc;
    this.clock = clock;
  }

  public Report reconcile(UUID user, UUID id) {
    var portfolio = portfolios.owned(user, id);
    var snapshot = analytics.valuation(user, id, SimulationCalendar.END);
    List<Check> checks = new ArrayList<>();
    BigDecimal ledger =
        jdbc.queryForObject(
            "select coalesce(sum(amount),0) from cash_ledger where portfolio_id=?",
            BigDecimal.class,
            id);
    checks.add(
        new Check(
            "Cash matches immutable executions",
            portfolio.getCash().compareTo(snapshot.cash()) == 0));
    checks.add(
        new Check("Cash matches immutable ledger", portfolio.getCash().compareTo(ledger) == 0));
    Map<String, HistoryRepository.Holding> lots = new TreeMap<>();
    for (var h : history.holdings(id)) lots.put(h.symbol(), h);
    boolean positions = true;
    for (var p : snapshot.positions())
      if (p.quantity().signum() > 0) {
        var h = lots.remove(p.symbol());
        positions &=
            h != null
                && h.quantity().compareTo(p.quantity()) == 0
                && h.costBasis().compareTo(p.costBasis()) == 0;
      }
    checks.add(
        new Check(
            "Open quantities and FIFO costs match immutable history", positions && lots.isEmpty()));
    Long missing =
        jdbc.queryForObject(
            """
            select count(*) from executed_order o left join cash_ledger l on l.order_id=o.id
            where o.portfolio_id=? and (l.id is null or l.portfolio_id<>o.portfolio_id or l.kind<>o.side
              or l.amount<>case when o.side='BUY' then -o.gross_amount else o.gross_amount end
              or not exists(select 1 from audit_event a where a.portfolio_id=o.portfolio_id
                and a.reference_id=o.id and a.event_type='SIMULATED_'||o.side))
            """,
            Long.class,
            id);
    checks.add(new Check("Every execution has matching ledger and audit references", missing == 0));
    return new Report(
        checks.stream().allMatch(Check::passed) ? "CONSISTENT" : "INCONSISTENT",
        clock.instant(),
        List.copyOf(checks));
  }
}
