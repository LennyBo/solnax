package com.rose.solnax.process;

import com.rose.solnax.model.entity.PowerLog;
import com.rose.solnax.process.adapters.chargepoints.IChargePoint;
import com.rose.solnax.process.adapters.chargepoints.TeslaWallCharger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobManager {

    private final PowerLogManager powerLogManager;
    private final IChargePoint chargePoint;

    @Value("${tesla-ble.power-buffer:500}")
    private int powerBuffer;

    @Scheduled(cron = "0 */5 * * * *")
    void logPower() {
        PowerLog powerLog = powerLogManager.logPower();
        log.info("Logged power log: {}", powerLog);
    }

    /**
     * Smart solar-tracking charge optimizer.
     *
     * Runs every 5 minutes during daylight hours.
     * Calculates available solar surplus and adjusts charging amps accordingly:
     *   - If surplus > min charge power and not charging → start + set amps
     *   - If already charging → adjust amps (may stop if surplus too low)
     *   - If surplus is negative → stop charging
     *
     * Formula: availablePower = chargerCurrentDraw - gridExchange - buffer
     *   (grid is positive when importing, negative when exporting)
     */
    @Scheduled(cron = "0 1/5 6-22 * * *")
    @Transactional
    void optimizePower() {
        // Clear per-cycle vehicle data cache to get fresh readings
        if (chargePoint instanceof TeslaWallCharger wallCharger) {
            wallCharger.clearCycleCache();
        }

        PowerLog lastLog = powerLogManager.getLastPowerLog();
        if (lastLog == null) {
            log.error("No recent log found. Aborting optimization!");
            return;
        }

        // Available power for the charger:
        // charger = current charger draw (watts, positive when drawing)
        // house = grid meter (positive = importing, negative = exporting)
        // availablePower = what the charger could draw without importing from grid
        int currentChargerDraw = lastLog.getCharger();
        int gridExchange = lastLog.getHouse();
        int availablePower = currentChargerDraw - gridExchange - powerBuffer;

        boolean isCharging = chargePoint.isCurrentlyCharging();
        long minPower = chargePoint.getMinPower();

        log.info("Optimization check: grid={}W, charger={}W, available={}W, minPower={}W, isCharging={}",
                gridExchange, currentChargerDraw, availablePower, minPower, isCharging);

        if (!isCharging && availablePower >= minPower) {
            // Enough surplus to start charging
            log.info("Starting charge with {}W available (surplus)", availablePower);
            chargePoint.startCharge();
            chargePoint.adjustChargePower(availablePower);
        } else if (isCharging && availablePower >= minPower) {
            // Already charging — adjust amps to match current surplus
            log.info("Adjusting charge power to {}W", availablePower);
            chargePoint.adjustChargePower(availablePower);
        } else if (isCharging && availablePower < minPower) {
            // Not enough surplus — stop charging
            log.info("Insufficient surplus ({}W < {}W) — stopping charge", availablePower, minPower);
            chargePoint.stopCharge();
        } else {
            log.info("No action needed (not charging, surplus={}W)", availablePower);
        }
    }
}
