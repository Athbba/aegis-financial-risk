package com.aegis.trading;

import com.aegis.market.HistoricalPriceProvider;
import com.aegis.portfolio.*;
import com.aegis.shared.DomainException;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TradingService {
  public enum Side {
    BUY,
    SELL
  }

  public record Request(
      @NotBlank @Pattern(regexp = "[A-Z]{1,12}") String symbol,
      @NotNull Side side,
      @NotNull @DecimalMin("0.000001") @DecimalMax("1000000") @Digits(integer = 7, fraction = 6)
          BigDecimal quantity,
      @NotNull LocalDate priceDate) {}

  public record Execution(
      UUID id,
      UUID portfolioId,
      String symbol,
      String side,
      BigDecimal quantity,
      BigDecimal executionPrice,
      BigDecimal grossAmount,
      BigDecimal realizedPnl,
      LocalDate priceDate,
      Instant executedAt,
      Instant recordedAt,
      String idempotencyKey) {
    public static Execution of(ExecutedOrder o) {
      return new Execution(
          o.getId(),
          o.getPortfolioId(),
          o.getAssetSymbol(),
          o.getSide(),
          o.getQuantity(),
          o.getExecutionPrice(),
          o.getGrossAmount(),
          o.getRealizedPnl(),
          o.getPriceDate(),
          o.getExecutedAt(),
          o.getRecordedAt(),
          o.getIdempotencyKey());
    }
  }

  private final PortfolioRepository portfolios;
  private final OrderRepository orders;
  private final LotRepository lots;
  private final HistoricalPriceProvider prices;
  private final HistoryRepository history;
  private final com.aegis.operations.TradeTelemetry telemetry;

  public TradingService(
      PortfolioRepository portfolios,
      OrderRepository orders,
      LotRepository lots,
      HistoricalPriceProvider prices,
      HistoryRepository history,
      com.aegis.operations.TradeTelemetry telemetry) {
    this.portfolios = portfolios;
    this.orders = orders;
    this.lots = lots;
    this.prices = prices;
    this.history = history;
    this.telemetry = telemetry;
  }

  @Transactional(timeout = 15)
  public Execution execute(UUID user, UUID portfolioId, String key, Request request) {
    var observation = telemetry.begin(request.side());
    try {
      return executeLocked(user, portfolioId, key, request, observation);
    } catch (DomainException e) {
      observation.rejected();
      throw e;
    }
  }

  private Execution executeLocked(
      UUID user,
      UUID portfolioId,
      String key,
      Request request,
      com.aegis.operations.TradeTelemetry.Observation observation) {
    // Lock first, then look up the key: simultaneous duplicates observe the committed prior
    // execution.
    Portfolio portfolio =
        portfolios.lockOwned(portfolioId, user).orElseThrow(DomainException::missing);
    var previous = orders.findByPortfolioIdAndIdempotencyKey(portfolioId, key);
    if (previous.isPresent()) {
      ExecutedOrder o = previous.get();
      if (!o.getAssetSymbol().equals(request.symbol())
          || !o.getSide().equals(request.side().name())
          || o.getQuantity().compareTo(request.quantity()) != 0
          || !o.getPriceDate().equals(request.priceDate()))
        throw new DomainException(
            "IDEMPOTENCY_CONFLICT",
            "This key was already used for a different order.",
            DomainException.Kind.CONFLICT);
      observation.replay();
      return Execution.of(o);
    }
    if (portfolio.getLastTradeDate() != null
        && request.priceDate().isBefore(portfolio.getLastTradeDate()))
      throw DomainException.invalid(
          "DATE_OUT_OF_ORDER", "Execution date cannot precede this portfolio's last trade date.");
    BigDecimal price = prices.exactClose(request.symbol(), request.priceDate());
    BigDecimal gross = FinancialMath.gross(request.quantity(), price);
    BigDecimal cash = portfolio.getCash();
    BigDecimal pnl = new BigDecimal("0.00");
    FinancialMath.Sale sale = null;
    List<PositionLot> ownedLots = List.of();
    if (request.side() == Side.BUY) {
      if (cash.compareTo(gross) < 0)
        throw DomainException.invalid(
            "INSUFFICIENT_CASH", "Insufficient simulated cash for this purchase.");
      cash = cash.subtract(gross);
    } else {
      ownedLots = lots.fifo(portfolioId, request.symbol());
      sale =
          FinancialMath.fifo(
              ownedLots.stream()
                  .map(
                      l ->
                          new FinancialMath.Lot(
                              l.getId(), l.getRemainingQuantity(), l.getRemainingCost()))
                  .toList(),
              request.quantity(),
              gross);
      pnl = sale.realizedPnl();
      cash = cash.add(gross);
    }
    if (cash.precision() - cash.scale() > 17)
      throw DomainException.invalid(
          "BALANCE_LIMIT", "This trade exceeds the supported cash balance.");
    Instant now = Instant.now();
    Instant executionTime = request.priceDate().atTime(21, 0).toInstant(ZoneOffset.UTC);
    ExecutedOrder order =
        orders.saveAndFlush(
            new ExecutedOrder(
                UUID.randomUUID(),
                portfolioId,
                request.symbol(),
                request.side().name(),
                request.quantity(),
                price,
                gross,
                pnl,
                request.priceDate(),
                executionTime,
                now,
                key));
    if (request.side() == Side.BUY) {
      lots.save(
          new PositionLot(
              UUID.randomUUID(),
              portfolioId,
              request.symbol(),
              order.getId(),
              request.quantity(),
              request.quantity(),
              gross,
              executionTime));
    } else {
      for (var consumption : sale.consumptions()) {
        PositionLot lot =
            ownedLots.stream()
                .filter(l -> l.getId().equals(consumption.lotId()))
                .findFirst()
                .orElseThrow();
        lot.consume(consumption.quantity(), consumption.cost());
        history.allocation(order.getId(), lot.getId(), consumption.quantity(), consumption.cost());
      }
    }
    portfolio.settle(cash, request.priceDate());
    history.ledger(
        portfolioId,
        order.getId(),
        request.side().name(),
        request.side() == Side.BUY ? gross.negate() : gross,
        cash,
        now);
    history.audit(portfolioId, user, "SIMULATED_" + request.side().name(), order.getId(), now);
    return Execution.of(order);
  }
}
