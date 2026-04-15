package com.rose.solnax.controllers;

import com.rose.solnax.model.dto.ChargeSessionDTO;
import com.rose.solnax.process.ChargeSessionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/charge-sessions")
@RequiredArgsConstructor
public class ChargeSessionController {

    private final ChargeSessionManager chargeSessionManager;

    @GetMapping
    public ResponseEntity<List<ChargeSessionDTO>> getAllSessions() {
        List<ChargeSessionDTO> sessions = chargeSessionManager.getAllSessions().stream()
                .map(ChargeSessionDTO::from)
                .toList();
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/recent")
    public ResponseEntity<List<ChargeSessionDTO>> getRecentSessions() {
        List<ChargeSessionDTO> sessions = chargeSessionManager.getRecentSessions().stream()
                .map(ChargeSessionDTO::from)
                .toList();
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/active")
    public ResponseEntity<List<ChargeSessionDTO>> getActiveSessions() {
        List<ChargeSessionDTO> sessions = chargeSessionManager.getActiveSessions().stream()
                .map(ChargeSessionDTO::from)
                .toList();
        return ResponseEntity.ok(sessions);
    }
}

