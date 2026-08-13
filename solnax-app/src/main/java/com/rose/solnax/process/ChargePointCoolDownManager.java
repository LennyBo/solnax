package com.rose.solnax.process;


import com.rose.solnax.model.entity.ChargePointCoolDown;
import com.rose.solnax.model.entity.enums.ChargeControlMode;
import com.rose.solnax.model.entity.enums.CoolDownReason;
import com.rose.solnax.model.repository.ChargePointCooldownRepository;
import com.rose.solnax.process.exception.CoolDownAlreadyCreated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChargePointCoolDownManager {

    /**
     * Mode cool downs are not bound to a single car, they steer the whole optimizer.
     */
    public static final String GLOBAL_TARGET = "ALL";

    private static final Set<CoolDownReason> MODE_REASONS = Arrays.stream(CoolDownReason.values())
            .filter(CoolDownReason::isMode)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(CoolDownReason.class)));

    private final ChargePointCooldownRepository chargePointCooldownRepository;

    // --- Optimizer modes (MANUAL / ECO_PLUS) ----------------------------

    /**
     * Activate a global optimizer mode until tomorrow morning.
     * Modes are mutually exclusive: activating one replaces the other.
     *
     * @return the moment the mode expires, or {@code null} when switching back to {@link ChargeControlMode#NORMAL}
     * @throws CoolDownAlreadyCreated when the requested mode is already active
     */
    @Transactional
    public LocalDateTime activateMode(ChargeControlMode mode) {
        if (mode == null || mode == ChargeControlMode.NORMAL) {
            clearChargeControlMode();
            return null;
        }

        ChargeControlMode current = getActiveChargeControlMode();
        if (current == mode) {
            throw new CoolDownAlreadyCreated("Already in " + mode + " mode");
        }

        // Modes are mutually exclusive - drop the previous one before activating the new one.
        clearChargeControlMode();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime end = LocalDate.now().plusDays(1).atTime(6, 0);

        chargePointCooldownRepository.save(
                ChargePointCoolDown.builder()
                        .time(now)
                        .target(GLOBAL_TARGET)
                        .end(end)
                        .reason(mode.getReason())
                        .build()
        );
        log.info("Activated {} mode until {}", mode, end);
        return end;
    }

    /**
     * @return the currently active optimizer mode. {@link ChargeControlMode#MANUAL} wins over
     * {@link ChargeControlMode#ECO_PLUS} should both somehow be present.
     */
    @Transactional(readOnly = true)
    public ChargeControlMode getActiveChargeControlMode() {
        return getActiveModeCoolDown()
                .map(coolDown -> ChargeControlMode.fromReason(coolDown.getReason()))
                .orElse(ChargeControlMode.NORMAL);
    }

    @Transactional(readOnly = true)
    public Optional<ChargePointCoolDown> getActiveModeCoolDown() {
        if (MODE_REASONS.isEmpty()) {
            return Optional.empty();
        }
        List<ChargePointCoolDown> modeCoolDowns =
                chargePointCooldownRepository.findAllByReasonInAndEndAfter(MODE_REASONS, LocalDateTime.now());

        return modeCoolDowns.stream()
                .filter(c -> c.getReason() == CoolDownReason.MANUAL)
                .findFirst()
                .or(() -> modeCoolDowns.stream().findFirst());
    }

    /**
     * Remove every active mode cool down, bringing the optimizer back to {@link ChargeControlMode#NORMAL}.
     * Vehicle scoped cool downs (FULL, NOT_CONNECTED, ...) are left untouched.
     */
    @Transactional
    public int clearChargeControlMode() {
        if (MODE_REASONS.isEmpty()) {
            return 0;
        }
        int i = chargePointCooldownRepository.deleteAllByReasonInAndEndAfter(MODE_REASONS, LocalDateTime.now());
        if (i > 0) {
            log.info("Cleared {} mode cooldowns", i);
        }
        return i;
    }

    @Transactional
    public void createCoolDownUntilTomorrow() {
        activateMode(ChargeControlMode.MANUAL);
    }

    @Transactional(readOnly = true)
    public boolean hasActiveManualCoolDown() {
        return chargePointCooldownRepository.existsByEndAfterAndReason(LocalDateTime.now(), CoolDownReason.MANUAL);
    }

    // --- Vehicle scoped cool downs --------------------------------------

    @Transactional
    public void clearCoolDowns() {
        int i = chargePointCooldownRepository.deleteAllByEndAfter(LocalDateTime.now());
        log.info("Cleared {} cooldowns", i);
    }

    @Transactional
    public void clearCoolDownsByReasonAndTarget(String target, CoolDownReason reason) {
        int i = chargePointCooldownRepository.deleteAllByTargetAndReasonAndEndAfter(target, reason, LocalDateTime.now());
        log.info("Cleared {} {} cooldowns for {}", i, reason, target);
    }

    @Transactional(readOnly = true)
    public List<ChargePointCoolDown> getActiveCoolDowns() {
        return chargePointCooldownRepository.findAllByEndAfter(LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public boolean hasActiveCoolDownForTarget(String target) {
        return chargePointCooldownRepository.existsByTargetAndEndAfter(target, LocalDateTime.now());
    }

    @Transactional
    public void coolDown(String target, CoolDownReason coolDownReason) {
        if (coolDownReason != null && coolDownReason.isMode()) {
            throw new IllegalArgumentException("Use activateMode(...) to create " + coolDownReason + " cool downs");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime end = now;
        if (coolDownReason == CoolDownReason.FULL) {
            end = now.plusHours(6);
        } else if (coolDownReason == CoolDownReason.NOT_CONNECTED) {
            end = now.plusMinutes(120);
        } else if (coolDownReason == CoolDownReason.LOW_BATTERY) {
            end = now.plusMinutes(240);
        } else if (coolDownReason == CoolDownReason.NO_RESPONSE) {
            // BLE timeout - moderate cooldown
            end = now.plusMinutes(60);
        }
        log.info("Creating cooldown for {} ending at {} reason {}", target, end, coolDownReason);
        ChargePointCoolDown chargePointCoolDown = ChargePointCoolDown.builder()
                .time(now)
                .target(target)
                .end(end)
                .reason(coolDownReason)
                .build();
        chargePointCooldownRepository.save(chargePointCoolDown);
    }
}
