package com.rose.solnax.model.repository;

import com.rose.solnax.model.entity.ChargeSession;
import com.rose.solnax.model.entity.enums.ChargeSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChargeSessionRepository extends JpaRepository<ChargeSession, UUID> {

    Optional<ChargeSession> findByVinAndStatus(String vin, ChargeSessionStatus status);

    List<ChargeSession> findByStatus(ChargeSessionStatus status);

    List<ChargeSession> findByStartedAtBetweenOrderByStartedAtDesc(LocalDateTime start, LocalDateTime end);

    List<ChargeSession> findAllByOrderByStartedAtDesc();
}

