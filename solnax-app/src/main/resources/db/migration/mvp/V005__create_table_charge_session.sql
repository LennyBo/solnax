CREATE TABLE charge_session(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vin VARCHAR NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT current_timestamp,
    ended_at TIMESTAMP,
    energy_start_kwh DOUBLE PRECISION,
    energy_end_kwh DOUBLE PRECISION,
    energy_charged_kwh DOUBLE PRECISION,
    amps_set INTEGER,
    status VARCHAR NOT NULL DEFAULT 'ACTIVE'
);

CREATE INDEX idx_charge_session_vin_status ON charge_session(vin, status);
CREATE INDEX idx_charge_session_started_at ON charge_session(started_at);

