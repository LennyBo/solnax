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

### Charge control modes

The optimizer runs in one of three global modes, persisted as a cool down whose reason is
flagged as a "mode" (`CoolDownReason.isMode()`), so it expires automatically like any other
cool down. Modes are mutually exclusive and are managed through `ChargePointCoolDownManager`:

- `NORMAL`: full solar optimization — start, adjust and stop charging based on the surplus.
- `ECO_PLUS`: keeps following solar production, but never stops an ongoing charge. When the
  surplus is gone the charge falls back to the lowest charge speed instead of stopping.
  Eco+ still only *starts* a charge when there is enough surplus — if the car (or the user)
  starts a charge, Eco+ keeps it alive. Because the optimizer only runs between 05:00 and
  22:59 and the mode expires at 06:00, an Eco+ charge that is running in the evening keeps
  going through the night at the lowest speed. This is intentional: Eco+ guarantees the car
  is charged by morning, prioritizing solar but accepting grid power when there is none.
- `MANUAL`: the optimizer is asleep regarding the car. It does not start, stop or adjust
  charging and does not talk to the vehicle at all — `ChargeOptimizer.optimize(...)` returns
  before any detection or BLE call. Power logging is unaffected.

Mode cool downs use the target `ALL` and must never be treated as a per-vehicle block;
`TeslaWallCharger` filters them out of every per-vehicle cool down check. Vehicle scoped
reasons (`FULL`, `NOT_CONNECTED`, `LOW_BATTERY`, `NO_RESPONSE`) keep blocking a single car.

When the optimizer wakes up after a manual sleep it calls `IChargePoint.resetCachedState()`,
which forgets the connected car and aborts any charge session started before the sleep,
because cars may have been swapped or unplugged in the meantime. The next detection cycle
then starts a fresh session for whichever car is really charging.

### Optimizer responsibilities

`ChargeOptimizer`:

- resolves the active charge control mode and sleeps entirely while `MANUAL` is active,
- clears the per-cycle charge point cache,
- detects auto-started / auto-stopped charging,
- computes available power from the latest `PowerLog`,
- starts charging or adjusts charging based on surplus,
- delegates charge-point specific low-battery / insufficient-surplus handling to `IChargePoint`,
  either via `handleInsufficientSurplus()` (NORMAL) or `maintainMinimumCharge()` (ECO_PLUS).

A charger draw above `IChargePoint.CHARGING_DETECTION_WATTS` (500W) counts as "charging"
everywhere — optimizer, auto-start detection and auto-stop detection — so a car running at the
lowest amps is still recognized as charging and stays under control of the optimizer.

### Tesla integration

`TeslaWallCharger` coordinates:

- BLE communication through `TeslaBLEAdapter`,
- active cooldown handling through `ChargePointCoolDownManager`,
- charge session lifecycle through `ChargeSessionManager`.

It caches vehicle data per optimizer cycle to avoid unnecessary BLE wake-ups.

When a car starts charging on its own, `TeslaWallCharger.detectAutoCharging(...)` resolves which VIN is charging, starts a session, clears stale `NOT_CONNECTED` cooldowns, and creates a `LOW_BATTERY` cooldown immediately when the active car is below 60%.

When surplus drops while a car is already charging, `TeslaWallCharger` owns the decision to either:

- keep charging by setting the low charge limit when a `LOW_BATTERY` cooldown is active or confirmed,
- or stop charging when the active vehicle is not low on battery.

In `ECO_PLUS` mode this decision is skipped entirely: `maintainMinimumCharge()` simply pins the
charge to the minimum amps and never stops it.

This keeps Tesla/BLE-specific wake-up and battery-resolution logic out of `ChargeOptimizer`.

## Frontend structure

The Angular app consumes backend power and charging APIs.

Main UI areas:

- `house-graphic`: live current-power overview
- `data-chart`: day view of historical `PowerLog` values
- `cool-down-control`: charge control mode (Normal / Eco+ / Manual) and cooldown management
- `charge-session-list`: session history

The frontend should treat nullable power values as "reading unavailable" rather than assuming zero.

## Operational notes

- `docker-compose.yaml` orchestrates local services.
- Database schema is versioned under `solnax-app/src/main/resources/db/migration/mvp`.
- Backend configuration lives in `application.yaml` and environment-specific variants such as `application-pi.yaml`.

