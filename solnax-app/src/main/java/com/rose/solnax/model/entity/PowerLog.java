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

    @Column(name="solar")
    private Integer solar;

    @Column(name="house")
    private Integer house;

    @Column(name="heater")
    private Integer heater;

    @Column(name="charger")
    private Integer charger;

    @Column(name="kitchen")
    private Integer kitchen;



    @Override
    public String toString() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
        return String.format(
                "[%s] solar=%dw, house=%dw, heater=%dw, charger=%dw, kitchen=%dw",
                time.format(fmt),
                solar,
                house,
                heater,
                charger,
                kitchen
        );
    }
}
