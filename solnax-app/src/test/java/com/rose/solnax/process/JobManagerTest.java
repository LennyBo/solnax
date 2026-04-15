package com.rose.solnax.process;


import com.rose.solnax.TestcontainersConfiguration;
import com.rose.solnax.model.entity.PowerLog;
import com.rose.solnax.process.adapters.chargepoints.IChargePoint;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.mockito.Mockito.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class JobManagerTest {


    @Mock
    private PowerLogManager powerLogManager;

    @Mock
    private IChargePoint chargePoint;

    @InjectMocks
    private JobManager optimizer; // class containing optimizePower()

    private PowerLog log(int houseOut, int chargerOut) {
        PowerLog log = new PowerLog();
        log.setHouse(houseOut);
        log.setCharger(chargerOut);
        return log;
    }

    @Test
    void shouldDoNothingWhenNoLogFound() {
        when(powerLogManager.getLastPowerLog()).thenReturn(null);

        optimizer.optimizePower();

        verifyNoInteractions(chargePoint);
    }

    @Test
    void shouldStartChargingWhenExcessPowerAndNotCharging() {
        // house = -4000 (exporting 4000W), charger = 0 → available = 0 - (-4000) - 500 = 3500W
        when(powerLogManager.getLastPowerLog())
                .thenReturn(log(-4000, 0));
        when(chargePoint.isCurrentlyCharging()).thenReturn(false);
        when(chargePoint.getMinPower()).thenReturn(3450L);

        optimizer.optimizePower();

        verify(chargePoint).startCharge();
        verify(chargePoint).adjustChargePower(4000);
    }

    @Test
    void shouldAdjustAmpsWhenAlreadyCharging() {
        // house = -2000 (exporting 2000W), charger = 3000 → available = 3000 - (-2000) - 500 = 4500W
        when(powerLogManager.getLastPowerLog())
                .thenReturn(log(-2000, 3000));
        when(chargePoint.isCurrentlyCharging()).thenReturn(true);
        when(chargePoint.getMinPower()).thenReturn(3450L);

        optimizer.optimizePower();

        verify(chargePoint).adjustChargePower(5000);
        verify(chargePoint, never()).startCharge();
        verify(chargePoint, never()).stopCharge();
    }

    @Test
    void shouldStopChargingWhenInsufficientSurplus() {
        // house = 2500 (importing 2500W), charger = 3500 → available = 3500 - 2500 - 500 = 500W (below min)
        when(powerLogManager.getLastPowerLog())
                .thenReturn(log(2500, 3500));
        when(chargePoint.isCurrentlyCharging()).thenReturn(true);
        when(chargePoint.getMinPower()).thenReturn(3450L);

        optimizer.optimizePower();

        verify(chargePoint).stopCharge();
        verify(chargePoint, never()).startCharge();
    }

    @Test
    void shouldNotStartWhenNotEnoughSurplus() {
        // house = -1000 (exporting 1000W), charger = 0 → available = 0 - (-1000) - 500 = 500W (below min)
        when(powerLogManager.getLastPowerLog())
                .thenReturn(log(-1000, 0));
        when(chargePoint.isCurrentlyCharging()).thenReturn(false);
        when(chargePoint.getMinPower()).thenReturn(3450L);

        optimizer.optimizePower();

        verify(chargePoint, never()).startCharge();
        verify(chargePoint, never()).stopCharge();
        verify(chargePoint, never()).adjustChargePower(anyInt());
    }

    @Test
    void shouldNotStopWhenNotCharging() {
        // house = 2500 (importing), charger = 0, not charging → nothing to stop
        when(powerLogManager.getLastPowerLog())
                .thenReturn(log(2500, 0));
        when(chargePoint.isCurrentlyCharging()).thenReturn(false);
        when(chargePoint.getMinPower()).thenReturn(3450L);

        optimizer.optimizePower();

        verify(chargePoint, never()).startCharge();
        verify(chargePoint, never()).stopCharge();
        verify(chargePoint, never()).adjustChargePower(anyInt());
    }

    @Test
    void shouldDoNothingInNeutralPowerRange() {
        // house = -1000 (exporting 1000W), charger = 0 → available = 500W < 3450W
        when(powerLogManager.getLastPowerLog())
                .thenReturn(log(-1000, 0));
        when(chargePoint.isCurrentlyCharging()).thenReturn(false);
        when(chargePoint.getMinPower()).thenReturn(3450L);

        optimizer.optimizePower();

        verify(chargePoint, never()).startCharge();
        verify(chargePoint, never()).stopCharge();
    }

    @Test
    void shouldStartAtExactMinPowerBoundary() {
        // house = -3950 (exporting 3950W), charger = 0 → available = 3950 - 500 = 3450W = exactly minPower
        when(powerLogManager.getLastPowerLog())
                .thenReturn(log(-3950, 0));
        when(chargePoint.isCurrentlyCharging()).thenReturn(false);
        when(chargePoint.getMinPower()).thenReturn(3450L);

        optimizer.optimizePower();

        verify(chargePoint).startCharge();
        verify(chargePoint).adjustChargePower(3950);
    }

    @Test
    void shouldReduceAmpsWhenSurplusDrops() {
        // Charging at 7000W, but surplus dropped: house = 500 (importing 500W)
        // available = 7000 - 500 - 500 = 6000W → adjust down
        when(powerLogManager.getLastPowerLog())
                .thenReturn(log(500, 7000));
        when(chargePoint.isCurrentlyCharging()).thenReturn(true);
        when(chargePoint.getMinPower()).thenReturn(3450L);

        optimizer.optimizePower();

        verify(chargePoint).adjustChargePower(6500);
        verify(chargePoint, never()).stopCharge();
    }
}
