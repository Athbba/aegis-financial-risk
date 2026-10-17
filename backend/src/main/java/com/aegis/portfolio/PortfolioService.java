package com.aegis.portfolio;

import com.aegis.shared.DomainException;
import com.aegis.web.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class PortfolioService {
  private final PortfolioRepository portfolios;
  private final HistoryRepository history;

  public PortfolioService(PortfolioRepository portfolios, HistoryRepository history) {
    this.portfolios = portfolios;
    this.history = history;
  }

  public record View(
      UUID id,
      String name,
      String currency,
      BigDecimal cash,
      java.time.LocalDate lastTradeDate,
      Instant createdAt) {
    public static View of(Portfolio p) {
      return new View(
          p.getId(),
          p.getName(),
          p.getCurrency(),
          p.getCash(),
          p.getLastTradeDate(),
          p.getCreatedAt());
    }
  }

  public record Detail(View portfolio, List<HistoryRepository.Holding> holdings) {}

  @Transactional
  public View create(UUID user, String name, BigDecimal funding) {
    Instant now = Instant.now();
    Portfolio p =
        portfolios.saveAndFlush(
            new Portfolio(
                UUID.randomUUID(), user, name.strip(), "USD", funding.setScale(2), null, now));
    history.ledger(p.getId(), null, "FUNDING", p.getCash(), p.getCash(), now);
    history.audit(p.getId(), user, "PORTFOLIO_FUNDED", p.getId(), now);
    return View.of(p);
  }

  public Portfolio owned(UUID user, UUID id) {
    return portfolios.findByIdAndOwnerId(id, user).orElseThrow(DomainException::missing);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Detail detail(UUID user, UUID id) {
    return new Detail(View.of(owned(user, id)), history.holdings(id));
  }

  public PageResponse<View> list(UUID user, int page, int size) {
    return PageResponse.of(
        portfolios
            .findByOwnerId(
                user, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")))
            .map(View::of));
  }
}
