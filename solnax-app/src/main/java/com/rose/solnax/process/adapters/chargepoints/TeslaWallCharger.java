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
    String whiteVin;

    @Value("${tesla-ble.black}")
    String blackVin;

    String connectedCar = null;
    LocalDateTime lastCheckTime = null;

    private final TeslaBLEAdapter bleAdapter;

    public TeslaWallCharger(TeslaBLEAdapter bleAdapter){
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
        if(!isChargeable()){
            log.info("No car connected to charge");
            return;
        }
        if(isWhiteChargeable()){
            log.info("White is ready to charge -> Starting!");
            bleAdapter.chargeStart(whiteVin);
        }else if(isBlackChargeable()){
            log.info("Black is ready to charge -> Starting!");
            bleAdapter.chargeStart(blackVin);
        }
    }

    @Override
    public void stopCharge() {
        if(isWhiteCharging()){
            log.info("Stopping charge of white!");
            bleAdapter.chargeStop(whiteVin);
        }else if(isBlackCharging()){
            log.info("Stopping charge of Black!");
            bleAdapter.chargeStart(blackVin);
        }
    }

    @Override
    public boolean isChargeable() {
        log.info("Checking if a car is ready to charge");
        lastCheckTime = LocalDateTime.now();
        if(isBlackChargeable()){
            log.info("Black is connected");
            connectedCar = blackVin;
            return true;
        }else{
            boolean whiteChargeable = isWhiteChargeable();
            if(whiteChargeable){
                connectedCar = whiteVin;
                log.info("White is connected");
            }else{
                log.info("No car connected");
                connectedCar = null;
            }
            return whiteChargeable;
        }
    }

    @Bean
    public Object logVins(){
        log.info("Black: {} White: {}",blackVin,whiteVin);
        return null;
    }

    private boolean isBlackChargeable(){
        VehicleApiResponse teslaProxyResponse = bleAdapter.vehicle_data(blackVin);
        return teslaProxyResponse.canCharge();
    }

    private boolean isWhiteChargeable(){
        VehicleApiResponse teslaProxyResponse = bleAdapter.vehicle_data(whiteVin);
        return teslaProxyResponse.canCharge();
    }

    private boolean isBlackCharging(){
        return false;
    }

    private boolean isWhiteCharging(){
        return false;
    }
}
