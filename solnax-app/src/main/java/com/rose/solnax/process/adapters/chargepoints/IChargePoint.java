package com.rose.solnax.process.adapters.chargepoints;

public interface IChargePoint {

    /**
     * Power draw above which the charger is considered to be actively charging.
     * Below it the charger is idle / in standby.
     */
    int CHARGING_DETECTION_WATTS = 500;

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
    int getBatteryLevel(boolean wakeUp);

    /**
     * Handle the case where a car is charging but there is no longer enough
     * surplus power available. Implementations may decide to stop charging,
     * keep charging for low-battery protection, or update charge limits.
     */
    void handleInsufficientSurplus();

    /**
     * Eco+ variant of {@link #handleInsufficientSurplus()}: the surplus is gone but the
     * ongoing charge must never be stopped. Implementations keep the car charging at the
     * lowest supported charge speed instead.
     */
    void maintainMinimumCharge();

    /**
     * Detect if charging stopped on its own (e.g. car reached max charge limit).
     * If there are active sessions but charger meter shows no draw, end them
     * and set charge limit back to min to prevent auto-restart.
     * @param chargerDraw current charger power draw in watts from the Shelly meter
     */
    void detectChargeStopped(int chargerDraw);

    /**
     * Drop the per-cycle vehicle data cache so the next reads are fresh.
     * Called at the start of every optimization cycle. Never talks to the vehicle.
     */
    void clearCycleCache();

    /**
     * Drop every cached assumption, including which car is considered connected and any
     * charge session started before the sleep. Used when the optimizer wakes up after being
     * asleep (manual cool down), because the cars may have been swapped or unplugged in the
     * meantime. Never talks to the vehicle.
     */
    void resetCachedState();
}
