package com.rose.solnax.process;

import com.rose.solnax.model.entity.PowerLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobManager {

    private final PowerLogManager powerLogManager;
    private final ChargeOptimizer chargeOptimizer;

    /**
     * Single job during daylight: logs power (TX1), then immediately optimizes (TX2).
     * No 1-minute delay needed — the freshly saved PowerLog is passed directly.
     */
    @Scheduled(cron = "0 */5 5-22 * * *")
    void logAndOptimize() {
        PowerLog powerLog = powerLogManager.logPower();
        log.info("Logged power log: {}", powerLog);

        chargeOptimizer.optimize(powerLog);
    }

    /**
     * Night-time: just log power, no optimization needed.
     */
    @Scheduled(cron = "0 */5 0-4,23 * * *")
    void logPowerOnly() {
        PowerLog powerLog = powerLogManager.logPower();
        log.info("Logged power log (night): {}", powerLog);
    }
}
