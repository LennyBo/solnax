package com.rose.solnax.model.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PowerLog {

    @Id
    private LocalDateTime time;

    @Column(name="solar_in")
    private Integer solarIn;

    @Column(name="house_out")
    private Integer houseOut;

    @Column(name="heat_out")
    private Integer heatOut;

    @Column(name="charger_out")
    private Integer chargerOut;

    @Column(name="power_wall")
    private Integer powerWall;

    @Override
    public String toString() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
        return String.format(
                "[%s] solar_in=%dw, house_out=%dw, heat_out=%dw, charger_out=%dw, power_wall=%dw",
                time.format(fmt),
                solarIn,
                houseOut,
                heatOut,
                chargerOut,
                powerWall
        );
    }
}
