package com.rose.solnax.model.entity;

import com.rose.solnax.model.entity.enums.ChargeSessionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargeSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "vin", nullable = false)
    private String vin;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "energy_start_kwh")
    private Double energyStartKwh;

    @Column(name = "energy_end_kwh")
    private Double energyEndKwh;

    @Column(name = "energy_charged_kwh")
    private Double energyChargedKwh;

    @Column(name = "amps_set")
    private Integer ampsSet;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ChargeSessionStatus status;

    @Override
    public String toString() {
        return String.format("[%s] vin=%s, status=%s, amps=%d, charged=%.2f kWh",
                startedAt, vin, status, ampsSet, energyChargedKwh != null ? energyChargedKwh : 0.0);
    }
}

