package com.aegis.risk;

import com.aegis.auth.CurrentUser;
import java.time.LocalDate;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/portfolios/{id}/risk")
public class RiskController {
  private final CurrentUser auth;
  private final RiskService risk;

  public RiskController(CurrentUser auth, RiskService risk) {
    this.auth = auth;
    this.risk = risk;
  }

  @GetMapping("/summary")
  RiskService.Summary summary(
      Authentication user,
      @PathVariable UUID id,
      @RequestParam(defaultValue = "2025-12-31") LocalDate date,
      @RequestParam(defaultValue = "730") int lookbackDays) {
    return risk.summary(auth.id(user), id, date, lookbackDays);
  }

  @GetMapping("/returns")
  RiskEngine.Returns returns(
      Authentication user,
      @PathVariable UUID id,
      @RequestParam(defaultValue = "2025-12-31") LocalDate date,
      @RequestParam(defaultValue = "730") int lookbackDays) {
    return risk.summary(auth.id(user), id, date, lookbackDays).analysis().returns();
  }

  @GetMapping("/drawdown")
  RiskEngine.Drawdown drawdown(
      Authentication user,
      @PathVariable UUID id,
      @RequestParam(defaultValue = "2025-12-31") LocalDate date,
      @RequestParam(defaultValue = "730") int lookbackDays) {
    return risk.summary(auth.id(user), id, date, lookbackDays).drawdown();
  }

  @PostMapping("/stress")
  List<RiskEngine.StressResult> stress(
      Authentication user, @PathVariable UUID id, @RequestBody RiskService.StressRequest request) {
    return risk.stress(auth.id(user), id, request);
  }
}
