package com.rose.solnax.process.adapters.chargepoints;

import com.rose.solnax.model.entity.ChargePointCoolDown;
import com.rose.solnax.model.entity.enums.CoolDownReason;
import com.rose.solnax.process.ChargePointCoolDownManager;
import com.rose.solnax.process.adapters.chargepoints.tesla.TeslaBLEAdapter;
import com.rose.solnax.process.adapters.chargepoints.tesla.model.VehicleApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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


    private final ChargePointCoolDownManager chargePointCoolDownManager;
    String connectedCar = null;
    LocalDateTime lastCheckTime = null;

    private final TeslaBLEAdapter bleAdapter;

    public TeslaWallCharger(ChargePointCoolDownManager chargePointCoolDownManager1, TeslaBLEAdapter bleAdapter) {
        this.chargePointCoolDownManager = chargePointCoolDownManager1;
        this.bleAdapter = bleAdapter;
    }


    @Override
    public Long getMinPower() {
        return 4320L;
    }

    @Override
    public Long getMaxPower() {
        return 11520L;
    }

    @Override
    public void startCharge() {
        if (!isChargeable()) {
            log.info("No car connected to charge");
            return;
        }
        if (isBlackChargeable()) {
            log.info("Black is ready to charge -> Starting!");
            bleAdapter.setChargeState(maxChargeLevel, blackVin);
            bleAdapter.chargeStart(blackVin);
        } else if (isWhiteChargeable()) {
            log.info("White is ready to charge -> Starting!");
            bleAdapter.setChargeState(maxChargeLevel, whiteVin);
            bleAdapter.chargeStart(whiteVin);
        }
    }

    @Override
    public void stopCharge() {
        List<ChargePointCoolDown> activeCoolDowns = chargePointCoolDownManager.getActiveCoolDowns();
        boolean isBlackCoolDown = activeCoolDowns.stream().anyMatch(c -> blackVin.equals(c.getTarget()));
        boolean isWhiteCoolDown = activeCoolDowns.stream().anyMatch(c -> whiteVin.equals(c.getTarget()));
        if(isBlackCoolDown && isWhiteCoolDown){
            log.info("Both cars in cool down period");
            return;
        }
        if (isBlackCharging()) {
            if(isBlackLow()){
                log.info("Battery of black is too low. Letting charge continue");
                bleAdapter.setChargeState(minChargeLevel,blackVin);
                return;
            }
            log.info("Stopping charge of Black!");
            bleAdapter.chargeStart(blackVin);
            bleAdapter.setChargeState(minChargeLevel, blackVin);
        } else if (isWhiteCharging()) {
            if(isWhiteLow()){
                log.info("Battery of black is too low. Letting charge continue");
                bleAdapter.setChargeState(minChargeLevel,whiteVin);
                return;
            }
            log.info("Stopping charge of white!");
            bleAdapter.chargeStop(whiteVin);
            bleAdapter.setChargeState(minChargeLevel, whiteVin);
        }
    }



    @Override
    public boolean isChargeable() {
        List<ChargePointCoolDown> activeCoolDowns = chargePointCoolDownManager.getActiveCoolDowns();
        boolean isBlackCoolDown = activeCoolDowns.stream().anyMatch(c -> blackVin.equals(c.getTarget()));
        boolean isWhiteCoolDown = activeCoolDowns.stream().anyMatch(c -> whiteVin.equals(c.getTarget()));
        if(isBlackCoolDown && isWhiteCoolDown){
            log.info("Both cars in cool down period");
            return false;
        }
        log.info("Checking if a car is ready to charge");
        lastCheckTime = LocalDateTime.now();
        if (!isBlackCoolDown && isBlackChargeable()) {
            log.info("Black is connected");
            connectedCar = blackVin;
            return true;
        } else if (!isWhiteCoolDown) {
            boolean whiteChargeable = isWhiteChargeable();
            if (whiteChargeable) {
                connectedCar = whiteVin;
                log.info("White is connected");
            } else {
                log.info("No car connected");
                connectedCar = null;
            }
            return whiteChargeable;
        }
        return false;
    }

    private boolean isBlackChargeable() {
        return isChargeable(blackVin);
    }

    private boolean isWhiteChargeable() {
        return isChargeable(whiteVin);
    }

    private boolean isBlackCharging() {
        return isCharging(blackVin);
    }

    private boolean isWhiteCharging() {
        return isCharging(whiteVin);
    }

    private boolean isBlackLow() {
        return isLowBattery(blackVin);
    }

    private boolean isWhiteLow() {
        return isLowBattery(whiteVin);
    }

    private boolean isChargeable(String vin) {
        try {
            VehicleApiResponse response = bleAdapter.vehicle_data(vin);

            if (response.isBatteryFull()) {
                chargePointCoolDownManager.coolDown(vin, CoolDownReason.FULL);
            } else if (!response.isConnected()) {
                chargePointCoolDownManager.coolDown(vin, CoolDownReason.NOT_CONNECTED);
            }

            return response.canCharge();

        } catch (Exception e) {
            log.warn("Couldn't check if {} is chargeable", vin);
            return false;
        }
    }

    private boolean isCharging(String vin) {
        try {
            VehicleApiResponse response = bleAdapter.vehicle_data(vin);
            if (!response.isConnected()) {
                chargePointCoolDownManager.coolDown(vin, CoolDownReason.NOT_CONNECTED);
            } else if (response.isBatteryLow()) {
                chargePointCoolDownManager.coolDown(vin, CoolDownReason.LOW_BATTERY);
            }
            return response.isActivelyCharging();
        } catch (Exception e) {
            log.warn("Couldn't check if {} is charging", vin);
            return false;
        }
    }

    private boolean isLowBattery(String vin) {
        try {
            VehicleApiResponse response = bleAdapter.vehicle_data(vin);
            return response.isBatteryLow();
        } catch (Exception e) {
            log.warn("Couldn't check if {} is low battery", vin);
            return false;
        }
    }

}
