package com.rose.solnax.model.dto;

import com.rose.solnax.model.entity.ChargePointCoolDown;
import com.rose.solnax.model.entity.enums.CoolDownReason;
import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.time.LocalDateTime;

@Getter
@Builder
public class CoolDownStatusDTO {

    private String target;
    private CoolDownReason reason;
    private LocalDateTime endsAt;
    private long minutesRemaining;

    public static CoolDownStatusDTO from(ChargePointCoolDown coolDown) {
        long remaining = Duration.between(LocalDateTime.now(), coolDown.getEnd()).toMinutes();
        return CoolDownStatusDTO.builder()
                .target(coolDown.getTarget())
                .reason(coolDown.getReason())
                .endsAt(coolDown.getEnd())
                .minutesRemaining(Math.max(remaining, 0))
                .build();
    }
}

