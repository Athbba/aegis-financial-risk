package com.aegis.market;

import com.aegis.shared.DomainException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MarketService implements HistoricalPriceProvider {
  private final JdbcTemplate jdbc;

  public MarketService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record Quote(
      String symbol,
      String name,
      String sector,
      String currency,
      String datasetVersion,
      LocalDate priceDate,
      BigDecimal closePrice) {}

  public List<Quote> quotes(LocalDate date) {
    return jdbc.query(
        """
        select a.*, h.close_price from asset a
        left join historical_price h on a.symbol=h.asset_symbol and h.price_date=? order by a.symbol
        """,
        (rs, n) ->
            new Quote(
                rs.getString("symbol"),
                rs.getString("name"),
                rs.getString("sector"),
                rs.getString("currency"),
                rs.getString("dataset_version"),
                date,
                rs.getBigDecimal("close_price")),
        date);
  }

  @Override
  public BigDecimal exactClose(String symbol, LocalDate date) {
    var quote =
        quotes(date).stream()
            .filter(q -> q.symbol().equals(symbol))
            .findFirst()
            .orElseThrow(DomainException::missing);
    if (quote.closePrice() == null)
      throw DomainException.invalid(
          "PRICE_UNAVAILABLE",
          "No synthetic closing price exists for this asset and date. Choose a weekday in"
              + " 2022–2025.");
    return quote.closePrice();
  }
}
