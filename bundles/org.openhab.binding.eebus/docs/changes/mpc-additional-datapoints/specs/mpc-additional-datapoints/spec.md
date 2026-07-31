# Delta for MPC Additional Data Points

## ADDED Requirements

### Requirement: Additional MPC Client-role data points resolved by ScopeType

The binding SHALL resolve and expose 12 additional MPC Client-role data points - `power-phase-a`/`-b`/`-c`, `energy-consumed`, `energy-produced`, `current-phase-a`/`-b`/`-c`, `voltage-phase-a`/`-b`/`-c`, `frequency` - each identified in a peer's `MeasurementDescriptionListData` by an exact `ScopeType` match, alongside the existing `power` (Total Active Power) data point.

#### Scenario: An additional data point's Channel appears after first successful resolution

- GIVEN a paired MPC Server peer whose `MeasurementDescriptionListData` includes an entry with `scopeType` `acCurrentA`
- WHEN `EEBusMpcClientUseCase` resolves that peer's measurements
- THEN the resolved `eebus:oh-entity`/`eebus:oh-mpc-entity` Thing's `mpc#current-phase-a` Channel is created (if not already statically present) and updated with the corresponding value

#### Scenario: A single measurement notification updates multiple Channels at once

- GIVEN a paired MPC Server peer whose `MeasurementListData` notification carries entries for `power`, `power-phase-a`, and `current-phase-a` simultaneously
- WHEN `EEBusMpcClientUseCase` processes that notification
- THEN all three corresponding Channels are updated from the single notification, not just one

#### Scenario: An unresolvable additional data point simply never gets a Channel on the dynamic path

- GIVEN a paired MPC Server peer whose `MeasurementDescriptionListData` has no entry with `scopeType` `acEnergyConsumed`
- WHEN `EEBusMpcClientUseCase` resolves that peer's measurements
- THEN no `energy-consumed` Channel is created on a plain `eebus:oh-entity` Thing for that peer (dynamic creation only ever happens on a resolved measurement)

### Requirement: No measurement-type-only fallback for the 12 additional data points

The binding SHALL resolve each of the 12 additional data points by exact `ScopeType` match only - unlike `power`'s existing 3-step fallback chain (scope match, then measurement-type match, then sole-entry fallback), no measurement-type-only or sole-entry fallback SHALL be applied to these 12, since more than one entry of the same generic `MeasurementType` (e.g. multiple `POWER`-typed entries for different phases) makes such a fallback ambiguous for them.

#### Scenario: Two same-typed entries without matching scope resolve to neither Channel

- GIVEN a peer's `MeasurementDescriptionListData` has two entries with `measurementType` `power` and neither has `scopeType` `acPowerA` or `acPowerB`
- WHEN `EEBusMpcClientUseCase` resolves that peer's measurements
- THEN neither `power-phase-a` nor `power-phase-b` is resolved to either entry (both stay unresolved, no Channel created for them)

### Requirement: Phase-to-phase voltage stub Channels

The binding SHALL declare three additional MPC Channels (`voltage-a-b`, `voltage-b-c`, `voltage-c-a`) in the `mpc` `channel-group-type`, present statically on `eebus:oh-mpc-entity` from Thing creation, with no resolution logic behind them - they SHALL never be created dynamically on a plain `eebus:oh-entity` Thing (since dynamic creation requires a resolved measurement, and no resolution is attempted for these three) and SHALL remain at their default (`NULL`) state indefinitely on `eebus:oh-mpc-entity`.

#### Scenario: Stub Channel exists but stays NULL on oh-mpc-entity

- GIVEN a newly created `eebus:oh-mpc-entity` Thing, paired against a real MPC Server peer that reports phase-to-phase voltage values
- WHEN any amount of time passes, including after successful measurement resolution of other data points
- THEN `mpc#voltage-a-b`, `mpc#voltage-b-c`, and `mpc#voltage-c-a` all still exist but remain at state `NULL`

#### Scenario: Stub Channel never appears on a plain oh-entity

- GIVEN a plain `eebus:oh-entity` Thing configured for MPC Client, paired against the same peer
- WHEN `EEBusMpcClientUseCase` resolves that peer's measurements
- THEN no `voltage-a-b`/`voltage-b-c`/`voltage-c-a` Channel is ever created on it

## MODIFIED Requirements

### Requirement: MPC Client-role measurement resolution handles multiple data points per subscription

The binding SHALL resolve and subscribe to all data points identifiable in a single `MeasurementDescriptionListData` read (the mandatory `power` plus any of the 12 additional ones present), rather than exactly one.

(Previously: `EEBusMpcClientUseCase#subscribe()` resolved and subscribed to exactly one measurement ID - `power` (Total Active Power) - per peer, ignoring any other entries a real peer's `MeasurementDescriptionListData`/`MeasurementListData` might contain.)

#### Scenario: A peer offering only power behaves exactly as before

- GIVEN a paired MPC Server peer whose `MeasurementDescriptionListData` has exactly one entry, matching `power`'s existing fallback chain
- WHEN `EEBusMpcClientUseCase` resolves and subscribes to that peer's measurements
- THEN only the `mpc#power` Channel is created/updated, identical to the pre-change behavior
