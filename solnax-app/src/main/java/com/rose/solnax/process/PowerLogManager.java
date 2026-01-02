package com.rose.solnax.process;


import com.rose.solnax.model.dto.PowerLogs;
import com.rose.solnax.model.entity.PowerLog;
import com.rose.solnax.model.repository.PowerLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collector;

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

    @Transactional(readOnly = true)
    public PowerLogs getPowerLogDTOForPeriod(LocalDateTime start, LocalDateTime stop){
        if(start.isAfter(stop)){
            throw new IllegalArgumentException("Start is after stop");
        }
        List<PowerLog> powerLogsOfTimePeriod = powerLogRepository.findByTimeBetweenOrderByTimeAsc(start, stop);
        return mapToPowerLogs(powerLogsOfTimePeriod);
    }

    private PowerLogs mapToPowerLogs(List<PowerLog> powerLogsOfTimePeriod) {
        return powerLogsOfTimePeriod.stream().collect(
                Collector.of(
                        PowerLogs::new,
                        (t,s) -> {
                            t.getTimes().add(s.getTime().toLocalTime());
                            t.getSolarIn().add(s.getSolarIn());
                            t.getHouse().add(s.getSolarIn() + s.getHouseOut());
                        },
                        (t1,t2) -> {
                            t1.getTimes().addAll(t2.getTimes());
                            t1.getSolarIn().addAll(t2.getSolarIn());
                            t1.getHouse().addAll(t2.getHouse());
                            return t1;
                        }
                )
        );
    }
}
