package com.rose.solnax.process;

import com.rose.solnax.model.entity.PowerLog;
import com.rose.solnax.process.adapters.chargepoints.IChargePoint;
import com.rose.solnax.process.adapters.chargepoints.TeslaWallCharger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChargeOptimizer {

    private final IChargePoint chargePoint;

    /**
     * Smart solar-tracking charge optimizer.
     *
     * Runs immediately after power is logged — no delay needed.
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
    @Transactional
    public void optimize(PowerLog lastLog) {
        // Clear per-cycle vehicle data cache to get fresh readings
        if (chargePoint instanceof TeslaWallCharger wallCharger) {
            wallCharger.clearCycleCache();
        }

        int currentChargerDraw = lastLog.getCharger();
        int gridExchange = lastLog.getHouse();
        int availablePower = currentChargerDraw - gridExchange;

        // Detect cars that started charging on their own — uses charger meter, no BLE
        chargePoint.detectAutoCharging(currentChargerDraw);

        // Detect if charging stopped on its own (e.g. reached max charge %) — uses charger meter, no BLE
        chargePoint.detectChargeStopped(currentChargerDraw);

        boolean isCharging = currentChargerDraw > chargePoint.getMinPower();
        long minPower = chargePoint.getMinPower();
        int batteryLevel = chargePoint.getBatteryLevel();

        log.info("Optimization check: grid={}W, charger={}W, available={}W, minPower={}W, isCharging={}, battery={}%",
                gridExchange, currentChargerDraw, availablePower, minPower, isCharging, batteryLevel);

        // ── Battery 60-80%: solar optimization mode ──
        if (!isCharging && availablePower >= minPower - 1300) {
            log.info("Starting charge with {}W available (surplus)", availablePower);
            chargePoint.startCharge();
            chargePoint.adjustChargePower(availablePower);
        } else if (isCharging && availablePower >= minPower - 1300) {
            log.info("Adjusting charge power to {}W", availablePower);
            chargePoint.adjustChargePower(availablePower);
        } else if (isCharging && availablePower < minPower - 1300 && !(batteryLevel >= 0 && batteryLevel < 60)) { //Allow to keep charging if low battery
            log.info("Insufficient surplus ({}W < {}W) — stopping charge", availablePower, minPower);
            chargePoint.stopCharge();
        } else {
            log.info("No action needed (not charging, surplus={}W)", availablePower);
        }
    }
}

