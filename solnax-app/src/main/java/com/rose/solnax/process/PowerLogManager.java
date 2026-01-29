package com.rose.solnax.process;


import com.rose.solnax.model.dto.InstantPower;
import com.rose.solnax.model.dto.PowerLogs;
import com.rose.solnax.model.entity.PowerLog;
import com.rose.solnax.model.repository.PowerLogRepository;
import com.rose.solnax.process.adapters.chargepoints.tesla.TWCManagerAdapter;
import com.rose.solnax.process.adapters.chargepoints.tesla.model.TeslaWallConnectorStatus;
import com.rose.solnax.process.adapters.meters.IPowerMeter;
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
    private final TWCManagerAdapter twcManagerAdapter;
    public final PowerLogRepository powerLogRepository;

    @Transactional
    public PowerLog save(PowerLog powerLog) {
        return powerLogRepository.save(powerLog);
    }


    @Transactional(readOnly = true)
    public PowerLog getLastPowerLog() {
        List<PowerLog> logs = powerLogRepository.findByTimeGreaterThanOrderByTime(LocalDateTime.now().minusMinutes(5));
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
                        Integer house = actual.getSolarIn() + actual.getHouseOut() - actual.getChargerOut();
                        paddedLogs.getSolarIn().add(actual.getSolarIn());
                        paddedLogs.getHouse().add(house);
                        paddedLogs.getCharger().add(actual.getChargerOut());
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
                .solar(powerLogCached.getSolarIn() / 1000.0)
                .house(powerLogCached.getHouseOut() / 1000.0 * -1)
                .heat(powerLogCached.getHeatOut() / 1000.0)
                .evCharger(powerLogCached.getChargerOut() / 1000.0)
                .build();
    }


    public PowerLog getPowerLog() {
        Integer houseOut = inverter.gridMeter();
        Integer solarIn = inverter.solarMeter();
        double chargeNowAmps = 0.0;
        try {
            TeslaWallConnectorStatus twcStatus = twcManagerAdapter.getTWCStatus();
            chargeNowAmps = Double.parseDouble(twcStatus.getTwc().get(0).getTwcChargeSpeed());
        }catch (Exception e){
            log.warn("Can't get TWCStatus");
        }

        return PowerLog.builder()
                .time(LocalDateTime.now())
                .solarIn(solarIn)
                .houseOut(houseOut)
                .chargerOut((int) (690 * chargeNowAmps))
                .heatOut(0)
                .build();
    }

    @Transactional
    public PowerLog logPower() {
        return powerLogRepository.save(getPowerLog());
    }
}
