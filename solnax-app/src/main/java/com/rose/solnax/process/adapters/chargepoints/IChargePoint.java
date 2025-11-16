package com.rose.solnax.process.adapters.chargepoints;

public interface IChargePoint {

    public Long getMinPower();

    public Long getMaxPower();

    public void startCharge();

    public void stopCharge();

    public boolean isChargeable();
}
