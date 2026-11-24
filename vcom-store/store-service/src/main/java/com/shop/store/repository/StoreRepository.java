package com.shop.store.repository;

import java.util.List;
import java.util.Optional;

import com.shop.store.domain.Store;
import org.springframework.data.domain.Sort;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, UUID> {
  Optional<Store> findBySlug(String slug);
  Optional<Store> findByOwnerId(UUID ownerId);
  List<Store> findByActiveTrue(Sort sort);
  boolean existsBySlug(String slug);
  boolean existsByOwnerId(UUID ownerId);
}
