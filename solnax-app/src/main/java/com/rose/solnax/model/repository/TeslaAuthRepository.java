package com.rose.solnax.model.repository;

import com.rose.solnax.model.entity.TeslaAuth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TeslaAuthRepository extends JpaRepository<TeslaAuth, Long> {
}
