package com.rose.solnax.process.adapters.chargepoints;

import com.rose.solnax.model.entity.ChargeSession;
import com.rose.solnax.model.entity.ChargePointCoolDown;
import com.rose.solnax.model.entity.enums.CoolDownReason;
import com.rose.solnax.process.ChargePointCoolDownManager;
import com.rose.solnax.process.ChargeSessionManager;
import com.rose.solnax.process.adapters.chargepoints.tesla.TeslaBLEAdapter;
import com.rose.solnax.process.adapters.chargepoints.tesla.model.VehicleApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeslaWallChargerTest {

    private static final String BLACK_VIN = "VINBLACK";
    private static final String WHITE_VIN = "VINWHITE";

    @Mock
    private ChargePointCoolDownManager chargePointCoolDownManager;

    @Mock
    private ChargeSessionManager chargeSessionManager;

    @Mock
    private TeslaBLEAdapter bleAdapter;

    private TeslaWallCharger wallCharger;

    @BeforeEach
    void setUp() {
        wallCharger = new TeslaWallCharger(chargePointCoolDownManager, chargeSessionManager, bleAdapter);
        ReflectionTestUtils.setField(wallCharger, "blackVin", BLACK_VIN);
        ReflectionTestUtils.setField(wallCharger, "whiteVin", WHITE_VIN);
        ReflectionTestUtils.setField(wallCharger, "maxChargeLevel", 80);
        ReflectionTestUtils.setField(wallCharger, "minChargeLevel", 60);
        ReflectionTestUtils.setField(wallCharger, "defaultVoltage", 230);
        ReflectionTestUtils.setField(wallCharger, "defaultPhases", 3);
        ReflectionTestUtils.setField(wallCharger, "minAmps", 5);
        ReflectionTestUtils.setField(wallCharger, "maxAmps", 16);
    }

    @Test
    void shouldPrepareBothCarsWhenBothAreFreshlyNotConnected() {
        when(chargePointCoolDownManager.getActiveCoolDowns()).thenReturn(List.of());
        when(chargePointCoolDownManager.hasActiveCoolDownForTarget(anyString())).thenReturn(false);
        when(bleAdapter.vehicle_data(BLACK_VIN)).thenReturn(vehicle("Disconnected", 65));
        when(bleAdapter.vehicle_data(WHITE_VIN)).thenReturn(vehicle("Disconnected", 70));

        wallCharger.startCharge();

        verify(bleAdapter).setChargeState(80, BLACK_VIN);
        verify(bleAdapter).setChargeState(80, WHITE_VIN);
        verify(bleAdapter, never()).chargeStart(anyString());
        verify(chargeSessionManager, never()).startSession(anyString(), any());
        verify(chargePointCoolDownManager).coolDown(BLACK_VIN, CoolDownReason.NOT_CONNECTED);
        verify(chargePointCoolDownManager).coolDown(WHITE_VIN, CoolDownReason.NOT_CONNECTED);
    }

    @Test
    void shouldPrepareOnlyDisconnectedCarWhenOtherCarIsFull() {
        when(chargePointCoolDownManager.getActiveCoolDowns()).thenReturn(List.of());
        when(chargePointCoolDownManager.hasActiveCoolDownForTarget(anyString())).thenReturn(false);
        when(bleAdapter.vehicle_data(BLACK_VIN)).thenReturn(vehicle("Complete", 80));
        when(bleAdapter.vehicle_data(WHITE_VIN)).thenReturn(vehicle("Disconnected", 72));

        wallCharger.startCharge();

        verify(chargePointCoolDownManager).coolDown(BLACK_VIN, CoolDownReason.FULL);
        verify(chargePointCoolDownManager).coolDown(WHITE_VIN, CoolDownReason.NOT_CONNECTED);
        verify(bleAdapter).setChargeState(80, WHITE_VIN);
        verify(bleAdapter, never()).setChargeState(80, BLACK_VIN);
        verify(bleAdapter, never()).chargeStart(anyString());
    }

    @Test
    void shouldNotPrepareDisconnectedCarsAgainWhileNotConnectedCooldownIsActive() {
        when(chargePointCoolDownManager.getActiveCoolDowns()).thenReturn(List.of(
                cooldown(BLACK_VIN, CoolDownReason.NOT_CONNECTED),
                cooldown(WHITE_VIN, CoolDownReason.NOT_CONNECTED)
        ));

        wallCharger.startCharge();

        verify(chargePointCoolDownManager).getActiveCoolDowns();
        verifyNoMoreInteractions(chargePointCoolDownManager, bleAdapter, chargeSessionManager);
    }

    @Test
    void shouldCreateLowBatteryCooldownWhenAutoChargingStartsBelowSixty() {
        when(chargeSessionManager.getActiveSessions()).thenReturn(List.of());
        when(chargePointCoolDownManager.getActiveCoolDowns()).thenReturn(List.of());
        when(bleAdapter.vehicle_data(BLACK_VIN)).thenReturn(vehicle("Charging", 55));

        wallCharger.detectAutoCharging(4000);

        verify(chargePointCoolDownManager).clearCoolDownsByReasonAndTarget(BLACK_VIN, CoolDownReason.NOT_CONNECTED);
        verify(chargePointCoolDownManager).clearCoolDownsByReasonAndTarget(WHITE_VIN, CoolDownReason.NOT_CONNECTED);
        verify(chargeSessionManager).startSession(eq(BLACK_VIN), any());
        verify(chargePointCoolDownManager).coolDown(BLACK_VIN, CoolDownReason.LOW_BATTERY);
    }

    @Test
    void shouldKeepChargingWhenInsufficientSurplusAndLowBatteryCooldownIsActive() {
        when(chargeSessionManager.getActiveSessions()).thenReturn(List.of(activeSession()));
        when(chargePointCoolDownManager.getActiveCoolDowns()).thenReturn(List.of(cooldown(BLACK_VIN, CoolDownReason.LOW_BATTERY)));

        wallCharger.handleInsufficientSurplus();

        verify(bleAdapter).setChargeState(60, BLACK_VIN);
        verify(bleAdapter, never()).chargeStop(anyString());
        verify(chargeSessionManager, never()).endSession(anyString(), any());
    }

    @Test
    void shouldRefreshBatteryStateAndStopWhenInsufficientSurplusAndBatteryIsNotLow() {
        when(chargeSessionManager.getActiveSessions()).thenReturn(List.of(activeSession()));
        when(chargePointCoolDownManager.getActiveCoolDowns()).thenReturn(List.of());
        when(bleAdapter.vehicle_data(BLACK_VIN)).thenReturn(vehicle("Charging", 65));

        wallCharger.handleInsufficientSurplus();

        verify(bleAdapter).chargeStop(BLACK_VIN);
        verify(bleAdapter).setChargeState(60, BLACK_VIN);
        verify(chargeSessionManager).endSession(eq(BLACK_VIN), any());
    }

    @Test
    void shouldKeepChargingAtMinimumAmpsInEcoPlusInsteadOfStopping() {
        when(chargeSessionManager.getActiveSessions()).thenReturn(List.of(activeSession()));

        wallCharger.maintainMinimumCharge();

        verify(bleAdapter).setChargingAmps(5, BLACK_VIN);
        verify(chargeSessionManager).updateSessionAmps(BLACK_VIN, 5);
        verify(bleAdapter, never()).chargeStop(anyString());
        verify(bleAdapter, never()).setChargeState(anyInt(), anyString());
        verify(chargeSessionManager, never()).endSession(anyString(), any());
    }

    @Test
    void shouldDoNothingInEcoPlusWhenNoCarIsCharging() {
        when(chargeSessionManager.getActiveSessions()).thenReturn(List.of());
        when(bleAdapter.vehicle_data(BLACK_VIN)).thenReturn(vehicle("Disconnected", 65));
        when(bleAdapter.vehicle_data(WHITE_VIN)).thenReturn(vehicle("Disconnected", 65));

        wallCharger.maintainMinimumCharge();

        verify(bleAdapter, never()).setChargingAmps(anyInt(), anyString());
        verify(bleAdapter, never()).chargeStop(anyString());
    }

    @Test
    void shouldAbortStaleSessionsWhenWakingUpFromAManualSleep() {
        when(chargeSessionManager.getActiveSessions()).thenReturn(List.of(activeSession()));

        wallCharger.resetCachedState();

        verify(chargeSessionManager).abortSession(BLACK_VIN);
        verifyNoMoreInteractions(bleAdapter);
    }

    @Test
    void shouldNotTreatModeCoolDownsAsAPerVehicleBlock() {
        // Legacy rows: MANUAL cool downs used to be stored per VIN. They must never
        // block a single car — the optimizer evaluates modes globally instead.
        when(chargePointCoolDownManager.getActiveCoolDowns()).thenReturn(List.of(
                cooldown(BLACK_VIN, CoolDownReason.MANUAL),
                cooldown(WHITE_VIN, CoolDownReason.MANUAL)
        ));
        when(bleAdapter.vehicle_data(BLACK_VIN)).thenReturn(vehicle("Connected", 65));

        wallCharger.startCharge();

        verify(bleAdapter).chargeStart(BLACK_VIN);
    }

    @Test
    void shouldNotBlockChargingWhileEcoPlusModeIsActive() {
        when(chargePointCoolDownManager.getActiveCoolDowns()).thenReturn(List.of(
                cooldown(ChargePointCoolDownManager.GLOBAL_TARGET, CoolDownReason.ECO_PLUS)
        ));
        when(bleAdapter.vehicle_data(BLACK_VIN)).thenReturn(vehicle("Connected", 65));

        wallCharger.startCharge();

        verify(bleAdapter).chargeStart(BLACK_VIN);
    }

    private VehicleApiResponse vehicle(String chargingState, int batteryLevel) {
        return VehicleApiResponse.builder()
                .response(VehicleApiResponse.Response.builder()
                        .response(VehicleApiResponse.VehicleData.builder()
                                .charge_state(VehicleApiResponse.ChargeState.builder()
                                        .charging_state(chargingState)
                                        .battery_level(batteryLevel)
                                        .build())
                                .build())
                        .build())
                .build();
    }

    private ChargePointCoolDown cooldown(String vin, CoolDownReason reason) {
        return ChargePointCoolDown.builder()
                .time(LocalDateTime.now())
                .target(vin)
                .end(LocalDateTime.now().plusHours(1))
                .reason(reason)
                .build();
    }

    private ChargeSession activeSession() {
        return ChargeSession.builder()
                .vin(BLACK_VIN)
                .startedAt(LocalDateTime.now())
                .build();
    }
}
