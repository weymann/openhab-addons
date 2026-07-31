# State Specification

## Purpose

How the Controllable System (LPC/LPP Server role) surfaces its own state machine's current
state to openHAB.

## ADDED Requirements

### Requirement: State Channel

The binding SHALL publish the Controllable System state machine's current state as a `state`
Channel, under both the `lpc` and `lpp` Channel Groups, encoded as a plain `Number` matching
`EEBusLimitControlState#ordinal()`, with a readable label available for each value via the
Channel-Type's state options.

#### Scenario: State transition while the peer is resolved

- GIVEN a Controllable System Entity has an Energy Guard partner resolved to its
  `eebus:oh-entity`/`eebus:oh-cs-entity` Thing handler
- WHEN the state machine transitions to a new state
- THEN the `state` Channel under the corresponding Channel Group (`lpc` or `lpp`) updates to
  that state's ordinal value
- AND Main UI (or any client resolving the Channel-Type's options) can show that value's
  readable label

#### Scenario: State known before the peer is resolved

- GIVEN a Controllable System Entity's state machine has already computed a state
- WHEN an Energy Guard partner is subsequently resolved to its Thing handler
- THEN the `state` Channel is updated immediately with the already-known state, rather than
  waiting for the next transition

#### Scenario: Existing metadata output unaffected

- GIVEN a user has an Item tagged `eebus="LPC.state"` or `eebus="LPP.state"`
- WHEN the state machine transitions
- THEN that Item continues to receive the state's name as a String, unchanged by the addition
  of the `state` Channel
