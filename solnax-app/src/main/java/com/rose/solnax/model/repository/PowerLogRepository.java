package com.rose.solnax.model.repository;

import com.rose.solnax.model.entity.PowerLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PowerLogRepository extends JpaRepository<PowerLog, LocalDateTime> {

    List<PowerLog> findByTimeGreaterThanOrderByTime(LocalDateTime time);

}
