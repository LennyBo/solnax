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

    private static final int IMPORT_THRESHOLD = 2000; //If we buy more than this we try to lower consumption
    private static final int EXPORT_THRESHOLD = -3000; //If we export more than that we try to optimize
    private static final int CHARGER_MIN_POWER = 3000; //If charger is above this level we assume already charging


    @Scheduled(cron = "0 */5 * * * *")
    void logPower(){
        PowerLog powerLog = powerLogManager.logPower();
        log.info("Logged power log: {}", powerLog);
    }



    @Scheduled(cron = "0 */6 6-22 * * *")
    @Transactional(readOnly = true)
    void optimizePower() {
        PowerLog lastLog = powerLogManager.getLastPowerLog();
        if (lastLog == null) {
            log.error("No recent log found. Aborting optimization!");
            return;
        }

        boolean isCharging = lastLog.getChargerOut() >= CHARGER_MIN_POWER;
        boolean isImporting = lastLog.getHouseOut() > IMPORT_THRESHOLD;
        boolean isExporting = lastLog.getHouseOut() < EXPORT_THRESHOLD;

        log.info("Checking for optimizations");

        if (!isCharging && isExporting) {
            log.info("Starting charge (excess power available)");
            chargePoint.startCharge();
        } else if (isCharging && isImporting) {
            log.info("Stopping charge (importing power)");
            chargePoint.stopCharge();
        } else {
            log.info("No action needed");
        }
    }
}
