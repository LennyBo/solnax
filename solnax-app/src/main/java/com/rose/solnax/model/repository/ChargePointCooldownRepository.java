package com.rose.solnax.model.repository;

import com.rose.solnax.model.entity.ChargePointCoolDown;
import com.rose.solnax.model.entity.enums.CoolDownReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChargePointCooldownRepository extends JpaRepository<ChargePointCoolDown, LocalDateTime> {
    List<ChargePointCoolDown> findAllByEndAfter(LocalDateTime localDateTime);
    boolean existsByEndAfterAndReason(LocalDateTime localDateTime, CoolDownReason reason);

    @Modifying
    int deleteAllByEndAfter(LocalDateTime localDateTime);
}
