package com.rose.solnax.process.adapters.chargepoints;

import com.rose.solnax.process.adapters.chargepoints.tesla.TeslaBLEAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TeslaWallCharger implements IChargePoint {

    @Value("${tesla-ble.white")
    String whiteVin;

    @Value("${tesla-ble.black")
    String blackVin;

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
        return false;
    }

    private boolean isBlackChargeable(){
        //Call vehicle data, check if not already charging
        return false;
    }

    private boolean isWhiteChargeable(){
        return false;
    }

    private boolean isBlackCharging(){
        return false;
    }

    private boolean isWhiteCharging(){
        return false;
    }
}
