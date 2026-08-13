package com.rose.solnax.process;


import com.rose.solnax.model.entity.PowerLog;
import com.rose.solnax.model.entity.enums.ChargeControlMode;
import com.rose.solnax.process.adapters.chargepoints.IChargePoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChargeOptimizerTest {

    @Mock
    private IChargePoint chargePoint;

    @Mock
    private ChargePointCoolDownManager coolDownManager;

    @InjectMocks
    private ChargeOptimizer optimizer;

    private PowerLog log(int houseOut, int chargerOut) {
        PowerLog log = new PowerLog();
        log.setHouse(houseOut);
        log.setCharger(chargerOut);
        return log;
    }

    private void mode(ChargeControlMode mode) {
        when(coolDownManager.getActiveChargeControlMode()).thenReturn(mode);
    }

    // ─── NORMAL mode ────────────────────────────────────────────────────

    @Test
    void shouldStartChargingWhenExcessPowerAndNotCharging() {
        // house = -4000 (exporting 4000W), charger = 0 → available = 4000W

        mode(ChargeControlMode.NORMAL);
        when(chargePoint.getMinPower()).thenReturn(3450L);
        when(chargePoint.getBatteryLevel(false)).thenReturn(60);

        optimizer.optimize(log(-4000, 0));

        verify(chargePoint).startCharge();
        verify(chargePoint).adjustChargePower(4000);
    }

    @Test
    void shouldAdjustAmpsWhenAlreadyCharging() {
        // house = -2000 (exporting 2000W), charger = 4000 → available = 6000W

        mode(ChargeControlMode.NORMAL);
        when(chargePoint.getMinPower()).thenReturn(3450L);
        when(chargePoint.getBatteryLevel(false)).thenReturn(60);

        optimizer.optimize(log(-2000, 4000));

        verify(chargePoint).adjustChargePower(6000);
        verify(chargePoint, never()).startCharge();
        verify(chargePoint, never()).stopCharge();
    }

    @Test
    void shouldStopChargingWhenInsufficientSurplus() {
        // house = 2500 (importing 2500W), charger = 4000 → available = 1500W (below min - tolerance)

        mode(ChargeControlMode.NORMAL);
        when(chargePoint.getMinPower()).thenReturn(3450L);
        when(chargePoint.getBatteryLevel(false)).thenReturn(60);

        optimizer.optimize(log(2500, 4000));

        verify(chargePoint).handleInsufficientSurplus();
        verify(chargePoint, never()).maintainMinimumCharge();
        verify(chargePoint, never()).startCharge();
    }

    @Test
    void shouldDelegateUnknownBatteryHandlingWhenInsufficientSurplus() {
        mode(ChargeControlMode.NORMAL);
        when(chargePoint.getMinPower()).thenReturn(3450L);
        when(chargePoint.getBatteryLevel(false)).thenReturn(-1);

        optimizer.optimize(log(2500, 4000));

        verify(chargePoint).handleInsufficientSurplus();
        verify(chargePoint, never()).startCharge();
    }

    @Test
    void shouldDelegateLowBatteryDecisionWhenBatteryIsKnownLow() {
        mode(ChargeControlMode.NORMAL);
        when(chargePoint.getMinPower()).thenReturn(3450L);
        when(chargePoint.getBatteryLevel(false)).thenReturn(20);

        optimizer.optimize(log(2500, 4000));

        verify(chargePoint).handleInsufficientSurplus();
        verify(chargePoint, never()).startCharge();
    }

    @Test
    void shouldNotStartWhenNotEnoughSurplus() {
        // house = -1000 (exporting 1000W), charger = 0 → available = 1000W (below min)

        mode(ChargeControlMode.NORMAL);
        when(chargePoint.getMinPower()).thenReturn(3450L);
        when(chargePoint.getBatteryLevel(false)).thenReturn(60);

        optimizer.optimize(log(-1000, 0));

        verify(chargePoint, never()).startCharge();
        verify(chargePoint, never()).stopCharge();
        verify(chargePoint, never()).adjustChargePower(anyInt());
    }

    @Test
    void shouldNotStopWhenNotCharging() {
        // house = 2500 (importing), charger = 0, not charging → nothing to stop

        mode(ChargeControlMode.NORMAL);
        when(chargePoint.getMinPower()).thenReturn(3450L);
        when(chargePoint.getBatteryLevel(false)).thenReturn(60);

        optimizer.optimize(log(2500, 0));

        verify(chargePoint, never()).startCharge();
        verify(chargePoint, never()).stopCharge();
        verify(chargePoint, never()).handleInsufficientSurplus();
        verify(chargePoint, never()).adjustChargePower(anyInt());
    }

    @Test
    void shouldStartAtExactMinPowerBoundary() {
        mode(ChargeControlMode.NORMAL);
        when(chargePoint.getMinPower()).thenReturn(3450L);
        when(chargePoint.getBatteryLevel(false)).thenReturn(60);

        optimizer.optimize(log(-3450, 0));

        verify(chargePoint).startCharge();
        verify(chargePoint).adjustChargePower(3450);
    }

    @Test
    void shouldReduceAmpsWhenSurplusDrops() {
        // Charging at 7000W, but surplus dropped: house = 500 (importing 500W) → available = 6500W

        mode(ChargeControlMode.NORMAL);
        when(chargePoint.getMinPower()).thenReturn(3450L);
        when(chargePoint.getBatteryLevel(false)).thenReturn(60);

        optimizer.optimize(log(500, 7000));

        verify(chargePoint).adjustChargePower(6500);
        verify(chargePoint, never()).handleInsufficientSurplus();
    }

    @Test
    void shouldTreatChargingAtMinimumAmpsAsCharging() {
        // Charging at exactly minPower with no surplus left → available = 0W
        // The draw is not above minPower, but it is clearly a running charge.

        mode(ChargeControlMode.NORMAL);
        when(chargePoint.getMinPower()).thenReturn(3450L);
        when(chargePoint.getBatteryLevel(false)).thenReturn(60);

        optimizer.optimize(log(3450, 3450));

        verify(chargePoint).handleInsufficientSurplus();
        verify(chargePoint, never()).startCharge();
    }

    // ─── MANUAL mode ────────────────────────────────────────────────────

    @Test
    void shouldDoAbsolutelyNothingWhileManualCoolDownIsActive() {
        mode(ChargeControlMode.MANUAL);

        optimizer.optimize(log(2500, 4000));

        verifyNoInteractions(chargePoint);
    }

    @Test
    void shouldNotStartChargingWhileManualCoolDownIsActive() {
        mode(ChargeControlMode.MANUAL);

        optimizer.optimize(log(-8000, 0));

        verifyNoInteractions(chargePoint);
    }

    @Test
    void shouldResetCachedVehicleStateWhenWakingUpFromManualCoolDown() {
        when(coolDownManager.getActiveChargeControlMode())
                .thenReturn(ChargeControlMode.MANUAL, ChargeControlMode.NORMAL);
        when(chargePoint.getMinPower()).thenReturn(3450L);
        when(chargePoint.getBatteryLevel(false)).thenReturn(60);

        optimizer.optimize(log(2500, 4000));
        optimizer.optimize(log(-4000, 0));

        verify(chargePoint).resetCachedState();
        verify(chargePoint, never()).clearCycleCache();
        verify(chargePoint).startCharge();
    }

    @Test
    void shouldOnlyClearTheCycleCacheWhileStayingAwake() {
        mode(ChargeControlMode.NORMAL);
        when(chargePoint.getMinPower()).thenReturn(3450L);
        when(chargePoint.getBatteryLevel(false)).thenReturn(60);

        optimizer.optimize(log(-4000, 0));

        verify(chargePoint).clearCycleCache();
        verify(chargePoint, never()).resetCachedState();
    }

    // ─── ECO_PLUS mode ──────────────────────────────────────────────────

    @Test
    void shouldKeepMinimumChargeInsteadOfStoppingInEcoPlus() {
        mode(ChargeControlMode.ECO_PLUS);
        when(chargePoint.getMinPower()).thenReturn(3450L);
        when(chargePoint.getBatteryLevel(false)).thenReturn(60);

        optimizer.optimize(log(2500, 4000));

        verify(chargePoint).maintainMinimumCharge();
        verify(chargePoint, never()).handleInsufficientSurplus();
        verify(chargePoint, never()).stopCharge();
    }

    @Test
    void shouldStillFollowSolarProductionInEcoPlus() {
        mode(ChargeControlMode.ECO_PLUS);
        when(chargePoint.getMinPower()).thenReturn(3450L);
        when(chargePoint.getBatteryLevel(false)).thenReturn(60);

        optimizer.optimize(log(-2000, 4000));

        verify(chargePoint).adjustChargePower(6000);
        verify(chargePoint, never()).maintainMinimumCharge();
    }

    @Test
    void shouldStartChargingOnSurplusInEcoPlus() {
        mode(ChargeControlMode.ECO_PLUS);
        when(chargePoint.getMinPower()).thenReturn(3450L);
        when(chargePoint.getBatteryLevel(false)).thenReturn(60);

        optimizer.optimize(log(-4000, 0));

        verify(chargePoint).startCharge();
        verify(chargePoint).adjustChargePower(4000);
    }

    @Test
    void shouldNotForceStartChargingWithoutSurplusInEcoPlus() {
        // Eco+ never stops a charge, but it also never starts one without surplus.

        mode(ChargeControlMode.ECO_PLUS);
        when(chargePoint.getMinPower()).thenReturn(3450L);
        when(chargePoint.getBatteryLevel(false)).thenReturn(60);

        optimizer.optimize(log(2500, 0));

        verify(chargePoint, never()).startCharge();
        verify(chargePoint, never()).maintainMinimumCharge();
        verify(chargePoint, never()).adjustChargePower(anyInt());
    }

    // ─── Missing readings ───────────────────────────────────────────────

    @Test
    void shouldSkipOptimizationWhenChargerReadingIsMissing() {
        mode(ChargeControlMode.NORMAL);

        PowerLog log = new PowerLog();
        log.setHouse(-4000);
        log.setCharger(null);

        optimizer.optimize(log);

        verify(chargePoint, never()).startCharge();
        verify(chargePoint, never()).detectAutoCharging(anyInt());
        verify(chargePoint, never()).handleInsufficientSurplus();
        verify(chargePoint, never()).maintainMinimumCharge();
    }
}
