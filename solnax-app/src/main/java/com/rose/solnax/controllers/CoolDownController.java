package com.rose.solnax.controllers;

import com.rose.solnax.model.dto.ChargeControlModeDTO;
import com.rose.solnax.model.dto.CoolDownStatusDTO;
import com.rose.solnax.model.entity.enums.ChargeControlMode;
import com.rose.solnax.process.ChargePointCoolDownManager;
import com.rose.solnax.process.exception.CoolDownAlreadyCreated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequiredArgsConstructor
@Slf4j
public class CoolDownController {

    private final ChargePointCoolDownManager chargePointCoolDownManager;

    // ─── Charge control mode (NORMAL / ECO_PLUS / MANUAL) ───────────────

    @GetMapping("/api/cool-down/mode")
    public ResponseEntity<ChargeControlModeDTO> getMode() {
        try {
            return ResponseEntity.ok(chargePointCoolDownManager.getActiveModeCoolDown()
                    .map(ChargeControlModeDTO::from)
                    .orElseGet(ChargeControlModeDTO::normal));
        } catch (Exception e) {
            log.error("Failed to read the charge control mode", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/api/cool-down/mode/{mode}")
    public ResponseEntity<ChargeControlModeDTO> activateMode(@PathVariable("mode") ChargeControlMode mode) {
        try {
            chargePointCoolDownManager.activateMode(mode);
            return ResponseEntity.ok(chargePointCoolDownManager.getActiveModeCoolDown()
                    .map(ChargeControlModeDTO::from)
                    .orElseGet(ChargeControlModeDTO::normal));
        } catch (CoolDownAlreadyCreated e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (Exception e) {
            log.error("Failed to activate mode {}", mode, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Back to NORMAL: only removes the mode cool downs, vehicle cool downs stay untouched.
     */
    @DeleteMapping("/api/cool-down/mode")
    public ResponseEntity<Void> clearMode() {
        try {
            chargePointCoolDownManager.clearChargeControlMode();
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to clear the charge control mode", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ─── Cool downs ─────────────────────────────────────────────────────

    @PostMapping("/api/cool-down/manual")
    public ResponseEntity<Boolean> createManualCoolDown() {
        try {
            chargePointCoolDownManager.activateMode(ChargeControlMode.MANUAL);
            return ResponseEntity.ok(true);
        } catch (CoolDownAlreadyCreated e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(false);
        } catch (Exception e) {
            log.error("Failed to activate the manual cool down", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(false);
        }
    }

    @GetMapping("/api/cool-down/manual")
    public ResponseEntity<Boolean> isManualCoolDownActive() {
        try {
            return ResponseEntity.ok(chargePointCoolDownManager.hasActiveManualCoolDown());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(false);
        }
    }

    /**
     * Clears every active cool down, including the current mode.
     */
    @DeleteMapping("/api/cool-down/manual")
    public ResponseEntity<Void> clearCoolDowns() {
        try {
            chargePointCoolDownManager.clearCoolDowns();
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to clear the cool downs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/api/cool-down/status")
    public ResponseEntity<List<CoolDownStatusDTO>> getCoolDownStatus() {
        try {
            List<CoolDownStatusDTO> statuses = chargePointCoolDownManager.getActiveCoolDowns().stream()
                    .map(CoolDownStatusDTO::from)
                    .toList();
            return ResponseEntity.ok(statuses);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
