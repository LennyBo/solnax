package com.rose.solnax.process;

import com.rose.solnax.model.entity.PowerLog;
import com.rose.solnax.model.entity.enums.ChargeControlMode;
import com.rose.solnax.process.adapters.chargepoints.IChargePoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChargeOptimizer {

    /**
     * Power draw above which the charger is considered to be actively charging.
     * Shared with {@link IChargePoint} so detection is consistent everywhere:
     * a car running at the lowest amps is still recognized as charging.
     */
    static final int CHARGING_DETECTION_WATTS = IChargePoint.CHARGING_DETECTION_WATTS;

    /**
     * How much grid import is tolerated before an ongoing charge is considered unsustainable.
     */
    static final int SURPLUS_TOLERANCE_WATTS = 1300;

    private final IChargePoint chargePoint;
    private final ChargePointCoolDownManager coolDownManager;

    /**
     * Mode of the previous cycle — used to detect the wake-up after a manual sleep.
     * The scheduler is single threaded, so plain field state is enough here.
     */
    private ChargeControlMode previousMode = ChargeControlMode.NORMAL;

    /**
     * Smart solar-tracking charge optimizer.
     * <p>
     * Runs immediately after power is logged — no delay needed.
     * <p>
     * Modes:
     * - MANUAL: the optimizer is asleep. It does not read, start, stop or adjust the car at all.
     * - ECO_PLUS: solar tracking stays active, but an ongoing charge is never stopped —
     * it falls back to the lowest charge speed when the surplus is gone.
     * ECO_PLUS still only starts a charge when there is enough surplus.
     * - NORMAL: full solar optimization, charging stops when the surplus is gone.
     * <p>
     * Auto-charge detection:
     * - If a car started charging on its own (manual plug-in or auto-start),
     * detect it, start a session, and clear NOT_CONNECTED cooldowns.
     * <p>
     * Formula: availablePower = chargerCurrentDraw - gridExchange
     * (grid is positive when importing, negative when exporting)
     */
    @Transactional
    public void optimize(PowerLog lastLog) {
        ChargeControlMode mode = coolDownManager.getActiveChargeControlMode();
        if (mode == null) {
            mode = ChargeControlMode.NORMAL;
        }

        // ── MANUAL: full sleep — no detection, no BLE, no charge decisions ──
        if (mode == ChargeControlMode.MANUAL) {
            previousMode = mode;
            log.info("Manual cool down is active — charge control is asleep (no start, no stop, no amp changes)");
            return;
        }

        if (previousMode == ChargeControlMode.MANUAL) {
            // Cars may have been swapped or unplugged while we were asleep —
            // forget every assumption and re-resolve from scratch.
            log.info("Manual cool down ended — resetting cached vehicle state before resuming");
            chargePoint.resetCachedState();
        } else {
            // Clear per-cycle vehicle data cache to get fresh readings
            chargePoint.clearCycleCache();
        }
        previousMode = mode;

        Integer currentChargerDraw = lastLog.getCharger();
        Integer gridExchange = lastLog.getHouse();
        if (currentChargerDraw == null || gridExchange == null) {
            log.warn("Skipping optimization because required power readings are missing (charger={}, house={})",
                    currentChargerDraw, gridExchange);
            return;
        }

        int availablePower = currentChargerDraw - gridExchange;

        // Detect cars that started charging on their own — uses charger meter, no BLE
        chargePoint.detectAutoCharging(currentChargerDraw);

        // Detect if charging stopped on its own (e.g. reached max charge %) — uses charger meter, no BLE
        chargePoint.detectChargeStopped(currentChargerDraw);

        boolean isCharging = currentChargerDraw > CHARGING_DETECTION_WATTS;
        long minPower = chargePoint.getMinPower();
        int batteryLevel = chargePoint.getBatteryLevel(false);

        log.info("Optimization check ({}): grid={}W, charger={}W, available={}W, minPower={}W, isCharging={}, battery={}%",
                mode, gridExchange, currentChargerDraw, availablePower, minPower, isCharging, batteryLevel);

        if (!isCharging && availablePower >= minPower) {
            log.info("Starting charge with {}W available (surplus)", availablePower);
            chargePoint.startCharge();
            chargePoint.adjustChargePower(availablePower);
        } else if (isCharging && availablePower >= minPower - SURPLUS_TOLERANCE_WATTS) {
            log.info("Adjusting charge power to {}W", availablePower);
            chargePoint.adjustChargePower(availablePower);
        } else if (isCharging) {
            if (mode == ChargeControlMode.ECO_PLUS) {
                log.info("Eco+ active — insufficient surplus ({}W < {}W) but keeping the charge alive at minimum speed",
                        availablePower, minPower);
                chargePoint.maintainMinimumCharge();
            } else {
                log.info("Insufficient surplus ({}W < {}W) — delegating charge-point handling", availablePower, minPower);
                chargePoint.handleInsufficientSurplus();
            }
        } else {
            log.info("No action needed (not charging, surplus={}W)", availablePower);
        }
    }
}
