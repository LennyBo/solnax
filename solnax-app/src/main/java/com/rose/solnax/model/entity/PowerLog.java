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
    private Double solarIn;

    @Column(name="house_out")
    private Double houseOut;

    @Column(name="heat_out")
    private Double heatOut;

    @Column(name="charger_out")
    private Double chargerOut;

    @Column(name="power_wall")
    private Double powerWall;

    @Override
    public String toString() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
        return String.format(
                "[%s] solar_in=%.2fw, house_out=%.2fw, heat_out=%.2fw, charger_out=%.2fw, power_wall=%.2fw",
                time.format(fmt),
                solarIn,
                houseOut,
                heatOut,
                chargerOut,
                powerWall
        );
    }
}
