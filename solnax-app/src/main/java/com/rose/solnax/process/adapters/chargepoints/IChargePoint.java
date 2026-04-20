package com.rose.solnax.process.adapters.chargepoints;

public interface IChargePoint {

    Long getMinPower();

    Long getMaxPower();

    void startCharge();

    void stopCharge();

    boolean isChargeable();

    /**
     * Adjust the charging power (amps) based on available watts.
     * If availableWatts is below the minimum threshold, charging should stop.
     * @param availableWatts watts available for charging
     */
    void adjustChargePower(int availableWatts);

    /**
     * @return VIN of the currently connected/charging car, or null
     */
    String getConnectedVin();

    /**
     * @return true if a car is currently actively charging
     */
    boolean isCurrentlyCharging();

    /**
     * Detect if a car started charging on its own (auto or manual start)
     * by checking if the charger meter reports significant power draw
     * without an active charge session. No BLE calls needed.
     * @param chargerDraw current charger power draw in watts from the Shelly meter
     */
    void detectAutoCharging(int chargerDraw);

    /**
     * @return the battery level of the currently connected/charging car, or -1
     */
    int getBatteryLevel();

    /**
     * Detect if charging stopped on its own (e.g. car reached max charge limit).
     * If there are active sessions but charger meter shows no draw, end them
     * and set charge limit back to min to prevent auto-restart.
     * @param chargerDraw current charger power draw in watts from the Shelly meter
     */
    void detectChargeStopped(int chargerDraw);
}
