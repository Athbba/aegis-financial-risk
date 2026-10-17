package com.aegis.trading;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;

public interface LotRepository extends JpaRepository<PositionLot, UUID> {
  @Query(
      value =
          "select * from position_lot where portfolio_id = :portfolioId and asset_symbol = :symbol"
              + " and remaining_quantity > 0 order by acquired_at, sequence_id",
      nativeQuery = true)
  List<PositionLot> fifo(UUID portfolioId, String symbol);
}
