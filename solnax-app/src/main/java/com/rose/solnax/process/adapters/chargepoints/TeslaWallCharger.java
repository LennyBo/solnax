package com.rose.solnax.process.adapters.chargepoints;

import com.rose.solnax.process.adapters.chargepoints.tesla.TeslaBLEAdapter;
import com.rose.solnax.process.adapters.chargepoints.tesla.model.VehicleApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

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


    String connectedCar = null;
    LocalDateTime lastCheckTime = null;

    private final TeslaBLEAdapter bleAdapter;

    public TeslaWallCharger(TeslaBLEAdapter bleAdapter) {
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
        log.info("Checking if a car is ready to charge");
        lastCheckTime = LocalDateTime.now();
        if (isBlackChargeable()) {
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

    @Bean
    public Object logVins() {
        log.info("Black: {} White: {}", blackVin, whiteVin);
        return null;
    }

    private boolean isBlackChargeable() {
        try {
            VehicleApiResponse teslaProxyResponse = bleAdapter.vehicle_data(blackVin);
            return teslaProxyResponse.canCharge();
        } catch (Exception e) {
            log.warn("Couldn't check if black is chargeable");
            return false;
        }
    }

    private boolean isWhiteChargeable() {
        try {
            VehicleApiResponse teslaProxyResponse = bleAdapter.vehicle_data(whiteVin);
            return teslaProxyResponse.canCharge();
        } catch (Exception e) {
            log.warn("Couldn't check if white is chargeable");
            return false;
        }
    }

    private boolean isBlackCharging() {
        try {
            VehicleApiResponse teslaProxyResponse = bleAdapter.vehicle_data(blackVin);
            return teslaProxyResponse.isActivelyCharging();
        } catch (Exception e) {
            log.warn("Couldn't check if black is charging");
            return false;
        }
    }

    private boolean isWhiteCharging() {
        try {
            VehicleApiResponse teslaProxyResponse = bleAdapter.vehicle_data(whiteVin);
            return teslaProxyResponse.isActivelyCharging();
        } catch (Exception e) {
            log.warn("Couldn't check if white is charging");
            return false;
        }
    }
}
