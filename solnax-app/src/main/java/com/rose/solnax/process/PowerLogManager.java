package com.rose.solnax.process;


import com.rose.solnax.model.dto.InstantPower;
import com.rose.solnax.model.dto.PowerLogs;
import com.rose.solnax.model.entity.PowerLog;
import com.rose.solnax.model.repository.PowerLogRepository;
import com.rose.solnax.process.adapters.chargepoints.tesla.TWCManagerAdapter;
import com.rose.solnax.process.adapters.meters.IPowerMeter;
import com.rose.solnax.process.adapters.meters.shelly.ShellyEm3Client;
import com.rose.solnax.process.adapters.meters.shelly.ShellyEm3Registry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
@Slf4j
public class PowerLogManager {

    private final IPowerMeter inverter;
    public final PowerLogRepository powerLogRepository;
    private final ShellyEm3Registry registry;


    @Transactional(readOnly = true)
    public PowerLog getLastPowerLog() {
        List<PowerLog> logs = powerLogRepository.findByTimeGreaterThanOrderByTimeDesc(LocalDateTime.now().minusMinutes(5));
        if (!logs.isEmpty()) {
            return logs.get(0);
        }
        return null;
    }

    @Transactional(readOnly = true)
    public PowerLogs getPowerLogDTOForPeriod(LocalDateTime start, LocalDateTime stop) {
        List<PowerLog> actualLogs = powerLogRepository.findByTimeBetweenOrderByTimeAsc(start, stop);

        // 1. Convert actual logs into a Map for quick lookup (key: LocalTime)
        Map<LocalTime, PowerLog> logMap = actualLogs.stream()
                .collect(Collectors.toMap(
                        log -> log.getTime().toLocalTime().withNano(0).withSecond(0),
                        Function.identity(),
                        (existing, replacement) -> existing // Handle duplicates if they exist
                ));

        // 2. Generate all 5-minute intervals for the day
        LocalTime dayStart = LocalTime.MIN; // 00:00
        int totalIntervals = 288; // (24 * 60) / 5

        PowerLogs paddedLogs = new PowerLogs();

        Stream.iterate(dayStart, time -> time.plusMinutes(5))
                .limit(totalIntervals)
                .forEach(currentTime -> {
                    paddedLogs.getTimes().add(currentTime);

                    PowerLog actual = logMap.get(currentTime);
                    if (actual != null) {
                        // Data exists for this slot
                        int house = actual.getSolar() + actual.getHouse() - actual.getCharger() - actual.getKitchen() - actual.getHeater();
                        paddedLogs.getSolar().add(actual.getSolar());
                        paddedLogs.getHouse().add(Math.max(house, 0));
                        paddedLogs.getCharger().add(actual.getCharger());
                        paddedLogs.getHeater().add(actual.getHeater());
                        paddedLogs.getKitchen().add(actual.getKitchen());
                    }else{
                        paddedLogs.getSolar().add(0);
                        paddedLogs.getHouse().add(0);
                        paddedLogs.getCharger().add(0);
                        paddedLogs.getHeater().add(0);
                        paddedLogs.getKitchen().add(0);
                    }
                });

        return paddedLogs;
    }


    /**
     * This method acts as the "Cached" version.
     * Spring will skip the method body if a value is found in 'power_logs'.
     */
    @Cacheable(value = "power_logs", key = "'latest'")
    public InstantPower getInstantPower() {
        PowerLog powerLogCached = getPowerLog();
        return InstantPower.builder()
                .solar(Math.max(0,powerLogCached.getSolar() / 1000.0))
                .house(Math.max(0,powerLogCached.getHouse() / 1000.0 * -1))
                .heat(Math.max(0,powerLogCached.getHeater() / 1000.0))
                .charger(Math.max(0,powerLogCached.getCharger() / 1000.0))
                .build();
    }


    public PowerLog getPowerLog() {
        Integer houseOut = inverter.gridMeter();
        Integer solarIn = inverter.solarMeter();
        Map<String, ShellyEm3Client> all = registry.getAll();

        double heater = all.get("heater").getTotalActivePowerW() * -1;
        double charger = all.get("charger").getTotalActivePowerW();
        //double sauna = all.get("sauna").getTotalActivePowerW();
        double kitchen = all.get("kitchen").getTotalActivePowerW() * -1;
        return PowerLog.builder()
                .time(LocalDateTime.now())
                .solar(solarIn)
                .house(houseOut)
                .charger((int) Math.max(0,charger))
                .heater((int) Math.max(0,heater))
                .kitchen((int) Math.max(0,kitchen))
                .build();
    }

    @Transactional
    public PowerLog logPower() {
        return powerLogRepository.save(getPowerLog());
    }
}
