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
     *
     * Battery level strategy:
     *   - Below 60%: let the car charge freely (don't interfere / don't stop)
     *   - 60-80%: solar optimization — adjust amps to match surplus, stop if not enough
     *   - At/above 80% (or charge complete): stop and set limit to 60% to prevent auto-restart
     *
     * Auto-charge detection:
     *   - If a car started charging on its own (manual plug-in or auto-start),
     *     detect it, start a session, and clear NOT_CONNECTED cooldowns.
     *
     * Formula: availablePower = chargerCurrentDraw - gridExchange - buffer
     *   (grid is positive when importing, negative when exporting)
     */
    @Scheduled(cron = "0 1/5 5-22 * * *")
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

        int currentChargerDraw = lastLog.getCharger();
        int gridExchange = lastLog.getHouse();
        int availablePower = currentChargerDraw - gridExchange - powerBuffer;

        // Detect cars that started charging on their own — uses charger meter, no BLE
        chargePoint.detectAutoCharging(currentChargerDraw);

        // Detect if charging stopped on its own (e.g. reached max charge %) — uses charger meter, no BLE
        chargePoint.detectChargeStopped(currentChargerDraw);

        boolean isCharging = chargePoint.isCurrentlyCharging();
        long minPower = chargePoint.getMinPower();
        int batteryLevel = chargePoint.getBatteryLevel();

        log.info("Optimization check: grid={}W, charger={}W, available={}W, minPower={}W, isCharging={}, battery={}%",
                gridExchange, currentChargerDraw, availablePower, minPower, isCharging, batteryLevel);

        // ── Battery below 60%: let it charge freely, don't stop ──
        if (isCharging && batteryLevel >= 0 && batteryLevel < 60) {
            log.info("Battery at {}% (< 60%) — letting charge continue freely", batteryLevel);
            return;
        }

        // ── Battery 60-80%: solar optimization mode ──
        if (!isCharging && availablePower >= minPower) {
            log.info("Starting charge with {}W available (surplus)", availablePower);
            chargePoint.startCharge();
            chargePoint.adjustChargePower(availablePower);
        } else if (isCharging && availablePower >= minPower) {
            log.info("Adjusting charge power to {}W", availablePower);
            chargePoint.adjustChargePower(availablePower);
        } else if (isCharging && availablePower < minPower - 1300) {
            log.info("Insufficient surplus ({}W < {}W) — stopping charge", availablePower, minPower);
            chargePoint.stopCharge();
        } else {
            log.info("No action needed (not charging, surplus={}W)", availablePower);
        }
    }
}
