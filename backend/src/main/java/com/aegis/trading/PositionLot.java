package com.aegis.trading;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "position_lot")
public class PositionLot {
  @Id private UUID id;

  @Column(name = "portfolio_id")
  private UUID portfolioId;

  @Column(name = "asset_symbol")
  private String assetSymbol;

  @Column(name = "buy_order_id")
  private UUID buyOrderId;

  @Column(name = "original_quantity", precision = 19, scale = 6)
  private BigDecimal originalQuantity;

  @Column(name = "remaining_quantity", precision = 19, scale = 6)
  private BigDecimal remainingQuantity;

  @Column(name = "remaining_cost", precision = 19, scale = 2)
  private BigDecimal remainingCost;

  @Column(name = "acquired_at")
  private Instant acquiredAt;

  protected PositionLot() {}

  public PositionLot(
      UUID id,
      UUID portfolioId,
      String assetSymbol,
      UUID buyOrderId,
      BigDecimal originalQuantity,
      BigDecimal remainingQuantity,
      BigDecimal remainingCost,
      Instant acquiredAt) {
    this.id = id;
    this.portfolioId = portfolioId;
    this.assetSymbol = assetSymbol;
    this.buyOrderId = buyOrderId;
    this.originalQuantity = originalQuantity;
    this.remainingQuantity = remainingQuantity;
    this.remainingCost = remainingCost;
    this.acquiredAt = acquiredAt;
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

  public UUID getBuyOrderId() {
    return buyOrderId;
  }

  public BigDecimal getOriginalQuantity() {
    return originalQuantity;
  }

  public BigDecimal getRemainingQuantity() {
    return remainingQuantity;
  }

  public BigDecimal getRemainingCost() {
    return remainingCost;
  }

  public Instant getAcquiredAt() {
    return acquiredAt;
  }

  public void consume(BigDecimal quantity, BigDecimal cost) {
    remainingQuantity = remainingQuantity.subtract(quantity);
    remainingCost = remainingCost.subtract(cost);
  }
}
