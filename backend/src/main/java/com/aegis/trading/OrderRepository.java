package com.aegis.trading;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<ExecutedOrder, UUID> {
  Optional<ExecutedOrder> findByPortfolioIdAndIdempotencyKey(UUID portfolioId, String key);

  Page<ExecutedOrder> findByPortfolioId(UUID portfolioId, Pageable pageable);
}
