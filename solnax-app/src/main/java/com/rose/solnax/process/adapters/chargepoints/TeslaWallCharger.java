package com.rose.solnax.process.adapters.chargepoints;

import com.rose.solnax.model.entity.ChargePointCoolDown;
import com.rose.solnax.model.entity.enums.CoolDownReason;
import com.rose.solnax.process.ChargePointCoolDownManager;
import com.rose.solnax.process.adapters.chargepoints.tesla.TeslaBLEAdapter;
import com.rose.solnax.process.adapters.chargepoints.tesla.model.VehicleApiResponse;
import jakarta.transaction.Transactional;
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
        if (isBlackCharging()) {
            log.info("Stopping charge of Black!");
            bleAdapter.chargeStart(blackVin);
            bleAdapter.setChargeState(minChargeLevel, blackVin);
        } else if (isWhiteCharging()) {
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
        } else {
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
    }

    private boolean isBlackChargeable() {
        return isChargeable(blackVin, blackVin);
    }

    private boolean isWhiteChargeable() {
        return isChargeable(whiteVin, whiteVin);
    }

    private boolean isBlackCharging() {
        return isCharging(blackVin, blackVin);
    }

    private boolean isWhiteCharging() {
        return isCharging(whiteVin, whiteVin);
    }


    private boolean isChargeable(String vin, String label) {
        try {
            VehicleApiResponse response = bleAdapter.vehicle_data(vin);

            if (!response.isConnected()) {
                chargePointCoolDownManager.coolDown(vin, CoolDownReason.NOT_CONNECTED);
            } else if (response.isBatteryFull()) {
                chargePointCoolDownManager.coolDown(vin, CoolDownReason.FULL);
            }

            return response.canCharge();

        } catch (Exception e) {
            log.warn("Couldn't check if {} is chargeable", label);
            return false;
        }
    }

    private boolean isCharging(String vin, String label) {
        try {
            VehicleApiResponse response = bleAdapter.vehicle_data(vin);
            return response.isActivelyCharging();
        } catch (Exception e) {
            log.warn("Couldn't check if {} is charging", label);
            return false;
        }
    }

}
