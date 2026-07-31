# Heartbeat Specification

## Purpose

How the Controllable System (LPC/LPP Server role) surfaces its paired Energy Guard's Heartbeat
to openHAB.

## ADDED Requirements

### Requirement: Heartbeat trigger Channel

The binding SHALL fire a `heartbeat` trigger Channel, under both the `lpc` and `lpp` Channel
Groups, once for each Heartbeat notification received from the paired Energy Guard's
`DeviceDiagnosis` feature.

#### Scenario: Heartbeat received while the peer is resolved

- GIVEN a Controllable System Entity has an Energy Guard partner resolved to its
  `eebus:oh-entity`/`eebus:oh-cs-entity` Thing handler
- WHEN a Heartbeat notification arrives from that partner's `DeviceDiagnosis` feature
- THEN the `heartbeat` Channel under the corresponding Channel Group (`lpc` or `lpp`, matching
  which Use Case received the notification) fires an event
- AND the event payload is the Heartbeat's `heartbeatCounter` value as a string, if the
  notification carried one

#### Scenario: Heartbeat received before the peer is resolved

- GIVEN a Controllable System Entity has not yet resolved an Energy Guard partner's
  `eebus:oh-entity`/`eebus:oh-cs-entity` Thing handler
- WHEN a Heartbeat notification arrives from that partner's `DeviceDiagnosis` feature
- THEN the internal Heartbeat watchdog is still rearmed
- AND no `heartbeat` Channel event fires, since no Thing is yet resolved to fire it on

#### Scenario: Heartbeat without a counter

- GIVEN a Heartbeat notification arrives without a `heartbeatCounter` value
- WHEN the `heartbeat` Channel fires
- THEN the event payload is empty rather than the binding failing to fire the event
