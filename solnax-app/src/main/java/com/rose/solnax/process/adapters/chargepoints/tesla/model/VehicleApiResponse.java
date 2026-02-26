package com.rose.solnax.process.adapters.chargepoints.tesla.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VehicleApiResponse {

    private Response response;

    public boolean isConnected() {
        return isChargeStateSet() && !"Disconnected".equals(response.getResponse().getCharge_state().getCharging_state());
    }

    public boolean canCharge() {
        return isChargeStateSet() &&
                chargeState().battery_level < 80 &&
                (
                        "Connected".equals(chargeState().getCharging_state()) ||
                                "Stopped".equals(chargeState().getCharging_state()) ||
                                "Complete".equals(chargeState().getCharging_state())
                );
    }

    public boolean isActivelyCharging(){
        return isChargeStateSet() &&
                "Charging".equals(response.getResponse().getCharge_state().getCharging_state());
    }

    private ChargeState chargeState() {
        return response.getResponse().getCharge_state();
    }


    private boolean isChargeStateSet() {
        return response != null &&
                response.getResponse() != null &&
                response.getResponse().getCharge_state() != null;
    }

    public boolean isBatteryFull() {
        return isChargeStateSet() &&
                chargeState().battery_level >= 79;
    }

    // ==================================================
    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Response {

        private Boolean result;
        private String reason;
        private String vin;
        private String command;
        private VehicleData response;
    }

    // ==================================================
    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VehicleData {

        private ChargeState charge_state;
        private ClimateState climate_state;
    }

    // ==================================================
    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChargeState {

        private long timestamp;
        private String charging_state;

        private int charge_limit_soc;
        private int charge_limit_soc_std;
        private int charge_limit_soc_min;
        private int charge_limit_soc_max;

        private boolean battery_heater_on;
        private boolean not_enough_power_to_heat;

        private int max_range_charge_counter;
        private boolean fast_charger_present;
        private String fast_charger_type;

        private double battery_range;
        private double est_battery_range;
        private double ideal_battery_range;

        private int battery_level;
        private int usable_battery_level;

        private double charge_energy_added;
        private double charge_miles_added_rated;
        private double charge_miles_added_ideal;

        private int charger_voltage;
        private int charger_pilot_current;
        private int charger_actual_current;
        private int charger_power;

        private boolean trip_charging;
        private int charge_rate;

        private boolean charge_port_door_open;

        private String scheduled_charging_mode;
        private long scheduled_departure_time;
        private int scheduled_departure_time_minutes;

        private boolean supercharger_session_trip_planner;

        private long scheduled_charging_start_time;
        private boolean scheduled_charging_pending;

        private boolean user_charge_enable_request;
        private boolean charge_enable_request;

        private int charger_phases;

        private String charge_port_latch;

        private int charge_current_request;
        private int charge_current_request_max;
        private int charge_amps;

        private boolean off_peak_charging_enabled;
        private String off_peak_charging_times;
        private long off_peak_hours_end_time;

        private boolean preconditioning_enabled;
        private String preconditioning_times;

        private boolean managed_charging_active;
        private boolean managed_charging_user_canceled;
        private long managed_charging_start_time;

        private boolean charge_port_cold_weather_mode;
        private String charge_port_color;

        private String conn_charge_cable;
        private String fast_charger_brand;

        private int minutes_to_full_charge;
    }

    // ==================================================
    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ClimateState {

        private long timestamp;

        private boolean allow_cabin_overheat_protection;
        private boolean auto_seat_climate_left;
        private boolean auto_seat_climate_right;
        private boolean auto_steering_wheel_heat;

        private boolean bioweapon_mode;

        private String cabin_overheat_protection;
        private boolean cabin_overheat_protection_actively_cooling;

        private String cop_activation_temperature;

        private double inside_temp;
        private double outside_temp;

        private int driver_temp_setting;
        private int passenger_temp_setting;

        private int left_temp_direction;
        private int right_temp_direction;

        private boolean is_auto_conditioning_on;
        private boolean is_front_defroster_on;
        private boolean is_rear_defroster_on;

        private int fan_status;

        private String hvac_auto_request;

        private boolean is_climate_on;

        private int min_avail_temp;
        private int max_avail_temp;

        private int seat_heater_left;
        private int seat_heater_right;

        private int seat_heater_rear_left;
        private int seat_heater_rear_right;
        private int seat_heater_rear_center;

        private int seat_heater_rear_right_back;
        private int seat_heater_rear_left_back;

        private int steering_wheel_heat_level;
        private boolean steering_wheel_heater;

        private boolean supports_fan_only_cabin_overheat_protection;

        private boolean battery_heater;
        private boolean battery_heater_no_power;

        private String climate_keeper_mode;
        private String defrost_mode;

        private boolean is_preconditioning;

        private boolean remote_heater_control_enabled;

        private boolean side_mirror_heaters;
        private boolean wiper_blade_heater;
    }
}