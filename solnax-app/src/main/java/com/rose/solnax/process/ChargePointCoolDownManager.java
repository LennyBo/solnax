package com.rose.solnax.process;


import com.rose.solnax.model.entity.ChargePointCoolDown;
import com.rose.solnax.model.entity.enums.CoolDownReason;
import com.rose.solnax.model.repository.ChargePointCooldownRepository;
import com.rose.solnax.process.exception.CoolDownAlreadyCreated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChargePointCoolDownManager {


    @Value("${tesla-ble.white}")
    private String whiteVin;

    @Value("${tesla-ble.black}")
    private String blackVin;

    private final ChargePointCooldownRepository chargePointCooldownRepository;

    @Transactional
    public void createCoolDownUntilTomorrow(){
        LocalDateTime now = LocalDateTime.now();
        boolean alreadyCool = hasActiveManualCoolDown();
        if(alreadyCool){
            throw new CoolDownAlreadyCreated("Already in a manual CoolDown for today");
        }
        LocalDateTime end = LocalDate.now().plusDays(1).atTime(6, 0);

        chargePointCooldownRepository.save(
                ChargePointCoolDown.builder()
                        .time(now)
                        .target(blackVin)
                        .end(end)
                        .reason(CoolDownReason.MANUAL)
                        .build()
        );
        chargePointCooldownRepository.save(
                ChargePointCoolDown.builder()
                        .time(now.plusSeconds(1)) //Dirty fix cus I can't be asked to do it now
                        .target(whiteVin)
                        .end(end)
                        .reason(CoolDownReason.MANUAL)
                        .build()
        );
        log.info("Created manual cool down until {}",end);
    }

    @Transactional(readOnly = true)
    public boolean hasActiveManualCoolDown(){
        return chargePointCooldownRepository.existsByEndAfterAndReason(LocalDateTime.now(), CoolDownReason.MANUAL);
    }

    @Transactional
    public void clearCoolDowns(){
        int i = chargePointCooldownRepository.deleteAllByEndAfter(LocalDateTime.now());
        log.info("Cleared {} cooldowns",i);
    }

    @Transactional(readOnly = true)
    public List<ChargePointCoolDown> getActiveCoolDowns() {
        return chargePointCooldownRepository.findAllByEndAfter(LocalDateTime.now());
    }

    @Transactional
    public void coolDown(String target, CoolDownReason coolDownReason) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime end = now;
        if(coolDownReason == CoolDownReason.FULL){
            end = LocalDateTime.now().plusHours(6);
        } else if (coolDownReason == CoolDownReason.NOT_CONNECTED) {
            // Escalating cooldowns: count recent NOT_CONNECTED cooldowns for this target
            int recentMisses = countRecentCoolDowns(target, CoolDownReason.NOT_CONNECTED, now.minusHours(12));
            int minutes = switch (recentMisses) {
                case 0 -> 30;
                case 1 -> 60;
                case 2 -> 120;
                default -> 240; // cap at 4 hours
            };
            end = now.plusMinutes(minutes);
            log.info("Escalating NOT_CONNECTED cooldown for {} — miss #{} → {} min", target, recentMisses + 1, minutes);
        } else if (coolDownReason == CoolDownReason.LOW_BATTERY) {
            end = LocalDateTime.now().plusMinutes(30);
        } else if (coolDownReason == CoolDownReason.NO_RESPONSE) {
            // BLE timeout — moderate cooldown
            int recentTimeouts = countRecentCoolDowns(target, CoolDownReason.NO_RESPONSE, now.minusHours(6));
            int minutes = switch (recentTimeouts) {
                case 0 -> 15;
                case 1 -> 30;
                default -> 60;
            };
            end = now.plusMinutes(minutes);
            log.info("NO_RESPONSE cooldown for {} — timeout #{} → {} min", target, recentTimeouts + 1, minutes);
        }
        log.info("Creating cooldown for {} ending at {} reason {}",target,end,coolDownReason);
        ChargePointCoolDown chargePointCoolDown = ChargePointCoolDown.builder()
                .time(now)
                .target(target)
                .end(end)
                .reason(coolDownReason)
                .build();
        chargePointCooldownRepository.save(chargePointCoolDown);
    }

    private int countRecentCoolDowns(String target, CoolDownReason reason, LocalDateTime since) {
        return chargePointCooldownRepository.countByTargetAndReasonAndTimeAfter(target, reason, since);
    }
}
