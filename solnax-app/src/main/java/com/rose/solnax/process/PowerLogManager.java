package com.rose.solnax.process;


import com.rose.solnax.model.entity.PowerLog;
import com.rose.solnax.model.repository.PowerLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PowerLogManager {



    public final PowerLogRepository powerLogRepository;

    @Transactional
    public PowerLog save(PowerLog powerLog) {
        return powerLogRepository.save(powerLog);
    }


    @Transactional(readOnly = true)
    public PowerLog getLastPowerLog(){
        List<PowerLog> logs = powerLogRepository.findByTimeGreaterThanOrderByTime(LocalDateTime.now().minusMinutes(5));
        if(!logs.isEmpty()){
            return logs.get(0);
        }
        return null;
    }
}
