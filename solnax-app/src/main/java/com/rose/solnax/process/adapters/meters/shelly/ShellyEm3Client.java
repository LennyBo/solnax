package com.rose.solnax.process.adapters.meters.shelly;

import com.rose.solnax.process.adapters.meters.shelly.dto.ShellyEm3Status;
import com.rose.solnax.process.adapters.meters.shelly.dto.ShellyEm3Status.*;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

/**
 * HTTP client for a single Shelly EM3 device.
 * Instantiated once per configured device by {@link ShellyEm3Registry}.
 */
public class ShellyEm3Client {

    private static final Logger log = LoggerFactory.getLogger(ShellyEm3Client.class);

    @Getter
    private final String deviceId;
    private final ShellyEm3Properties.Device device;
    private final RestTemplate rest;

    public ShellyEm3Client(String deviceId, ShellyEm3Properties.Device device, RestTemplate rest) {
        this.deviceId = deviceId;
        this.device   = device;
        this.rest     = rest;
    }

    // -------------------------------------------------------------------------
    // Raw API calls
    // -------------------------------------------------------------------------

    public ShellyEm3Status getStatus() {
        return get("/status", ShellyEm3Status.class);
    }

    public ShellyEm3Status.EmeterChannel getPhaseL1() { return getEmeter(0); }
    public ShellyEm3Status.EmeterChannel getPhaseL2() { return getEmeter(1); }
    public ShellyEm3Status.EmeterChannel getPhaseL3() { return getEmeter(2); }

    public ShellyEm3Status.EmeterChannel getEmeter(int phaseIndex) {
        validatePhase(phaseIndex);
        return get("/emeter/" + phaseIndex, ShellyEm3Status.EmeterChannel.class);
    }

    // -------------------------------------------------------------------------
    // Aggregates
    // -------------------------------------------------------------------------

    public List<EmeterChannel> getAllPhases() {
        List<EmeterChannel> channels = getStatus().emeters();
        if (channels == null || channels.size() < 3) {
            throw new ShellyEm3Exception(deviceId + ": expected 3 emeter channels, got "
                    + (channels == null ? "null" : channels.size()));
        }
        return channels;
    }

    public Optional<EmeterChannel> getPhaseIfValid(int phaseIndex) {
        List<EmeterChannel> phases = getAllPhases();
        if (phaseIndex < 0 || phaseIndex >= phases.size()) return Optional.empty();
        EmeterChannel ch = phases.get(phaseIndex);
        return ch.valid() ? Optional.of(ch) : Optional.empty();
    }

    public double getTotalActivePowerW() {
        return getAllPhases().stream()
                .filter(EmeterChannel::valid)
                .mapToDouble(EmeterChannel::powerW)
                .sum();
    }

    public double getTotalEnergyWh() {
        return getAllPhases().stream()
                .filter(EmeterChannel::valid)
                .mapToDouble(EmeterChannel::totalEnergyWh)
                .sum();
    }

    public double getTotalEnergyKWh()          { return getTotalEnergyWh() / 1000.0; }

    public double getTotalReturnedEnergyWh() {
        return getAllPhases().stream()
                .filter(EmeterChannel::valid)
                .mapToDouble(EmeterChannel::totalReturnedWh)
                .sum();
    }

    // -------------------------------------------------------------------------
    // Control
    // -------------------------------------------------------------------------

    public void resetEnergyCounter(int phaseIndex) {
        validatePhase(phaseIndex);
        String url = device.baseUrl() + "/emeter/" + phaseIndex + "?reset=true";
        log.info("[{}] Resetting energy counter for phase {}", deviceId, phaseIndex);
        try {
            rest.getForObject(url, String.class);
        } catch (RestClientException ex) {
            throw new ShellyEm3Exception(deviceId + ": reset failed for phase " + phaseIndex, ex);
        }
    }

    // -------------------------------------------------------------------------

    private <T> T get(String path, Class<T> type) {
        String url = device.baseUrl() + path;
        log.debug("[{}] GET {}", deviceId, url);
        try {
            T body = rest.getForObject(url, type);
            if (body == null) throw new ShellyEm3Exception(deviceId + ": empty response from " + url);
            return body;
        } catch (RestClientException ex) {
            throw new ShellyEm3Exception(deviceId + ": HTTP call failed – " + url, ex);
        }
    }

    private static void validatePhase(int index) {
        if (index < 0 || index > 2)
            throw new IllegalArgumentException("Phase index must be 0, 1 or 2 – got: " + index);
    }
}