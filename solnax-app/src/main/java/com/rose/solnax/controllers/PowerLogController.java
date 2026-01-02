package com.rose.solnax.controllers;

import com.rose.solnax.model.dto.PowerLogs;
import com.rose.solnax.process.PowerLogManager;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
public class PowerLogController {

    private final PowerLogManager powerLogManager;

    @GetMapping("/api/power")
    public PowerLogs getPowerLogOnDay(@RequestParam("onDate") Optional<LocalDate> onDateOpt){
        LocalDateTime start;
        LocalDateTime stop;
        LocalDate onDate = onDateOpt.orElseGet(LocalDate::now);
        start = onDate.atTime(LocalTime.of(0,0));
        stop = onDate.plusDays(1).atTime(LocalTime.of(0,0));
        return powerLogManager.getPowerLogDTOForPeriod(start,stop);
    }
}
