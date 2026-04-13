package com.rose.solnax.process.adapters.meters.shelly.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Top-level response from GET /status
 * Only energy-meter related fields are mapped; all other fields are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShellyEm3Status(

        /** MAC address of the device */
        String mac,

        /** Uptime in seconds */
        @JsonProperty("uptime") long uptimeSeconds,

        /** WiFi RSSI */
        @JsonProperty("wifi_sta") WifiStatus wifi,

        /** Three emeter channels (phases L1/L2/L3) */
        @JsonProperty("emeters") List<EmeterChannel> emeters,

        /** Total energy counters across all phases */
        @JsonProperty("total_power") double totalPower

) {
    // -------------------------------------------------------------------------
    // Nested records
    // -------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WifiStatus(
    boolean connected,
    @JsonProperty("ssid") String ssid,
    @JsonProperty("rssi")  int rssi
    ) {}

    /**
     * Single emeter channel – maps both /status emeters[] entries
     * and individual /emeter/{n} responses.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EmeterChannel(

    /** True if valid readings are available */
    @JsonProperty("is_valid") boolean valid,

    /** Active (real) power in Watts */
    @JsonProperty("power") double powerW,

    /** Apparent power in VA */
    @JsonProperty("pf") double powerFactor,

    /** Reactive power in VAr */
    @JsonProperty("reactive") double reactiveVAr,

    /** RMS voltage in V */
    @JsonProperty("voltage") double voltageV,

    /** RMS current in A */
    @JsonProperty("current") double currentA,

    /** Total active energy counter in Wh */
    @JsonProperty("total") double totalEnergyWh,

    /** Total returned (feed-in) energy in Wh */
    @JsonProperty("total_returned") double totalReturnedWh
    ) {
        /** Convenience: apparent power = V × I */
        public double apparentPowerVA() {
            return voltageV * currentA;
        }
    }
}