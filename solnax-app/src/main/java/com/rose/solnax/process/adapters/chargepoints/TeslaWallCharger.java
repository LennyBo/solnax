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
        return (long) (minAmps * defaultVoltage * defaultPhases);
    }

    @Override
    public Long getMaxPower() {
        return (long) (maxAmps * defaultVoltage * defaultPhases);
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
        if (data != null && data.isBatteryLow()) {
            log.info("Battery of {} is too low. Letting charge continue at min level", vinLabel(vinToStop));
            bleAdapter.setChargeState(minChargeLevel, vinToStop);
            return;
        }

        log.info("Stopping charge of {}!", vinLabel(vinToStop));
        bleAdapter.chargeStop(vinToStop);
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

        VehicleApiResponse data = getVehicleData(vin);
        int voltage = defaultVoltage;
        int phases = defaultPhases;

        if (data != null) {
            voltage = data.getChargerVoltage() > 0 ? data.getChargerVoltage() : defaultVoltage;
            phases = data.getChargerPhases() > 0 ? data.getChargerPhases() : defaultPhases;
        }

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
                chargePointCoolDownManager.coolDown(vin, CoolDownReason.NO_RESPONSE);
                return null;
            }

            if (response.isBatteryFull()) {
                chargePointCoolDownManager.coolDown(vin, CoolDownReason.FULL);
            } else if (!response.isConnected()) {
                chargePointCoolDownManager.coolDown(vin, CoolDownReason.NOT_CONNECTED);
            }

            return response;
        } catch (Exception e) {
            log.warn("Couldn't reach {} via BLE: {}", vinLabel(vin), e.getMessage());
            chargePointCoolDownManager.coolDown(vin, CoolDownReason.NO_RESPONSE);
            return null;
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
