package com.rose.solnax.process.adapters.chargepoints;

import com.rose.solnax.model.entity.ChargePointCoolDown;
import com.rose.solnax.model.entity.enums.CoolDownReason;
import com.rose.solnax.process.ChargePointCoolDownManager;
import com.rose.solnax.process.ChargeSessionManager;
import com.rose.solnax.process.adapters.chargepoints.tesla.TeslaBLEAdapter;
import com.rose.solnax.process.adapters.chargepoints.tesla.model.VehicleApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class TeslaWallCharger implements IChargePoint {

    @Value("${tesla-ble.white}")
    private String whiteVin;

    @Value("${tesla-ble.black}")
    private String blackVin;

    @Value("${tesla-ble.max-charge-level}")
    private Integer maxChargeLevel;

    @Value("${tesla-ble.min-charge-level}")
    private Integer minChargeLevel;

    @Value("${tesla-ble.default-voltage:230}")
    private int defaultVoltage;

    @Value("${tesla-ble.default-phases:3}")
    private int defaultPhases;

    @Value("${tesla-ble.min-amps:5}")
    private int minAmps;

    @Value("${tesla-ble.max-amps:16}")
    private int maxAmps;

    private final ChargePointCoolDownManager chargePointCoolDownManager;
    private final ChargeSessionManager chargeSessionManager;
    private final TeslaBLEAdapter bleAdapter;

    /**
     * Cached state from the last check cycle — avoids redundant BLE wake-ups.
     */
    private String connectedCar = null;
    private VehicleApiResponse cachedBlackData = null;
    private VehicleApiResponse cachedWhiteData = null;

    public TeslaWallCharger(ChargePointCoolDownManager chargePointCoolDownManager,
                            ChargeSessionManager chargeSessionManager,
                            TeslaBLEAdapter bleAdapter) {
        this.chargePointCoolDownManager = chargePointCoolDownManager;
        this.chargeSessionManager = chargeSessionManager;
        this.bleAdapter = bleAdapter;
    }

    // ─── Power thresholds ───────────────────────────────────────────────

    @Override
    public Long getMinPower() {
        return ((long) minAmps * defaultVoltage * defaultPhases);
    }

    @Override
    public Long getMaxPower() {
        return ((long) maxAmps * defaultVoltage * defaultPhases);
    }

    // ─── Core charge actions ────────────────────────────────────────────

    @Override
    public void startCharge() {
        if (!isChargeable()) {
            log.info("No car connected to charge");
            return;
        }

        if (connectedCar != null) {
            VehicleApiResponse data = getVehicleData(connectedCar);
            log.info("{} is ready to charge -> Starting!", vinLabel(connectedCar));
            chargePointCoolDownManager.clearCoolDownsByReasonAndTarget(connectedCar, CoolDownReason.NOT_CONNECTED);
            bleAdapter.setChargeState(maxChargeLevel, connectedCar);
            bleAdapter.chargeStart(connectedCar);
            chargeSessionManager.startSession(connectedCar, data);
        }
    }

    @Override
    public void stopCharge() {
        List<ChargePointCoolDown> activeCoolDowns = chargePointCoolDownManager.getActiveCoolDowns();
        boolean isBlackCoolDown = activeCoolDowns.stream().anyMatch(c -> blackVin.equals(c.getTarget()));
        boolean isWhiteCoolDown = activeCoolDowns.stream().anyMatch(c -> whiteVin.equals(c.getTarget()));

        if (isBlackCoolDown && isWhiteCoolDown) {
            log.info("Both cars in cool down period");
            return;
        }

        String vinToStop = findChargingVin(isBlackCoolDown, isWhiteCoolDown);
        if (vinToStop == null) {
            log.info("No car currently charging to stop");
            return;
        }

        VehicleApiResponse data = getVehicleData(vinToStop);

        log.info("Stopping charge of {}!", vinLabel(vinToStop));
        bleAdapter.chargeStop(vinToStop);
        // Set charge limit to 60% so the car won't auto-start when plugged in
        bleAdapter.setChargeState(minChargeLevel, vinToStop);
        chargeSessionManager.endSession(vinToStop, data);
    }

    // ─── Chargeable / Connected checks ──────────────────────────────────

    @Override
    public boolean isChargeable() {
        List<ChargePointCoolDown> activeCoolDowns = chargePointCoolDownManager.getActiveCoolDowns();
        boolean isBlackCoolDown = activeCoolDowns.stream().anyMatch(c -> blackVin.equals(c.getTarget()));
        boolean isWhiteCoolDown = activeCoolDowns.stream().anyMatch(c -> whiteVin.equals(c.getTarget()));

        if (isBlackCoolDown && isWhiteCoolDown) {
            log.info("Both cars in cool down period");
            connectedCar = null;
            return false;
        }

        log.info("Checking if a car is ready to charge");

        if (!isBlackCoolDown) {
            VehicleApiResponse blackData = fetchAndEvaluate(blackVin);
            if (blackData != null && blackData.canCharge()) {
                log.info("Black is connected and chargeable");
                connectedCar = blackVin;
                cachedBlackData = blackData;
                return true;
            }
        }

        if (!isWhiteCoolDown) {
            VehicleApiResponse whiteData = fetchAndEvaluate(whiteVin);
            if (whiteData != null && whiteData.canCharge()) {
                log.info("White is connected and chargeable");
                connectedCar = whiteVin;
                cachedWhiteData = whiteData;
                return true;
            }
        }

        log.info("No car connected");
        connectedCar = null;
        return false;
    }

    @Override
    public boolean isCurrentlyCharging() {
        List<ChargePointCoolDown> activeCoolDowns = chargePointCoolDownManager.getActiveCoolDowns();
        boolean isBlackCoolDown = activeCoolDowns.stream().anyMatch(c -> blackVin.equals(c.getTarget()));
        boolean isWhiteCoolDown = activeCoolDowns.stream().anyMatch(c -> whiteVin.equals(c.getTarget()));
        return findChargingVin(isBlackCoolDown, isWhiteCoolDown) != null;
    }

    @Override
    public String getConnectedVin() {
        return connectedCar;
    }

    @Override
    public void detectAutoCharging(int chargerDraw) {
        // If the charger meter shows significant draw but there's no active session,
        // a car must have started charging on its own (manual plug-in or auto-start).
        // No BLE calls needed — we just use the Shelly power reading.
        long minDetectionWatts = getMinPower();
        if (chargerDraw < minDetectionWatts) {
            return; // No significant draw — nothing to detect
        }

        List<com.rose.solnax.model.entity.ChargeSession> activeSessions = chargeSessionManager.getActiveSessions();
        if (!activeSessions.isEmpty()) {
            return; // Already tracking a session
        }

        // Something is charging without a session — figure out which car
        // We don't know which car it is without BLE, so just start a session with a placeholder
        // and let the next isChargeable/isCurrentlyCharging call (which already uses BLE) resolve it.
        log.info("Charger drawing {}W with no active session — a car started charging on its own", chargerDraw);

        // Clear NOT_CONNECTED cooldowns for both cars since one of them is clearly connected
        chargePointCoolDownManager.clearCoolDownsByReasonAndTarget(blackVin, CoolDownReason.NOT_CONNECTED);
        chargePointCoolDownManager.clearCoolDownsByReasonAndTarget(whiteVin, CoolDownReason.NOT_CONNECTED);
    }

    @Override
    public int getBatteryLevel() {
        // Only use cached data — don't wake the car just to check battery level
        if (connectedCar == null) return -1;
        VehicleApiResponse cached = blackVin.equals(connectedCar) ? cachedBlackData : cachedWhiteData;
        if (cached == null) return -1;
        return cached.getBatteryLevel();
    }

    @Override
    public void detectChargeStopped(int chargerDraw) {
        // If there are active sessions but the charger meter shows no significant draw,
        // the car stopped charging on its own (reached charge limit, error, etc.)
        // No BLE calls needed — just use the Shelly power reading.
        List<com.rose.solnax.model.entity.ChargeSession> activeSessions = chargeSessionManager.getActiveSessions();
        if (activeSessions.isEmpty()) {
            return; // No active sessions — nothing to detect
        }

        // Allow some tolerance — small draw could be idle/standby
        if (chargerDraw > 500) {
            return; // Still drawing power — charge is ongoing
        }

        log.info("Charger draw is {}W but there are {} active session(s) — charge stopped on its own", chargerDraw, activeSessions.size());

        for (var session : activeSessions) {
            String vin = session.getVin();
            // End the session — pass null for vehicleData to avoid BLE wake-up
            chargeSessionManager.endSession(vin, null);

            // Set charge limit back to 60% to prevent auto-restart
            try {
                bleAdapter.setChargeState(minChargeLevel, vin);
                log.info("Set charge limit to {}% for {} to prevent auto-restart", minChargeLevel, vinLabel(vin));
            } catch (Exception e) {
                log.warn("Failed to set charge limit for {}: {}", vinLabel(vin), e.getMessage());
            }
        }

        connectedCar = null;
    }

    // ─── Amp management ─────────────────────────────────────────────────

    @Override
    public void adjustChargePower(int availableWatts) {
        String vin = connectedCar;
        if (vin == null) {
            List<ChargePointCoolDown> activeCoolDowns = chargePointCoolDownManager.getActiveCoolDowns();
            boolean isBlackCoolDown = activeCoolDowns.stream().anyMatch(c -> blackVin.equals(c.getTarget()));
            boolean isWhiteCoolDown = activeCoolDowns.stream().anyMatch(c -> whiteVin.equals(c.getTarget()));
            vin = findChargingVin(isBlackCoolDown, isWhiteCoolDown);
        }

        if (vin == null) {
            log.info("No connected car to adjust amps for");
            return;
        }

        //VehicleApiResponse data = getVehicleData(vin);
        int voltage = defaultVoltage;
        int phases = defaultPhases;

        //if (data != null) {
            //voltage = data.getChargerVoltage() > 0 ? data.getChargerVoltage() : defaultVoltage;
            //phases = data.getChargerPhases() > 0 ? data.getChargerPhases() : defaultPhases;
        //}

        int wattsPerAmp = voltage * phases;
        int targetAmps = availableWatts / wattsPerAmp;

        targetAmps = Math.max(minAmps, Math.min(maxAmps, targetAmps));

        if (availableWatts < (long) minAmps * wattsPerAmp) {
            log.info("Available power {}W is below minimum {}W — stopping charge",
                    availableWatts, minAmps * wattsPerAmp);
            stopCharge();
            return;
        }

        log.info("Adjusting charge amps to {} (available: {}W, {}V x {} phases = {}W/A)",
                targetAmps, availableWatts, voltage, phases, wattsPerAmp);

        try {
            bleAdapter.setChargingAmps(targetAmps, vin);
            chargeSessionManager.updateSessionAmps(vin, targetAmps);
        } catch (Exception e) {
            log.warn("Failed to set charging amps for {}: {}", vin, e.getMessage());
        }
    }

    // ─── Private helpers ────────────────────────────────────────────────

    private VehicleApiResponse fetchAndEvaluate(String vin) {
        try {
            VehicleApiResponse response = bleAdapter.vehicle_data(vin);
            if (response == null) {
                //chargePointCoolDownManager.coolDown(vin, CoolDownReason.NO_RESPONSE);
                return null;
            }

            if (response.isBatteryFull()) {
                chargePointCoolDownManager.coolDown(vin, CoolDownReason.FULL);
            } else if (!response.isConnected()) {
                prepareDisconnectedVehicleForNextCharge(vin);
                chargePointCoolDownManager.coolDown(vin, CoolDownReason.NOT_CONNECTED);
            } else if(response.isBatteryLow() && response.isActivelyCharging()) {
                chargePointCoolDownManager.coolDown(vin, CoolDownReason.LOW_BATTERY);
            }

            return response;
        } catch (Exception e) {
            log.warn("Couldn't reach {} via BLE: {}", vinLabel(vin), e.getMessage());
            //chargePointCoolDownManager.coolDown(vin, CoolDownReason.NO_RESPONSE);
            return null;
        }
    }

    private void prepareDisconnectedVehicleForNextCharge(String vin) {
        if (chargePointCoolDownManager.hasActiveCoolDownForTarget(vin)) {
            return;
        }

        try {
            bleAdapter.setChargeState(maxChargeLevel, vin);
            log.info("{} is not connected — set charge limit to {}% for the next plug-in", vinLabel(vin), maxChargeLevel);
        } catch (Exception e) {
            log.warn("Failed to set charge limit for disconnected {}: {}", vinLabel(vin), e.getMessage());
        }
    }

    private VehicleApiResponse getVehicleData(String vin) {
        if (blackVin.equals(vin) && cachedBlackData != null) return cachedBlackData;
        if (whiteVin.equals(vin) && cachedWhiteData != null) return cachedWhiteData;

        try {
            VehicleApiResponse data = bleAdapter.vehicle_data(vin);
            if (blackVin.equals(vin)) cachedBlackData = data;
            if (whiteVin.equals(vin)) cachedWhiteData = data;
            return data;
        } catch (Exception e) {
            log.warn("Couldn't get vehicle data for {}: {}", vinLabel(vin), e.getMessage());
            return null;
        }
    }

    private String findChargingVin(boolean isBlackCoolDown, boolean isWhiteCoolDown) {
        if (!isBlackCoolDown) {
            try {
                VehicleApiResponse blackData = getVehicleData(blackVin);
                if (blackData != null && blackData.isActivelyCharging()) {
                    connectedCar = blackVin;
                    // Ensure a session exists (handles auto/manual start detection)
                    chargeSessionManager.startSession(blackVin, blackData);
                    return blackVin;
                }
            } catch (Exception e) {
                log.warn("Couldn't check if black is charging: {}", e.getMessage());
            }
        }

        if (!isWhiteCoolDown) {
            try {
                VehicleApiResponse whiteData = getVehicleData(whiteVin);
                if (whiteData != null && whiteData.isActivelyCharging()) {
                    connectedCar = whiteVin;
                    // Ensure a session exists (handles auto/manual start detection)
                    chargeSessionManager.startSession(whiteVin, whiteData);
                    return whiteVin;
                }
            } catch (Exception e) {
                log.warn("Couldn't check if white is charging: {}", e.getMessage());
            }
        }

        return null;
    }

    /**
     * Clear the per-cycle cache. Called at the start of each optimization cycle.
     */
    public void clearCycleCache() {
        cachedBlackData = null;
        cachedWhiteData = null;
    }

    private String vinLabel(String vin) {
        if (blackVin.equals(vin)) return "Black";
        if (whiteVin.equals(vin)) return "White";
        return vin;
    }
}
