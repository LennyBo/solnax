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

/**
 * Robust SolarEdge Modbus TCP adapter using Modbus4J.
 * - Accepts SolarEdge-style register numbers (40001..). Converts to zero-based offsets.
 * - Supports multi-word reads, signed/unsigned, and scale registers (10^scale).
 * - Reconnects automatically when needed.
 */
@Service
@Slf4j
public class SolarEdgeModBus implements IPowerMeter, DisposableBean {

    private final ModbusFactory modbusFactory = new ModbusFactory();
    private com.serotonin.modbus4j.ModbusMaster master;

    @Value("${solaredge.modbus.host}")
    private String host;
    @Value("${solaredge.modbus.port:1502}")
    private int port;
    @Value("${solaredge.modbus.slaveId:1}")
    private int slaveId;
    @Value("${solaredge.modbus.connectTimeoutMs:3000}")
    private int connectTimeoutMs;
    @Value("${solaredge.modbus.retries:1}")
    private int retries;

    // Your configurable registers (SolarEdge 400xx numbers). Example defaults can be overridden in YAML.
    @Value("${solaredge.modbus.registers.gridPowerOffset:40077}")
    private int gridPowerOffset;      // SolarEdge register number (e.g. 40077 for AC power)
    @Value("${solaredge.modbus.registers.gridPowerWords:1}")
    private int gridPowerWords;       // number of 16-bit registers (words)
    @Value("${solaredge.modbus.registers.gridPowerSigned:true}")
    private boolean gridPowerSigned;  // whether the raw value is signed

    @Value("${solaredge.modbus.registers.sitePowerOffset:40085}")
    private int sitePowerOffset;
    @Value("${solaredge.modbus.registers.sitePowerWords:1}")
    private int sitePowerWords;
    @Value("${solaredge.modbus.registers.sitePowerSigned:true}")
    private boolean sitePowerSigned;

    // Common scale registers (use SolarEdge scale register numbers). 0 means "no scale".
    @Value("${solaredge.modbus.scales.powerScale:40106}")
    private int powerScaleRegister; // applies to AC power, etc. (SolarEdge convention)

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
            master.setRetries(retries);

            try {
                master.init();
                log.info("Connected to SolarEdge Modbus TCP {}:{}", host, port);
            } catch (ModbusInitException e) {
                log.error("Failed to init Modbus master: {}", e.getMessage());
                // best-effort cleanup
                try { master.destroy(); } catch (Exception ignored) {}
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

    private int toZeroBased(int solarEdgeRegister) {
        if (solarEdgeRegister >= 40001 && solarEdgeRegister < 50000)
            return solarEdgeRegister - 40001;
        if (solarEdgeRegister >= 30001 && solarEdgeRegister < 40000)
            return solarEdgeRegister - 30001;
        return solarEdgeRegister;
    }

    /**
     * Read holding registers starting from a SolarEdge-style register number (e.g. 40077).
     * Returns raw short[] (each element is signed Java short representing the 16-bit word).
     */
    private short[] readHoldingRegistersRaw(int solarEdgeRegister, int wordCount) throws ModbusTransportException {
        ensureConnected();
        int offset = toZeroBased(solarEdgeRegister);
        ReadHoldingRegistersRequest request = new ReadHoldingRegistersRequest(slaveId, offset, wordCount);
        ReadHoldingRegistersResponse response = (ReadHoldingRegistersResponse) master.send(request);
        if (response == null) {
            throw new ModbusTransportException("Null response reading registers at " + solarEdgeRegister);
        }
        if (response.isException()) {
            throw new ModbusTransportException("Exception reading registers at " + solarEdgeRegister + ": " + response.getExceptionMessage());
        }
        return response.getShortData();
    }

    /**
     * Read a scale register (signed 16-bit exponent). Returns null if scaleRegister==0.
     */
    private Integer readScale(int scaleRegister) {
        if (scaleRegister <= 0) return null;
        try {
            short[] s = readHoldingRegistersRaw(scaleRegister, 1);
            // treat as signed 16-bit
            return (int) s[0];
        } catch (Exception e) {
            log.warn("Failed to read scale register {}: {}", scaleRegister, e.getMessage());
            return null;
        }
    }

    /**
     * Combine short[] into unsigned long / signed long depending on words and signed flag.
     * Assumes network (big-endian) word order from Modbus: regs[0] high word.
     */
    private long combineWords(short[] regs, boolean signed) {
        // Build big-endian byte buffer
        ByteBuffer bb = ByteBuffer.allocate(regs.length * 2);
        bb.order(ByteOrder.BIG_ENDIAN);
        for (short w : regs) bb.putShort(w);
        bb.flip();

        if (regs.length == 1) {
            int unsigned = bb.getShort() & 0xFFFF;
            if (signed) return (short) unsigned;
            return unsigned;
        } else if (regs.length == 2) {
            long unsigned = ((long) bb.getInt()) & 0xFFFFFFFFL;
            if (signed) return bb.getInt();
            return unsigned;
        } else if (regs.length == 4) {
            // 64-bit
            long val = bb.getLong();
            return signed ? val : val; // Java long is signed, but treat as bit-pattern for unsigned if needed
        } else {
            // generic combining for N words: accumulate as unsigned into long (may overflow)
            long acc = 0;
            for (short w : regs) {
                acc = (acc << 16) | (w & 0xFFFFL);
            }
            if (signed) {
                // if top bit (bit length) is set, interpret as negative two's complement
                int bits = regs.length * 16;
                long signMask = 1L << (bits - 1);
                if ((acc & signMask) != 0) {
                    long mask = (1L << bits) - 1;
                    long twos = acc & mask;
                    long signedVal = twos - (1L << bits);
                    return signedVal;
                }
            }
            return acc;
        }
    }

    /**
     * Read a value from solarEdgeRegister with wordCount, apply optional scaleRegister, and return double.
     */
    public double readScaledDouble(int solarEdgeRegister, int wordCount, boolean signed, int scaleRegister) {
        try {
            short[] raw = readHoldingRegistersRaw(solarEdgeRegister, wordCount);
            long combined = combineWords(raw, signed);
            Integer scaleExp = readScale(scaleRegister);
            double scale = 1.0;
            if (scaleExp != null) scale = Math.pow(10.0, scaleExp);
            return combined * scale;
        } catch (ModbusTransportException e) {
            // attempt reconnect once and retry
            log.warn("Read failed at {} (attempting reconnect): {}", solarEdgeRegister, e.getMessage());
            try {
                // reconnect and retry once
                connect();
                short[] raw = readHoldingRegistersRaw(solarEdgeRegister, wordCount);
                long combined = combineWords(raw, signed);
                Integer scaleExp = readScale(scaleRegister);
                double scale = 1.0;
                if (scaleExp != null) scale = Math.pow(10.0, scaleExp);
                return combined * scale;
            } catch (Exception ex) {
                throw new UnableToReadException("Unable to read register " + solarEdgeRegister + " after reconnect: " + ex.getMessage());
            }
        } catch (Exception e) {
            throw new UnableToReadException("Unable to read register " + solarEdgeRegister + ": " + e.getMessage());
        }
    }

    /**
     * Grid power (Watts) - uses config values and power scale by default.
     */
    public double readGridPowerWatts() {
        return readScaledDouble(gridPowerOffset, gridPowerWords, gridPowerSigned, powerScaleRegister);
    }

    public double readSitePowerWatts() {
        return readScaledDouble(sitePowerOffset, sitePowerWords, sitePowerSigned, powerScaleRegister);
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
        return readGridPowerWatts();
    }

    @Override
    public Double solarMeter() {
        return readSitePowerWatts();
    }
}
