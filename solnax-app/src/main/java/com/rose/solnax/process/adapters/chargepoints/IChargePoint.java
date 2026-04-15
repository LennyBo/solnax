package com.rose.solnax.process.adapters.chargepoints;

public interface IChargePoint {

    public Long getMinPower();

    public Long getMaxPower();

    public void startCharge();

    public void stopCharge();

    public boolean isChargeable();

    /**
     * Adjust the charging power (amps) based on available watts.
     * If availableWatts is below the minimum threshold, charging should stop.
     * @param availableWatts watts available for charging
     */
    public void adjustChargePower(int availableWatts);

    /**
     * @return VIN of the currently connected/charging car, or null
     */
    public String getConnectedVin();

    /**
     * @return true if a car is currently actively charging
     */
    public boolean isCurrentlyCharging();
}
