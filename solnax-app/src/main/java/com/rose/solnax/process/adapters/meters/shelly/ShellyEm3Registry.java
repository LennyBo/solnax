package com.rose.solnax.process.adapters.meters.shelly;

import com.rose.solnax.process.adapters.meters.shelly.dto.ShellyEm3Status;
import com.rose.solnax.process.adapters.meters.shelly.dto.ShellyEm3Status.*;
import  com.rose.solnax.process.adapters.meters.shelly.ShellyEm3Properties.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Central registry: builds one {@link ShellyEm3Client} per configured device
 * and exposes lookup + cross-device aggregate methods.
 *
 * Inject this where you need to work with multiple devices.
 * Inject {@link ShellyEm3Client} directly (via {@link #get(String)}) for single-device operations.
 */
@Service
public class ShellyEm3Registry {

    private static final Logger log = LoggerFactory.getLogger(ShellyEm3Registry.class);

    private final Map<String, ShellyEm3Client> clients;

    public ShellyEm3Registry(ShellyEm3Properties props,
                             RestTemplateBuilder builder) {
        if (props.getDevices().isEmpty()) {
            log.warn("No Shelly EM3 devices configured under shelly.em3.devices");
        }

        clients = props.getDevices().entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        e -> buildClient(e.getKey(), e.getValue(), props, builder)
                ));

        log.info("Shelly EM3 registry initialised with {} device(s): {}",
                clients.size(), clients.keySet());
    }

    // -------------------------------------------------------------------------
    // Lookup
    // -------------------------------------------------------------------------

    /**
     * Returns the client for the given device ID.
     *
     * @throws ShellyEm3Exception if no device with that ID is configured
     */
    public ShellyEm3Client get(String deviceId) {
        ShellyEm3Client client = clients.get(deviceId);
        if (client == null) {
            throw new ShellyEm3Exception("Unknown device: '" + deviceId
                    + "'. Configured devices: " + clients.keySet());
        }
        return client;
    }

    /** Returns an unmodifiable view of all registered clients keyed by device ID. */
    public Map<String, ShellyEm3Client> getAll() {
        return clients;
    }

    /** Returns all configured device IDs. */
    public Set<String> getDeviceIds() {
        return clients.keySet();
    }

    /** Returns true when a device with this ID is configured. */
    public boolean contains(String deviceId) {
        return clients.containsKey(deviceId);
    }

    // -------------------------------------------------------------------------
    // Cross-device aggregates
    // -------------------------------------------------------------------------

    /**
     * Returns the combined active power in Watts across ALL devices and phases.
     */
    public double getCombinedActivePowerW() {
        return clients.values().stream()
                .mapToDouble(ShellyEm3Client::getTotalActivePowerW)
                .sum();
    }

    /**
     * Returns the combined accumulated energy in kWh across ALL devices.
     */
    public double getCombinedEnergyKWh() {
        return clients.values().stream()
                .mapToDouble(ShellyEm3Client::getTotalEnergyKWh)
                .sum();
    }

    /**
     * Returns a snapshot of active power per device ID.
     * Devices that fail to respond are skipped and logged.
     */
    public Map<String, Double> getActivePowerPerDevice() {
        Map<String, Double> result = new LinkedHashMap<>();
        clients.forEach((id, client) -> {
            try {
                result.put(id, client.getTotalActivePowerW());
            } catch (ShellyEm3Exception ex) {
                log.warn("Skipping device '{}' during power snapshot: {}", id, ex.getMessage());
            }
        });
        return Collections.unmodifiableMap(result);
    }

    /**
     * Returns all valid emeter channels across every device, tagged with their device ID.
     */
    public List<TaggedChannel> getAllChannels() {
        List<TaggedChannel> result = new ArrayList<>();
        clients.forEach((id, client) -> {
            try {
                List<ShellyEm3Status.EmeterChannel> phases = client.getAllPhases();
                for (int i = 0; i < phases.size(); i++) {
                    ShellyEm3Status.EmeterChannel ch = phases.get(i);
                    if (ch.valid()) result.add(new TaggedChannel(id, i, ch));
                }
            } catch (ShellyEm3Exception ex) {
                log.warn("Skipping device '{}' during channel sweep: {}", id, ex.getMessage());
            }
        });
        return Collections.unmodifiableList(result);
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    private static ShellyEm3Client buildClient(String id, Device device,
                                               ShellyEm3Properties props,
                                               RestTemplateBuilder builder) {
        Duration connectTimeout = Optional.ofNullable(device.getConnectTimeout())
                .orElse(props.getConnectTimeout());
        Duration readTimeout = Optional.ofNullable(device.getReadTimeout())
                .orElse(props.getReadTimeout());

        RestTemplateBuilder b = builder
                .connectTimeout(connectTimeout)
                .readTimeout(readTimeout);

        if (StringUtils.hasText(device.getUsername())) {
            b = b.basicAuthentication(device.getUsername(), device.getPassword());
        }

        log.debug("Registering Shelly EM3 device '{}' → {}", id, device.baseUrl());
        return new ShellyEm3Client(id, device, b.build());
    }

    // -------------------------------------------------------------------------
    // Tagged channel record
    // -------------------------------------------------------------------------

    /**
     * An emeter channel annotated with the originating device and phase index.
     */
    public record TaggedChannel(String deviceId, int phaseIndex, EmeterChannel channel) {

        /** Convenience label, e.g. "solar/L2" */
        public String label() {
            return deviceId + "/L" + (phaseIndex + 1);
        }
    }
}