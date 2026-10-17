package com.aegis.market;

import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/assets")
public class MarketController {
  private final MarketService market;

  public MarketController(MarketService market) {
    this.market = market;
  }

  @GetMapping
  List<MarketService.Quote> assets(@RequestParam(defaultValue = "2025-12-31") LocalDate date) {
    return market.quotes(date);
  }
}
