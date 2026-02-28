package com.rose.solnax.controllers;

import com.rose.solnax.process.ChargePointCoolDownManager;
import com.rose.solnax.process.exception.CoolDownAlreadyCreated;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
public class CoolDownController {

    private final ChargePointCoolDownManager chargePointCoolDownManager;

    @PostMapping("/api/cool-down/manual")
    public ResponseEntity<Boolean> createManualCoolDown() {
        try {
            chargePointCoolDownManager.createCoolDownUntilTomorrow();
            return ResponseEntity.ok(true);
        } catch (CoolDownAlreadyCreated e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(false);
        } catch (Exception e) {
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

    @DeleteMapping("/api/cool-down/manual")
    public ResponseEntity<Void> clearCoolDowns() {
        try {
            chargePointCoolDownManager.clearCoolDowns();
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
