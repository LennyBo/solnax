package com.rose.solnax.model.dto;

import com.rose.solnax.model.entity.ChargePointCoolDown;
import com.rose.solnax.model.entity.enums.ChargeControlMode;
import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.time.LocalDateTime;

@Getter
@Builder
public class ChargeControlModeDTO {

    private ChargeControlMode mode;
    private LocalDateTime endsAt;
    private long minutesRemaining;

    public static ChargeControlModeDTO normal() {
        return ChargeControlModeDTO.builder()
                .mode(ChargeControlMode.NORMAL)
                .minutesRemaining(0)
                .build();
    }

    public static ChargeControlModeDTO from(ChargePointCoolDown coolDown) {
        long remaining = Duration.between(LocalDateTime.now(), coolDown.getEnd()).toMinutes();
        return ChargeControlModeDTO.builder()
                .mode(ChargeControlMode.fromReason(coolDown.getReason()))
                .endsAt(coolDown.getEnd())
                .minutesRemaining(Math.max(remaining, 0))
                .build();
    }
}
