package com.aegis.portfolio;

import com.aegis.shared.SimulationCalendar;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "portfolio")
public class Portfolio {
  @Id private UUID id;

  @Column(name = "owner_id")
  private UUID ownerId;

  @Column(name = "name")
  private String name;

  @Column(name = "currency")
  private String currency;

  @Column(name = "cash", precision = 19, scale = 2)
  private BigDecimal cash;

  @Column(name = "last_trade_date")
  private LocalDate lastTradeDate;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "simulation_start_date", nullable = false, updatable = false)
  private LocalDate simulationStartDate = SimulationCalendar.START;

  public LocalDate getSimulationStartDate() {
    return simulationStartDate;
  }

  protected Portfolio() {}

  public Portfolio(
      UUID id,
      UUID ownerId,
      String name,
      String currency,
      BigDecimal cash,
      LocalDate lastTradeDate,
      Instant createdAt) {
    this.id = id;
    this.ownerId = ownerId;
    this.name = name;
    this.currency = currency;
    this.cash = cash;
    this.lastTradeDate = lastTradeDate;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOwnerId() {
    return ownerId;
  }

  public String getName() {
    return name;
  }

  public String getCurrency() {
    return currency;
  }

  public BigDecimal getCash() {
    return cash;
  }

  public LocalDate getLastTradeDate() {
    return lastTradeDate;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void settle(BigDecimal cash, LocalDate date) {
    this.cash = cash;
    this.lastTradeDate = date;
  }
}
