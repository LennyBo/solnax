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
        log.setHouseOut(houseOut);
        log.setChargerOut(chargerOut);
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
        when(powerLogManager.getLastPowerLog())
                .thenReturn(log(-4000, 0));

        optimizer.optimizePower();

        verify(chargePoint).startCharge();
        verify(chargePoint, never()).stopCharge();
    }

    @Test
    void shouldNotStartWhenAlreadyCharging() {
        when(powerLogManager.getLastPowerLog())
                .thenReturn(log(-4000, 3500));

        optimizer.optimizePower();

        verifyNoInteractions(chargePoint);
    }

    @Test
    void shouldStopChargingWhenImportingAndCharging() {
        when(powerLogManager.getLastPowerLog())
                .thenReturn(log(2500, 3500));

        optimizer.optimizePower();

        verify(chargePoint).stopCharge();
        verify(chargePoint, never()).startCharge();
    }

    @Test
    void shouldNotStopWhenNotCharging() {
        when(powerLogManager.getLastPowerLog())
                .thenReturn(log(2500, 0));

        optimizer.optimizePower();

        verifyNoInteractions(chargePoint);
    }

    @Test
    void shouldDoNothingInNeutralPowerRange() {
        when(powerLogManager.getLastPowerLog())
                .thenReturn(log(-1000, 0));

        optimizer.optimizePower();

        verifyNoInteractions(chargePoint);
    }


    @Test
    void shouldNotStartAtExportThresholdBoundary() {
        when(powerLogManager.getLastPowerLog())
                .thenReturn(log(-3000, 0));

        optimizer.optimizePower();

        verifyNoInteractions(chargePoint);
    }

    @Test
    void shouldNotStopAtImportThresholdBoundary() {
        when(powerLogManager.getLastPowerLog())
                .thenReturn(log(2000, 3500));

        optimizer.optimizePower();

        verifyNoInteractions(chargePoint);
    }


}
