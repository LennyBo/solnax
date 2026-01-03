package com.rose.solnax.process;

import com.rose.solnax.model.entity.PowerLog;
import com.rose.solnax.process.adapters.chargepoints.IChargePoint;
import com.rose.solnax.process.adapters.meters.IPowerMeter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobManager {

    private final PowerLogManager powerLogManager;

    private final IChargePoint chargePoint;


    @Scheduled(cron = "0 */5 * * * *")
    void logPower(){
        PowerLog powerLog = powerLogManager.logPower();
        log.info("Logged power log: {}", powerLog);
    }

    @Scheduled(cron="0 */6 6-22 * * *")
    @Transactional(readOnly = true)
    void optimizePower(){
        PowerLog lastLog = powerLogManager.getLastPowerLog();
        if(lastLog == null){
            log.error("No recent log found. Aborting optimization!");
            return;
        }
        log.info("Checking for optimizations");
        if(powerExcess(lastLog)){
            log.info("Starting charge");
            chargePoint.startCharge();
        }else if(powerRecess(lastLog)){
            log.info("Stopping charge");
            chargePoint.stopCharge();
        }
    }

    private boolean powerRecess(PowerLog lastLog) {
        return lastLog.getHouseOut() > 2000 && lastLog.getChargerOut() > 3000;  //means we are charging and importing need to stop
    }

    private boolean powerExcess(PowerLog lastLog) {
        return lastLog.getHouseOut() < -3800 && lastLog.getChargerOut() < 3000; //Means we are not charging and exporting need to start
    }
}
