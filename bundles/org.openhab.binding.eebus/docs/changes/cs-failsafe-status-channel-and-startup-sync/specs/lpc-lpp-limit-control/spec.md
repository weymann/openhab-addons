# Delta for LPC/LPP Limit Control

## ADDED Requirements

### Requirement: Controllable System exposes the Energy Guard's failsafe configuration as a read-only Channel

The binding SHALL expose the paired Energy Guard's currently configured failsafe limit value and
failsafe duration minimum (LPC/LPP Scenario 2) as read-only Channels on that peer's
`eebus:oh-entity` Thing, and SHALL NOT provide any path (Item, metadata, or Rule) for openHAB to
change these values - only the Energy Guard, writing over EEBus, may change them.

#### Scenario: Energy Guard writes a new failsafe limit

- GIVEN a paired `eebus:oh-entity` playing Energy Guard against openHAB's Controllable System role
- WHEN the Energy Guard writes a new `FailsafeConsumptionActivePowerLimit` (or
  `FailsafeProductionActivePowerLimit`) value over EEBus
- THEN the paired oh-entity's `lpc#failsafe-limit-value` (or `lpp#failsafe-limit-value`) Channel
  updates to the new value
- AND the Channel remains read-only - no Command sent to it is forwarded to SPINE

#### Scenario: Energy Guard writes a new failsafe duration

- GIVEN a paired `eebus:oh-entity` playing Energy Guard against openHAB's Controllable System role
- WHEN the Energy Guard writes a new `FailsafeDurationMinimum` value over EEBus
- THEN the paired oh-entity's `lpc#failsafe-duration-minimum` (or `lpp#failsafe-duration-minimum`)
  Channel updates to the new value

### Requirement: Controllable System publishes its current status immediately at startup, not only on the first transition

The binding SHALL publish the Controllable System's freshly reset state and failsafe
configuration to the `LPC.state`/`LPP.state` metadata Item and to the tracked Energy Guard's
paired oh-entity Channels immediately when the use case starts, rather than waiting for the first
Heartbeat timeout or Energy Guard write.

#### Scenario: openHAB restarts while a limit was previously active

- GIVEN an `eebus:oh-entity`'s `lpc#limit-active` Channel showed `ON` before openHAB restarted
- WHEN the Controllable System use case starts again after the restart
- THEN the `LPC.state` metadata Item is set to `INIT` immediately, without waiting for a
  Heartbeat timeout or a fresh Energy Guard write
- AND if the Energy Guard peer is already resolved at that point, its `lpc#limit-active` Channel
  is updated to `OFF` in the same step

#### Scenario: The Energy Guard peer resolves after the Controllable System's own startup

- GIVEN the Controllable System use case has already published its initial status at startup,
  before any Energy Guard partner was found
- WHEN `onEnergyGuardFound` later resolves that partner's `eebus:oh-entity` handler
- THEN the currently known state and failsafe status are published to that peer's Channels
  immediately, without waiting for the next transition or write

---
