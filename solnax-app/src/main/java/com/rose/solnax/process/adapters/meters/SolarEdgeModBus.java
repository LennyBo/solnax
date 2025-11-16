package com.rose.solnax.process.adapters.meters;

import com.rose.solnax.process.exception.UnableToReadException;
import com.serotonin.modbus4j.ModbusFactory;
import com.serotonin.modbus4j.exception.ModbusInitException;
import com.serotonin.modbus4j.exception.ModbusTransportException;
import com.serotonin.modbus4j.ip.IpParameters;
import com.serotonin.modbus4j.msg.ReadHoldingRegistersRequest;
import com.serotonin.modbus4j.msg.ReadHoldingRegistersResponse;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
public class SolarEdgeModBus implements IPowerMeter, DisposableBean {

    private final ModbusFactory modbusFactory = new ModbusFactory();
    private com.serotonin.modbus4j.ModbusMaster master;

    // Connection config
    @Value("${solaredge.modbus.host}")
    private String host;
    @Value("${solaredge.modbus.port:1502}")
    private int port;
    @Value("${solaredge.modbus.slaveId:1}")
    private int slaveId;
    @Value("${solaredge.modbus.connectTimeoutMs:3000}")
    private int connectTimeoutMs;

    // Registers (configurable)
    @Value("${solaredge.modbus.registers.gridPowerOffset}")
    private int gridPowerOffset;
    @Value("${solaredge.modbus.registers.sitePowerOffset}")
    private int sitePowerOffset;
    @Value("${solaredge.modbus.registers.gridPowerWords}")
    private int gridPowerWords;   // number of 16-bit registers (words)
    @Value("${solaredge.modbus.registers.sitePowerWords}")
    private int sitePowerWords;

    private final ReentrantLock connectionLock = new ReentrantLock();

    @PostConstruct
    public void init() {
        connect();
    }

    private void connect() {
        connectionLock.lock();
        try {
            if (master != null && master.isInitialized()) return;

            IpParameters params = new IpParameters();
            params.setHost(host);
            params.setPort(port);
            params.setEncapsulated(false);

            master = modbusFactory.createTcpMaster(params, true);
            master.setTimeout(connectTimeoutMs);
            master.setRetries(1);

            try {
                master.init();
                log.info("Connected to SolarEdge Modbus TCP {}:{}", host, port);
            } catch (ModbusInitException e) {
                log.error("Failed to init Modbus master: {}", e.getMessage());
                // leave master uninitialized; reconnect attempts will be made on read
                if (master != null) {
                    try { master.destroy(); } catch (Exception ignored) {}
                }
                master = null;
            }
        } finally {
            connectionLock.unlock();
        }
    }

    private void ensureConnected() {
        if (master == null || !master.isInitialized()) {
            connect();
            if (master == null || !master.isInitialized()) {
                throw new IllegalStateException("Modbus master not initialized");
            }
        }
    }

    /**
     * Read n 16-bit registers starting from offset (SolarEdge documentation addresses).
     * Combines into a byte[] and return raw bytes in Big-Endian word order (Modbus network order).
     */
    private byte[] readRegistersRaw(int offset, int wordCount) throws ModbusTransportException {
        ensureConnected();
        // Modbus4j expects zero-based offset relative to register addressing depending on device mapping.
        ReadHoldingRegistersRequest request = new ReadHoldingRegistersRequest(slaveId, offset, wordCount);
        ReadHoldingRegistersResponse response = (ReadHoldingRegistersResponse) master.send(request);
        if (response == null || response.isException()) {
            throw new ModbusTransportException("Error reading registers: " + (response == null ? "null" : response.getExceptionMessage()));
        }
        short[] data = response.getShortData(); // array of signed 16-bit values
        ByteBuffer bb = ByteBuffer.allocate(data.length * 2);
        // Modbus returns big-endian per register; we'll put each short as big-endian
        bb.order(ByteOrder.BIG_ENDIAN);
        for (short s : data) bb.putShort(s);
        return bb.array();
    }

    /**
     * Convert raw register bytes to signed 32-bit int (big-endian) or signed 64-bit
     * If device uses different endianess or swapped words, adapt here.
     */
    private long parseSignedInt(byte[] raw, int bytes) {
        ByteBuffer bb = ByteBuffer.wrap(raw, 0, bytes);
        bb.order(ByteOrder.BIG_ENDIAN);
        if (bytes == 4) return bb.getInt();
        if (bytes == 8) return bb.getLong();
        throw new IllegalArgumentException("Unsupported byte length: " + bytes);
    }

    /**
     * Public: grid power in Watts (positive export or positive import depending on device's sign convention).
     * The address and number of words are configurable in application.yml.
     */
    public long readGridPowerWatts() {
        try {
            byte[] raw = readRegistersRaw(gridPowerOffset, gridPowerWords);
            return parseSignedInt(raw, gridPowerWords * 2);
        } catch (Exception e) {
            throw new UnableToReadException("Unable to read grid power : " + e);
        }
    }

    public long readSitePowerWatts() {
        try {
            byte[] raw = readRegistersRaw(sitePowerOffset, sitePowerWords);
            return parseSignedInt(raw, sitePowerWords * 2);
        } catch (Exception e) {
            throw new UnableToReadException("Unable to read solar power : " + e);
        }
    }

    @Override
    public void destroy() {
        if (master != null) {
            try {
                master.destroy();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public Double gridMeter() {
        return readGridPowerWatts() + 0.0;
    }

    @Override
    public Double solarMeter() {
        return readSitePowerWatts() + 0.0;
    }
}
