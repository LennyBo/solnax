package com.rose.solnax.process.adapters.meters;

import com.rose.solnax.process.exception.UnableToReadException;
import com.serotonin.modbus4j.ModbusFactory;
import com.serotonin.modbus4j.exception.ModbusInitException;
import com.serotonin.modbus4j.exception.ModbusTransportException;
import com.serotonin.modbus4j.ip.IpParameters;
import com.serotonin.modbus4j.msg.ReadInputRegistersRequest;
import com.serotonin.modbus4j.msg.ReadInputRegistersResponse;
import com.serotonin.modbus4j.msg.ReadHoldingRegistersRequest;
import com.serotonin.modbus4j.msg.ReadHoldingRegistersResponse;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

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

    @Value("${solaredge.modbus.registers.gridPowerOffset:40077}")
    private int gridPowerOffset;
    @Value("${solaredge.modbus.registers.gridPowerWords:1}")
    private int gridPowerWords;
    @Value("${solaredge.modbus.registers.gridPowerSigned:true}")
    private boolean gridPowerSigned;

    @Value("${solaredge.modbus.registers.sitePowerOffset:40085}")
    private int sitePowerOffset;
    @Value("${solaredge.modbus.registers.sitePowerWords:1}")
    private int sitePowerWords;
    @Value("${solaredge.modbus.registers.sitePowerSigned:true}")
    private boolean sitePowerSigned;

    @Value("${solaredge.modbus.scales.powerScale:40106}")
    private int powerScaleRegister;

    private final ReentrantLock connectionLock = new ReentrantLock();

    @PostConstruct
    public void init() { connect(); }

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
            } catch (ModbusInitException ex) {
                log.error("Modbus init failed: {}", ex.getMessage());
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
        if (solarEdgeRegister >= 40001 && solarEdgeRegister < 50000) return solarEdgeRegister - 40001;
        if (solarEdgeRegister >= 30001 && solarEdgeRegister < 40000) return solarEdgeRegister - 30001;
        return solarEdgeRegister;
    }

    private short[] readHoldingRaw(int solarEdgeRegister, int words) throws ModbusTransportException {
        ensureConnected();
        int offset = toZeroBased(solarEdgeRegister);
        ReadHoldingRegistersRequest req = new ReadHoldingRegistersRequest(slaveId, offset, words);
        ReadHoldingRegistersResponse resp = (ReadHoldingRegistersResponse) master.send(req);
        if (resp == null) throw new ModbusTransportException("Null response (holding) at " + solarEdgeRegister);
        if (resp.isException()) throw new ModbusTransportException("Exception (holding) at " + solarEdgeRegister + ": " + resp.getExceptionMessage());
        return resp.getShortData();
    }

    private short[] readInputRaw(int solarEdgeRegister, int words) throws ModbusTransportException {
        ensureConnected();
        int offset = toZeroBased(solarEdgeRegister);
        ReadInputRegistersRequest req = new ReadInputRegistersRequest(slaveId, offset, words);
        ReadInputRegistersResponse resp = (ReadInputRegistersResponse) master.send(req);
        if (resp == null) throw new ModbusTransportException("Null response (input) at " + solarEdgeRegister);
        if (resp.isException()) throw new ModbusTransportException("Exception (input) at " + solarEdgeRegister + ": " + resp.getExceptionMessage());
        return resp.getShortData();
    }

    private Integer readScale(int scaleRegister) {
        if (scaleRegister <= 0) return null;
        try {
            short[] r = readInputRaw(scaleRegister, 1);
            log.debug("Scale register {} raw words={}", scaleRegister, Arrays.toString(r));
            if (r[0] == Short.MIN_VALUE) return null;
            return (int) r[0];
        } catch (Exception e) {
            log.warn("Failed to read scale register {}: {}", scaleRegister, e.getMessage());
            return null;
        }
    }

    private long combineBEUnsigned(short[] regs) {
        long acc = 0;
        for (short s : regs) acc = (acc << 16) | (s & 0xFFFFL);
        return acc;
    }

    private long combineBESigned(short[] regs) {
        int bits = regs.length * 16;
        long unsigned = combineBEUnsigned(regs);
        long signBit = 1L << (bits - 1);
        if ((unsigned & signBit) != 0) {
            long mask = (1L << bits) - 1;
            long twos = unsigned & mask;
            return twos - (1L << bits);
        } else return unsigned;
    }

    private short[] swapWords(short[] regs) {
        short[] s = new short[regs.length];
        for (int i = 0; i < regs.length; i++) s[i] = regs[regs.length - 1 - i];
        return s;
    }

    private double robustReadScaled(int solarEdgeRegister, int words, boolean signed, int scaleRegister) {
        try {
            // first try input registers
            short[] raw = readInputRaw(solarEdgeRegister, words);
            log.debug("Input read @{} words={} raw={}", solarEdgeRegister, words, Arrays.toString(raw));
            double val = interpretAndScale(raw, signed, scaleRegister, solarEdgeRegister, "input");
            if (!Double.isNaN(val)) return val;

            // fallback to holding registers if needed
            short[] rawHold = readHoldingRaw(solarEdgeRegister, words);
            log.debug("Holding read @{} words={} raw={}", solarEdgeRegister, words, Arrays.toString(rawHold));
            val = interpretAndScale(rawHold, signed, scaleRegister, solarEdgeRegister, "holding");
            if (!Double.isNaN(val)) return val;

            throw new UnableToReadException("All reads returned zero/invalid at register " + solarEdgeRegister);
        } catch (ModbusTransportException e) {
            log.warn("Transport exception reading register {}: {}", solarEdgeRegister, e.getMessage());
            try { connect(); } catch (Exception ignored) {}
            try {
                short[] rawRetry = readInputRaw(solarEdgeRegister, words);
                log.debug("Retry input raw={}", Arrays.toString(rawRetry));
                double val = interpretAndScale(rawRetry, signed, scaleRegister, solarEdgeRegister, "input(retry)");
                if (!Double.isNaN(val)) return val;
            } catch (Exception ex) {
                throw new UnableToReadException("Unable to read after reconnect: " + ex.getMessage());
            }
            throw new UnableToReadException("Transport exception: " + e.getMessage());
        }
    }

    private double interpretAndScale(short[] raw, boolean signed, int scaleRegister, int reg, String mode) {
        boolean allZero = true;
        for (short s : raw) if ((s & 0xFFFF) != 0) { allZero = false; break; }

        Integer scaleExp = readScale(scaleRegister);
        double scale = scaleExp == null ? 1.0 : Math.pow(10.0, scaleExp);
        log.debug("Scale register {} => exponent {} -> scale {}", scaleRegister, scaleExp, scale);

        if (signed) {
            long sVal = combineBESigned(raw);
            log.debug("Interpreted signed BE value={} (raw={})", sVal, Arrays.toString(raw));
            if (sVal != 0 || !allZero) return sVal * scale;
            long sValSw = combineBESigned(swapWords(raw));
            log.debug("Interpreted signed BE-swapped value={} (swapped={})", sValSw, Arrays.toString(swapWords(raw)));
            if (sValSw != 0) return sValSw * scale;
        }

        long uVal = combineBEUnsigned(raw);
        log.debug("Interpreted unsigned BE value={} (raw={})", uVal, Arrays.toString(raw));
        if (uVal != 0 || !allZero) return uVal * scale;
        long uValSw = combineBEUnsigned(swapWords(raw));
        log.debug("Interpreted unsigned BE-swapped value={} (swapped={})", uValSw, Arrays.toString(swapWords(raw)));
        if (uValSw != 0) return uValSw * scale;

        return Double.NaN;
    }

    public double readGridPowerWatts() {
        return robustReadScaled(gridPowerOffset, gridPowerWords, gridPowerSigned, powerScaleRegister);
    }

    public double readSitePowerWatts() {
        return robustReadScaled(sitePowerOffset, sitePowerWords, sitePowerSigned, powerScaleRegister);
    }

    @Override
    public void destroy() {
        if (master != null) {
            try { master.destroy(); } catch (Exception ignored) {}
        }
    }

    @Override
    public Double gridMeter() { return readGridPowerWatts(); }

    @Override
    public Double solarMeter() { return readSitePowerWatts(); }
}
