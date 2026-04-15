package com.rose.solnax.process;

import com.rose.solnax.model.entity.ChargeSession;
import com.rose.solnax.model.entity.enums.ChargeSessionStatus;
import com.rose.solnax.model.repository.ChargeSessionRepository;
import com.rose.solnax.process.adapters.chargepoints.tesla.TeslaBLEAdapter;
import com.rose.solnax.process.adapters.chargepoints.tesla.model.VehicleApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChargeSessionManager {

    private final ChargeSessionRepository chargeSessionRepository;
    private final TeslaBLEAdapter bleAdapter;

    /**
     * Start a new charge session for the given VIN.
     * Reads the current energy_added from the car to establish a baseline.
     */
    @Transactional
    public ChargeSession startSession(String vin, VehicleApiResponse vehicleData) {
        // Check if there's already an active session for this VIN
        Optional<ChargeSession> existing = chargeSessionRepository.findByVinAndStatus(vin, ChargeSessionStatus.ACTIVE);
        if (existing.isPresent()) {
            log.info("Active session already exists for VIN {}", vin);
            return existing.get();
        }

        double energyStart = 0.0;
        try {
            if (vehicleData != null && vehicleData.getResponse() != null
                    && vehicleData.getResponse().getResponse() != null
                    && vehicleData.getResponse().getResponse().getCharge_state() != null) {
                energyStart = vehicleData.getResponse().getResponse().getCharge_state().getCharge_energy_added();
            }
        } catch (Exception e) {
            log.warn("Could not read energy_added for session start of {}", vin);
        }

        ChargeSession session = ChargeSession.builder()
                .vin(vin)
                .startedAt(LocalDateTime.now())
                .energyStartKwh(energyStart)
                .status(ChargeSessionStatus.ACTIVE)
                .build();

        session = chargeSessionRepository.save(session);
        log.info("Started charge session for VIN {}: {}", vin, session.getId());
        return session;
    }

    /**
     * End an active charge session for the given VIN.
     * Reads energy_added to compute total charged.
     */
    @Transactional
    public void endSession(String vin, VehicleApiResponse vehicleData) {
        Optional<ChargeSession> activeOpt = chargeSessionRepository.findByVinAndStatus(vin, ChargeSessionStatus.ACTIVE);
        if (activeOpt.isEmpty()) {
            log.info("No active session found for VIN {} to end", vin);
            return;
        }

        ChargeSession session = activeOpt.get();
        session.setEndedAt(LocalDateTime.now());
        session.setStatus(ChargeSessionStatus.COMPLETED);

        try {
            if (vehicleData != null && vehicleData.getResponse() != null
                    && vehicleData.getResponse().getResponse() != null
                    && vehicleData.getResponse().getResponse().getCharge_state() != null) {
                double energyEnd = vehicleData.getResponse().getResponse().getCharge_state().getCharge_energy_added();
                session.setEnergyEndKwh(energyEnd);
                double charged = energyEnd - (session.getEnergyStartKwh() != null ? session.getEnergyStartKwh() : 0.0);
                session.setEnergyChargedKwh(Math.max(charged, 0.0));
            }
        } catch (Exception e) {
            log.warn("Could not read energy_added for session end of {}", vin);
        }

        chargeSessionRepository.save(session);
        log.info("Ended charge session for VIN {}: charged {}kWh", vin, session.getEnergyChargedKwh());
    }

    /**
     * Update the amps set on an active session.
     */
    @Transactional
    public void updateSessionAmps(String vin, int amps) {
        chargeSessionRepository.findByVinAndStatus(vin, ChargeSessionStatus.ACTIVE)
                .ifPresent(session -> {
                    session.setAmpsSet(amps);
                    chargeSessionRepository.save(session);
                });
    }

    /**
     * Abort any active sessions for a VIN (e.g., car disconnected mid-charge).
     */
    @Transactional
    public void abortSession(String vin) {
        chargeSessionRepository.findByVinAndStatus(vin, ChargeSessionStatus.ACTIVE)
                .ifPresent(session -> {
                    session.setEndedAt(LocalDateTime.now());
                    session.setStatus(ChargeSessionStatus.ABORTED);
                    chargeSessionRepository.save(session);
                    log.info("Aborted charge session for VIN {}", vin);
                });
    }

    @Transactional(readOnly = true)
    public List<ChargeSession> getActiveSessions() {
        return chargeSessionRepository.findByStatus(ChargeSessionStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<ChargeSession> getRecentSessions() {
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        return chargeSessionRepository.findByStartedAtBetweenOrderByStartedAtDesc(weekAgo, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<ChargeSession> getAllSessions() {
        return chargeSessionRepository.findAllByOrderByStartedAtDesc();
    }
}

