package com.rose.solnax.process;


import com.rose.solnax.model.entity.ChargePointCoolDown;
import com.rose.solnax.model.entity.enums.CoolDownReason;
import com.rose.solnax.model.repository.ChargePointCooldownRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChargePointCoolDownManager {


    private final ChargePointCooldownRepository chargePointCooldownRepository;

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
            end = LocalDateTime.now().plusMinutes(60);
        } else if (coolDownReason == CoolDownReason.LOW_BATTERY) {
            end = LocalDateTime.now().plusMinutes(30);
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
}
