package com.shop.payment.repository;

import com.shop.payment.domain.RegistrationCharge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RegistrationRepository extends JpaRepository<RegistrationCharge, UUID> {
}
