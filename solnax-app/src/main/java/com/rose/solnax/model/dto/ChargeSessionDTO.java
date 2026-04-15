package com.rose.solnax.model.dto;

import com.rose.solnax.model.entity.ChargeSession;
import com.rose.solnax.model.entity.enums.ChargeSessionStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class ChargeSessionDTO {

    private UUID id;
    private String vin;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Double energyChargedKwh;
    private Integer ampsSet;
    private ChargeSessionStatus status;

    public static ChargeSessionDTO from(ChargeSession session) {
        return ChargeSessionDTO.builder()
                .id(session.getId())
                .vin(session.getVin())
                .startedAt(session.getStartedAt())
                .endedAt(session.getEndedAt())
                .energyChargedKwh(session.getEnergyChargedKwh())
                .ampsSet(session.getAmpsSet())
                .status(session.getStatus())
                .build();
    }
}

