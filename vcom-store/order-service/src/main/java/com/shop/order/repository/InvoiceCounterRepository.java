package com.shop.order.repository;

import com.shop.order.domain.InvoiceCounter;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceCounterRepository extends JpaRepository<InvoiceCounter, UUID> {
  /** Locks the row so two orders confirmed at once never get the same invoice number. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from InvoiceCounter c where c.merchantId = :id")
  Optional<InvoiceCounter> lockFor(@Param("id") UUID id);
}
