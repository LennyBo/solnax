// Paste into same package, replace your previous SolarEdgeModBus class.
// This is verbose by design: logs raw words, tries both register types, word-swap, signed/unsigned.
// Keep your YAML values (use SolarEdge 400xx addresses) — the code converts automatically.
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

    // Configured as SolarEdge register numbers (400xx). Keep them like that in YAML.
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

    // default common power scale register (SolarEdge convention). Set to 0 to disable scale read.
    @Value("${solaredge.modbus.scales.powerScale:40106}")
    private int powerScaleRegister;

    private final ReentrantLock connectionLock = new ReentrantLock();

    @PostConstruct
    public void init(){ connect(); }

    private void connect(){
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
            } catch (ModbusInitException ex){
                log.error("Modbus init failed: {}", ex.getMessage());
                try { master.destroy(); } catch (Exception ignored){}
                master = null;
            }
        } finally { connectionLock.unlock(); }
    }

    private void ensureConnected(){
        if (master == null || !master.isInitialized()){
            connect();
            if (master == null || !master.isInitialized()){
                throw new IllegalStateException("Modbus master not initialized");
            }
        }
    }

    // Convert SolarEdge 40001-style address to zero-based for modbus4j
    private int toZeroBased(int solarEdgeRegister){
        if (solarEdgeRegister >= 40001 && solarEdgeRegister < 50000) return solarEdgeRegister - 40001;
        if (solarEdgeRegister >= 30001 && solarEdgeRegister < 40000) return solarEdgeRegister - 30001;
        return solarEdgeRegister;
    }

    /* Low level read helpers for both holding (FC3) and input (FC4) */
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

    private Integer readScale(int scaleRegister){
        if (scaleRegister <= 0) return null;
        try {
            short[] r = readHoldingRaw(scaleRegister, 1);
            log.debug("Scale register {} raw words={}", scaleRegister, Arrays.toString(r));
            // scale stored as signed 16-bit exponent
            return (int) r[0];
        } catch (Exception e){
            log.warn("Failed to read scale register {}: {}", scaleRegister, e.getMessage());
            return null;
        }
    }

    /* utilities to combine words into numeric values */
    private long combineBEUnsigned(short[] regs){ // big-endian, unsigned
        long acc = 0;
        for (short s : regs) acc = (acc << 16) | (s & 0xFFFFL);
        return acc;
    }
    private long combineBESigned(short[] regs){ // big-endian, signed two's complement
        int bits = regs.length * 16;
        long unsigned = combineBEUnsigned(regs);
        long signBit = 1L << (bits - 1);
        if ((unsigned & signBit) != 0){
            long mask = (1L<<bits) - 1;
            long twos = unsigned & mask;
            return twos - (1L<<bits);
        } else return unsigned;
    }
    private short[] swapWords(short[] regs){ // swap word order (useful for word-swapped devices)
        short[] s = new short[regs.length];
        for (int i=0;i<regs.length;i++) s[i] = regs[regs.length-1-i];
        return s;
    }

    /**
     * Robust read that:
     * - reads holding registers; if result is all zeros, tries input registers
     * - logs raw words
     * - tries signed and unsigned, normal and word-swapped orders
     * - reads scale register and applies exponent scaling (value * 10^scale)
     */
    private double robustReadScaled(int solarEdgeRegister, int words, boolean signed, int scaleRegister) {
        try {
            // 1) attempt holding registers
            short[] raw = readHoldingRaw(solarEdgeRegister, words);
            log.debug("Holding read @{} words={} raw={}", solarEdgeRegister, words, Arrays.toString(raw));
            double val = interpretAndScale(raw, signed, scaleRegister, solarEdgeRegister, "holding");
            if (!Double.isNaN(val)) return val;

            // 2) attempt input registers
            short[] raw2 = readInputRaw(solarEdgeRegister, words);
            log.debug("Input read @{} words={} raw={}", solarEdgeRegister, words, Arrays.toString(raw2));
            val = interpretAndScale(raw2, signed, scaleRegister, solarEdgeRegister, "input");
            if (!Double.isNaN(val)) return val;

            // 3) nothing useful: throw (or return 0)
            throw new UnableToReadException("All reads returned zero/invalid at register " + solarEdgeRegister);
        } catch (ModbusTransportException e){
            log.warn("Transport exception reading register {}: {}", solarEdgeRegister, e.getMessage());
            // try reconnect once
            try { connect(); } catch (Exception ignored){}
            try {
                short[] raw = readHoldingRaw(solarEdgeRegister, words);
                log.debug("Retry holding raw={}", Arrays.toString(raw));
                double val = interpretAndScale(raw, signed, scaleRegister, solarEdgeRegister, "holding(retry)");
                if (!Double.isNaN(val)) return val;
            } catch (Exception ex){
                throw new UnableToReadException("Unable to read after reconnect: " + ex.getMessage());
            }
            throw new UnableToReadException("Transport exception: " + e.getMessage());
        } catch (UnableToReadException e){
            throw e;
        } catch (Exception e){
            throw new UnableToReadException("Unexpected: " + e.getMessage());
        }
    }

    private double interpretAndScale(short[] raw, boolean signed, int scaleRegister, int reg, String mode){
        boolean allZero = true;
        for (short s : raw) if ((s & 0xFFFF) != 0) { allZero = false; break; }
        if (allZero) {
            log.debug("{} read produced all-zero words at {}: {}. will try alternatives.", mode, reg, Arrays.toString(raw));
            // but continue to try word-swap and signed/unsigned to be thorough
        }

        // read scale (if present)
        Integer scaleExp = readScale(scaleRegister);
        double scale = scaleExp == null ? 1.0 : Math.pow(10.0, scaleExp);
        log.debug("Scale register {} => exponent {} -> scale {}", scaleRegister, scaleExp, scale);

        // Try interpretations in order that mimic common expectations:
        // 1) big-endian signed (if signed), 2) big-endian unsigned, 3) word-swapped signed, 4) word-swapped unsigned
        if (signed) {
            long sVal = combineBESigned(raw);
            log.debug("Interpreted signed BE value={} (raw={})", sVal, Arrays.toString(raw));
            if (sVal != 0 || !allZero) return sVal * scale;
            // try swapped
            long sValSw = combineBESigned(swapWords(raw));
            log.debug("Interpreted signed BE-swapped value={} (swapped={})", sValSw, Arrays.toString(swapWords(raw)));
            if (sValSw != 0) return sValSw * scale;
        }

        // unsigned attempts
        long uVal = combineBEUnsigned(raw);
        log.debug("Interpreted unsigned BE value={} (raw={})", uVal, Arrays.toString(raw));
        if (uVal != 0 || !allZero) return uVal * scale;
        long uValSw = combineBEUnsigned(swapWords(raw));
        log.debug("Interpreted unsigned BE-swapped value={} (swapped={})", uValSw, Arrays.toString(swapWords(raw)));
        if (uValSw != 0) return uValSw * scale;

        // if reached here, nothing useful
        return Double.NaN;
    }

    public double readGridPowerWatts(){
        return robustReadScaled(gridPowerOffset, gridPowerWords, gridPowerSigned, powerScaleRegister);
    }

    public double readSitePowerWatts(){
        return robustReadScaled(sitePowerOffset, sitePowerWords, sitePowerSigned, powerScaleRegister);
    }

    @Override
    public void destroy(){
        if (master != null) {
            try { master.destroy(); } catch (Exception ignored){}
        }
    }

    @Override
    public Double gridMeter(){
        return readGridPowerWatts();
    }

    @Override
    public Double solarMeter(){
        return readSitePowerWatts();
    }
}
