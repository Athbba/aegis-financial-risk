package com.aegis.trading;

import com.aegis.shared.DomainException;
import java.math.*;
import java.util.*;

/** Pure decimal accounting. Settlement/cost are USD cents using HALF_EVEN. */
public final class FinancialMath {
  private FinancialMath() {}

  public record Lot(UUID id, BigDecimal quantity, BigDecimal cost) {}

  public record Consumption(UUID lotId, BigDecimal quantity, BigDecimal cost) {}

  public record Sale(
      List<Consumption> consumptions, BigDecimal totalCost, BigDecimal realizedPnl) {}

  public static BigDecimal gross(BigDecimal quantity, BigDecimal price) {
    if (quantity.signum() <= 0 || price.signum() <= 0)
      throw DomainException.invalid("INVALID_ORDER", "Quantity and price must be positive.");
    BigDecimal total = quantity.multiply(price).setScale(2, RoundingMode.HALF_EVEN);
    if (total.signum() <= 0)
      throw DomainException.invalid(
          "ORDER_TOO_SMALL", "Order value must round to at least USD 0.01.");
    return total;
  }

  public static Sale fifo(List<Lot> lots, BigDecimal quantity, BigDecimal proceeds) {
    if (quantity.signum() <= 0)
      throw DomainException.invalid("INVALID_QUANTITY", "Quantity must be positive.");
    BigDecimal available =
        lots.stream().map(Lot::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
    if (available.compareTo(quantity) < 0)
      throw DomainException.invalid("INSUFFICIENT_HOLDINGS", "You cannot sell more than you own.");
    BigDecimal remaining = quantity;
    BigDecimal totalCost = new BigDecimal("0.00");
    List<Consumption> consumed = new ArrayList<>();
    for (Lot lot : lots) {
      if (remaining.signum() == 0) break;
      BigDecimal taken = remaining.min(lot.quantity());
      BigDecimal cost =
          taken.compareTo(lot.quantity()) == 0
              ? lot.cost()
              : lot.cost().multiply(taken).divide(lot.quantity(), 2, RoundingMode.HALF_EVEN);
      consumed.add(new Consumption(lot.id(), taken, cost));
      totalCost = totalCost.add(cost);
      remaining = remaining.subtract(taken);
    }
    return new Sale(List.copyOf(consumed), totalCost, proceeds.subtract(totalCost));
  }
}
