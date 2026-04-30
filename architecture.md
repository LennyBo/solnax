# Solnax Architecture

## Overview

Solnax is split into two main applications:

- `solnax-app`: Spring Boot backend that reads power meters, stores time-series power logs, optimizes EV charging, and exposes REST APIs.
- `solnax-frontend`: Angular frontend that visualizes current power flow, historical charts, cooldown state, and charge sessions.

The backend is the system of record for optimization and persistence. The frontend is a read/control client over backend APIs.

## Backend structure

### Core process flow

1. `JobManager` runs every 5 minutes.
2. `PowerLogManager.logPower()` collects readings from the inverter and Shelly EM3 devices and persists a `PowerLog`.
3. During daytime cycles, `ChargeOptimizer.optimize(...)` uses the freshly saved `PowerLog` to decide whether to start, stop, or adjust EV charging.
4. Charge-point specific behavior is encapsulated behind `IChargePoint`, currently implemented by `TeslaWallCharger`.

### Meter adapters

- `IPowerMeter` provides inverter/grid readings.
- `ShellyEm3Registry` manages configured Shelly EM3 clients.
- `ShellyEm3Client` talks to one Shelly device over HTTP.

#### Best-effort Shelly reads

Shelly-backed sub-meter readings are intentionally best-effort:

- `heater`, `charger`, and `kitchen` are read independently.
- If one Shelly does not respond, only that specific reading is stored as `null`.
- A single failing Shelly must **not** break the `PowerLogManager` loop or prevent other readings from being persisted.
- Daytime optimization skips the cycle if required readings such as charger power are missing.

This keeps logging resilient while making missing data explicit instead of silently inventing a value.

### Persistence model

`PowerLog` stores:

- `time`
- `solar`
- `house`
- `heater`
- `charger`
- `kitchen`

The Shelly-derived fields (`heater`, `charger`, `kitchen`) are nullable to represent missing device responses.

### DTO/API behavior

- `InstantPower` is built from the latest live meter snapshot.
- Historical `PowerLogs` are padded to 5-minute intervals for chart rendering.
- When a Shelly-derived value is missing, the matching DTO field remains `null`.
- Derived `house` consumption in chart data is only produced when all required inputs are available; otherwise it is also `null` for that slot.

## Charging subsystem

### Optimizer responsibilities

`ChargeOptimizer`:

- clears per-cycle Tesla cache,
- detects auto-started / auto-stopped charging,
- computes available power from the latest `PowerLog`,
- starts, stops, or adjusts charging based on surplus and battery rules.

### Tesla integration

`TeslaWallCharger` coordinates:

- BLE communication through `TeslaBLEAdapter`,
- active cooldown handling through `ChargePointCoolDownManager`,
- charge session lifecycle through `ChargeSessionManager`.

It caches vehicle data per optimizer cycle to avoid unnecessary BLE wake-ups.

## Frontend structure

The Angular app consumes backend power and charging APIs.

Main UI areas:

- `house-graphic`: live current-power overview
- `data-chart`: day view of historical `PowerLog` values
- `cool-down-control`: cooldown management
- `charge-session-list`: session history

The frontend should treat nullable power values as "reading unavailable" rather than assuming zero.

## Operational notes

- `docker-compose.yaml` orchestrates local services.
- Database schema is versioned under `solnax-app/src/main/resources/db/migration/mvp`.
- Backend configuration lives in `application.yaml` and environment-specific variants such as `application-pi.yaml`.

