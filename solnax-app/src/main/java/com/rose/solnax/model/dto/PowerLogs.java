package com.rose.solnax.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Getter
public class PowerLogs {

    public PowerLogs(){
        times = new ArrayList<>();
        solar = new ArrayList<>();
        house = new ArrayList<>();
        charger = new ArrayList<>();
        heater = new ArrayList<>();
        kitchen = new ArrayList<>();
    }

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
    List<LocalTime> times;
    List<Integer> solar;
    List<Integer> house;
    List<Integer> charger;
    List<Integer> heater;
    List<Integer> kitchen;

}
