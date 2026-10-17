package com.aegis.portfolio;

import com.aegis.auth.CurrentUser;
import com.aegis.trading.*;
import com.aegis.web.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/portfolios")
@Validated
public class PortfolioController {
  public record Create(
      @NotBlank @Size(max = 80) String name,
      @NotNull @DecimalMin("0.01") @DecimalMax("1000000000") @Digits(integer = 10, fraction = 2)
          BigDecimal initialCash) {}

  private final CurrentUser auth;
  private final PortfolioService portfolios;
  private final TradingService trading;
  private final OrderRepository orders;
  private final HistoryRepository history;

  public PortfolioController(
      CurrentUser auth,
      PortfolioService portfolios,
      TradingService trading,
      OrderRepository orders,
      HistoryRepository history) {
    this.auth = auth;
    this.portfolios = portfolios;
    this.trading = trading;
    this.orders = orders;
    this.history = history;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  PortfolioService.View create(Authentication user, @Valid @RequestBody Create request) {
    return portfolios.create(auth.id(user), request.name(), request.initialCash());
  }

  @GetMapping
  PageResponse<PortfolioService.View> list(
      Authentication user,
      @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return portfolios.list(auth.id(user), page, size);
  }

  @GetMapping("/{id}")
  PortfolioService.Detail detail(Authentication user, @PathVariable UUID id) {
    return portfolios.detail(auth.id(user), id);
  }

  @PostMapping("/{id}/orders")
  TradingService.Execution execute(
      Authentication user,
      @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9_-]{8,80}") String key,
      @Valid @RequestBody TradingService.Request request) {
    return trading.execute(auth.id(user), id, key, request);
  }

  @GetMapping("/{id}/orders")
  PageResponse<TradingService.Execution> orders(
      Authentication user,
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    portfolios.owned(auth.id(user), id);
    return PageResponse.of(
        orders
            .findByPortfolioId(
                id, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "recordedAt", "id")))
            .map(TradingService.Execution::of));
  }

  @GetMapping("/{id}/orders/by-key/{key}")
  TradingService.Execution byKey(
      Authentication user,
      @PathVariable UUID id,
      @PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{8,80}") String key) {
    portfolios.owned(auth.id(user), id);
    return orders
        .findByPortfolioIdAndIdempotencyKey(id, key)
        .map(TradingService.Execution::of)
        .orElseThrow(com.aegis.shared.DomainException::missing);
  }

  @GetMapping("/{id}/ledger")
  PageResponse<HistoryRepository.Ledger> ledger(
      Authentication user,
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    portfolios.owned(auth.id(user), id);
    return new PageResponse<>(history.ledger(id, page, size), page, size, history.ledgerCount(id));
  }

  @GetMapping("/{id}/audit")
  PageResponse<HistoryRepository.Audit> audit(
      Authentication user,
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    portfolios.owned(auth.id(user), id);
    return new PageResponse<>(history.audit(id, page, size), page, size, history.auditCount(id));
  }
}
