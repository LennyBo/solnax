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
        solarIn = new ArrayList<>();
        house = new ArrayList<>();
    }

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
    List<LocalTime> times;
    List<Integer> solarIn;
    List<Integer> house;

}
