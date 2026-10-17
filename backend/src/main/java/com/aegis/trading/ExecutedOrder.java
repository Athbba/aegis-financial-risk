package com.aegis.trading;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "executed_order")
public class ExecutedOrder {
  @Id private UUID id;

  @Column(name = "portfolio_id")
  private UUID portfolioId;

  @Column(name = "asset_symbol")
  private String assetSymbol;

  @Column(name = "side")
  private String side;

  @Column(name = "quantity", precision = 19, scale = 6)
  private BigDecimal quantity;

  @Column(name = "execution_price", precision = 19, scale = 4)
  private BigDecimal executionPrice;

  @Column(name = "gross_amount", precision = 19, scale = 2)
  private BigDecimal grossAmount;

  @Column(name = "realized_pnl", precision = 19, scale = 2)
  private BigDecimal realizedPnl;

  @Column(name = "price_date")
  private LocalDate priceDate;

  @Column(name = "executed_at")
  private Instant executedAt;

  @Column(name = "recorded_at")
  private Instant recordedAt;

  @Column(name = "idempotency_key")
  private String idempotencyKey;

  protected ExecutedOrder() {}

  public ExecutedOrder(
      UUID id,
      UUID portfolioId,
      String assetSymbol,
      String side,
      BigDecimal quantity,
      BigDecimal executionPrice,
      BigDecimal grossAmount,
      BigDecimal realizedPnl,
      LocalDate priceDate,
      Instant executedAt,
      Instant recordedAt,
      String idempotencyKey) {
    this.id = id;
    this.portfolioId = portfolioId;
    this.assetSymbol = assetSymbol;
    this.side = side;
    this.quantity = quantity;
    this.executionPrice = executionPrice;
    this.grossAmount = grossAmount;
    this.realizedPnl = realizedPnl;
    this.priceDate = priceDate;
    this.executedAt = executedAt;
    this.recordedAt = recordedAt;
    this.idempotencyKey = idempotencyKey;
  }

  public UUID getId() {
    return id;
  }

  public UUID getPortfolioId() {
    return portfolioId;
  }

  public String getAssetSymbol() {
    return assetSymbol;
  }

  public String getSide() {
    return side;
  }

  public BigDecimal getQuantity() {
    return quantity;
  }

  public BigDecimal getExecutionPrice() {
    return executionPrice;
  }

  public BigDecimal getGrossAmount() {
    return grossAmount;
  }

  public BigDecimal getRealizedPnl() {
    return realizedPnl;
  }

  public LocalDate getPriceDate() {
    return priceDate;
  }

  public Instant getExecutedAt() {
    return executedAt;
  }

  public Instant getRecordedAt() {
    return recordedAt;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }
}
