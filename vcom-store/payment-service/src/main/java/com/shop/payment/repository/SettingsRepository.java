package com.shop.payment.repository;

import com.shop.payment.domain.PlatformSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingsRepository extends JpaRepository<PlatformSettings, Long> {}
