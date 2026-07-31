# Delta for CS Service Bridge

## ADDED Requirements

### Requirement: A convenience Bridge Thing type offers the Controllable System role without checkboxes or Item metadata

The binding SHALL provide a Bridge Thing type, `eebus:cs-service`, that unconditionally offers
the LPC and LPP Server ("Controllable System") use cases on its own local SHIP/SPINE identity,
without requiring the `supportedUseCasesClient`/`supportedUseCasesServer` configuration used by
`eebus:service`.

#### Scenario: Adding an eebus:cs-service Bridge

- GIVEN a user adds a new `eebus:cs-service` Bridge Thing with valid vendor/device/serial
  configuration
- WHEN the Bridge starts
- THEN it exposes LPC and LPP Server-role use cases on its local Entity, with no configuration
  step beyond the Bridge's own Thing-config
- AND it does not expose MPC, or any Client-role use case, regardless of any other configuration

### Requirement: eebus:cs-service seeds its failsafe values from Thing config, never from Item metadata or a Rule

The binding SHALL seed the `eebus:cs-service` Bridge's failsafe limit and duration values from
its own Thing-config parameters at every startup, and SHALL NOT provide any Item-metadata-based
or Rule-based path to change these values from openHAB - only a paired Energy Guard, writing over
EEBus, may change the running value.

#### Scenario: Bridge starts for the first time

- GIVEN an `eebus:cs-service` Bridge configured with `failsafeConsumptionLimitSeedWatts=2000`,
  `failsafeDurationMinimumSeedSeconds=180`
- WHEN the Bridge starts and no Energy Guard has connected yet
- THEN the SPINE-visible failsafe limit is `2000` W and the failsafe duration is `180` s
- AND the paired oh-peer's `failsafe-limit-value`/`failsafe-duration-minimum` Channels (once a
  peer resolves) reflect the same seeded values

#### Scenario: User edits the Thing-config seed value after a real Energy Guard has already written a different one

- GIVEN a paired Energy Guard has written a failsafe limit different from the Bridge's
  Thing-config seed
- WHEN the user edits the Bridge's `failsafeConsumptionLimitSeedWatts` Thing-config parameter
- THEN the running SPINE-visible value is not changed immediately
- AND the new Thing-config value only takes effect the next time the Bridge (re-)initializes

### Requirement: eebus:cs-service is a separate Bridge Thing type, not a mode of eebus:service

The binding SHALL implement `eebus:cs-service` as its own `ThingTypeUID`, with its own local
SHIP/SPINE identity, structurally independent of and coexistable with any `eebus:service` Bridge -
not a configuration mode switch on `eebus:service`.

#### Scenario: Both Bridge types configured in the same openHAB instance

- GIVEN an existing `eebus:service` Bridge with `supportedUseCasesServer` including LPC/LPP
- WHEN a user additionally adds an `eebus:cs-service` Bridge
- THEN both Bridges start independently, each with its own local SHIP/SPINE identity and
  certificate/SKI, and neither configuration affects the other

---
