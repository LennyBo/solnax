package com.rose.solnax.process;


import com.rose.solnax.model.dto.InstantPower;
import com.rose.solnax.model.dto.PowerLogs;
import com.rose.solnax.model.entity.PowerLog;
import com.rose.solnax.model.repository.PowerLogRepository;
import com.rose.solnax.process.adapters.meters.IPowerMeter;
import com.rose.solnax.process.adapters.meters.shelly.ShellyEm3Client;
import com.rose.solnax.process.adapters.meters.shelly.ShellyEm3Exception;
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
                        Integer house = calculateDerivedHouse(actual);
                        paddedLogs.getSolar().add(actual.getSolar());
                        paddedLogs.getHouse().add(house == null ? null : Math.max(house, 0));
                        paddedLogs.getCharger().add(actual.getCharger());
                        paddedLogs.getHeater().add(actual.getHeater());
                        paddedLogs.getKitchen().add(actual.getKitchen());
                    }else{
                        paddedLogs.getSolar().add(null);
                        paddedLogs.getHouse().add(null);
                        paddedLogs.getCharger().add(null);
                        paddedLogs.getHeater().add(null);
                        paddedLogs.getKitchen().add(null);
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
                .solar(toKilowatts(powerLogCached.getSolar()))
                .house(toInvertedKilowatts(powerLogCached.getHouse()))
                .heat(toPositiveKilowatts(powerLogCached.getHeater()))
                .charger(toPositiveKilowatts(powerLogCached.getCharger()))
                .build();
    }


    public PowerLog getPowerLog() {
        Integer houseOut = inverter.gridMeter();
        Integer solarIn = inverter.solarMeter();
        Integer heater = readShellyPower("heater", -1);
        Integer charger = readShellyPower("charger", 1);
        Integer kitchen = readShellyPower("kitchen", -1);

        return PowerLog.builder()
                .time(LocalDateTime.now())
                .solar(solarIn)
                .house(houseOut)
                .charger(charger)
                .heater(heater)
                .kitchen(kitchen)
                .build();
    }

    @Transactional
    public PowerLog logPower() {
        return powerLogRepository.save(getPowerLog());
    }

    private Integer calculateDerivedHouse(PowerLog actual) {
        if (actual.getSolar() == null || actual.getHouse() == null || actual.getCharger() == null
                || actual.getKitchen() == null || actual.getHeater() == null) {
            return null;
        }

        return actual.getSolar() + actual.getHouse() - actual.getCharger() - actual.getKitchen() - actual.getHeater();
    }

    private Integer readShellyPower(String deviceId, int direction) {
        ShellyEm3Client client = registry.getAll().get(deviceId);
        if (client == null) {
            log.warn("Shelly '{}' is not configured; storing null for this reading", deviceId);
            return null;
        }

        try {
            double watts = client.getTotalActivePowerW() * direction;
            return (int) Math.max(0, watts);
        } catch (ShellyEm3Exception ex) {
            log.warn("Shelly '{}' did not respond; storing null for this reading: {}", deviceId, ex.getMessage());
            return null;
        }
    }

    private Double toKilowatts(Integer watts) {
        return watts == null ? null : watts / 1000.0;
    }

    private Double toPositiveKilowatts(Integer watts) {
        return watts == null ? null : Math.max(0, watts / 1000.0);
    }

    private Double toInvertedKilowatts(Integer watts) {
        return watts == null ? null : watts / 1000.0 * -1;
    }
}
