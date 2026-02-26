package com.rose.solnax.model.entity;

import com.rose.solnax.model.entity.enums.CoolDownReason;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargePointCoolDown {

    @Id
    private LocalDateTime time;

    @Column(name="target",nullable = false)
    private String target;

    @Column(name="ends_at",nullable = false)
    private LocalDateTime end;


    @Enumerated(EnumType.STRING)
    @Column(name="reason",nullable = false)
    private CoolDownReason reason;
}
