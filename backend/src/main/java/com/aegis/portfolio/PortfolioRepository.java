package com.aegis.portfolio;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface PortfolioRepository extends JpaRepository<Portfolio, UUID> {
  Page<Portfolio> findByOwnerId(UUID ownerId, Pageable pageable);

  Optional<Portfolio> findByIdAndOwnerId(UUID id, UUID ownerId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Portfolio p where p.id = :id and p.ownerId = :ownerId")
  Optional<Portfolio> lockOwned(UUID id, UUID ownerId);
}
