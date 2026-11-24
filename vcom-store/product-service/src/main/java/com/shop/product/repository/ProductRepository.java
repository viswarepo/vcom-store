package com.shop.product.repository;

import java.util.List;
import java.util.UUID;

import com.shop.product.domain.Product;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
  List<Product> findByMerchantId(UUID merchantId, Sort sort);
  long countByMerchantId(UUID merchantId);

  @Query("select distinct p.category from Product p where p.merchantId = :merchantId and p.category is not null")
  List<String> findCategories(@Param("merchantId") UUID merchantId);

  interface PriceBounds {
    java.math.BigDecimal getLowest();
    java.math.BigDecimal getHighest();
  }

  @Query("select min(p.price) as lowest, max(p.price) as highest from Product p where p.merchantId = :merchantId")
  PriceBounds findPriceBounds(@Param("merchantId") UUID merchantId);
}
