package com.rose.solnax.process.adapters.chargepoints.tesla.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class TeslaWallConnectorStatus {

    private String maxAmpsToDivideAmongSlaves;
    private String wiringMaxAmpsAllTWCs;
    private String minAmpsPerTWC;
    private String chargeNowAmps;

    private String nonScheduledAmpsMax;
    private String scheduledAmpsMax;

    private String scheduledAmpStartTime;
    private String scheduledAmpsEndTime;

    private String resumeTrackGreenEnergyTime;

    @JsonProperty("TWC")
    private List<Twc> twc;

    // ==================================================
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Twc {

        private String twcModelMaxAmps;
        private String twcChargeSpeed;
        private String twcChargeAvailable;
    }
}

