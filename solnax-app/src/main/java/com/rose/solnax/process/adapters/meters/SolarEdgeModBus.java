package com.rose.solnax.process.adapters.meters;

import com.serotonin.modbus4j.ModbusFactory;
import com.serotonin.modbus4j.ModbusMaster;
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

import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
public class SolarEdgeModBus implements IPowerMeter, DisposableBean {

    private final ModbusFactory modbusFactory = new ModbusFactory();
    private ModbusMaster master;

    @Value("${solaredge.modbus.host}")
    private String host;
    @Value("${solaredge.modbus.port:1502}")
    private int port;
    @Value("${solaredge.modbus.slaveId:1}")
    private int slaveId;
    @Value("${solaredge.modbus.connectTimeoutMs:3000}")
    private int connectTimeoutMs;

    // Use Relative Base-0 Offsets
    @Value("${solaredge.modbus.registers.gridPowerOffset:206}")
    private int gridPowerOffset;
    @Value("${solaredge.modbus.registers.sitePowerOffset:83}")
    private int sitePowerOffset;

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

            master = modbusFactory.createTcpMaster(params, true);
            master.setTimeout(connectTimeoutMs);
            master.setRetries(1);

            try {
                master.init();
                log.info("Connected to SolarEdge Modbus TCP {}:{}", host, port);
            } catch (ModbusInitException e) {
                log.error("Failed to init Modbus master: {}", e.getMessage());
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
     * Reads a SunSpec value and its scale factor.
     * SolarEdge Power (Inverter): Value at 83, SF at 84 (Distance 1)
     * SolarEdge Power (Grid Meter): Value at 206, SF at 210 (Distance 4)
     */
    private Integer readSunSpecValue(int offset, int sfOffset) {
        ensureConnected();
        try {
            int count = (sfOffset - offset) + 1;
            ReadHoldingRegistersRequest request = new ReadHoldingRegistersRequest(slaveId, offset, count);
            ReadHoldingRegistersResponse response = (ReadHoldingRegistersResponse) master.send(request);

            if (response == null || response.isException()) {
                throw new ModbusTransportException("Read failed: " + (response == null ? "null" : response.getExceptionMessage()));
            }

            short[] data = response.getShortData();
            short rawValue = data[0];
            short scaleFactor = data[data.length - 1];

            // Formula: Value * 10^SF
            return (int) (rawValue * Math.pow(10, scaleFactor));
        } catch (Exception e) {
            log.error("Modbus read error at offset {}: {}", offset, e.getMessage());
            return 0;
        }
    }

    @Override
    public Integer gridMeter() {
        // Documentation 40207 (Value) and 40211 (SF)
        // Relative: 206 and 210
        return readSunSpecValue(gridPowerOffset, gridPowerOffset + 4) * -1;
    }

    @Override
    public Integer solarMeter() {
        // Documentation 40084 (Value) and 40085 (SF)
        // Relative: 83 and 84
        return readSunSpecValue(sitePowerOffset, sitePowerOffset + 1);
    }

    @Override
    public void destroy() {
        if (master != null) {
            master.destroy();
        }
    }
}