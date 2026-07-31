# Delta for EnergyGuard Outgoing Heartbeat (Scenario 3)

## ADDED Requirements

### Requirement: EnergyGuard actor sends its own outgoing Heartbeat

The binding SHALL expose a local `DeviceDiagnosis` Server feature with a self-perpetuating
Heartbeat (interval 60 seconds, per LPC-005/006) on the local CEM Entity whenever an LPC or LPP
EnergyGuard (Client-role) Use Case is active on it - matching the mutual-Heartbeat requirement of
Scenario 3 (`EEBus_UC_IG_GeneralGuidelines_V1.0.0.pdf`).

#### Scenario: A standalone EnergyGuard-only oh-device sends its own Heartbeat

- GIVEN an `eebus:oh-device` Bridge configured with an EnergyGuard-role LPC or LPP Use Case
  (e.g. an `eebus:oh-eg-entity` child, or `supportedUseCasesClient` containing `LPC`/`LPP`) and
  no Controllable-System-role Use Case active
- WHEN the binding starts up and connects
- THEN the local CEM Entity exposes an active, self-perpetuating `DeviceDiagnosis` Heartbeat
  (60 second interval) that a subscribed peer receives, without any additional configuration or
  Rule

#### Scenario: Heartbeat is shared, not duplicated, when both roles are active on the same Bridge

- GIVEN an `eebus:oh-device` Bridge configured with both a Controllable-System-role LPC/LPP Use
  Case (e.g. `eebus:oh-cs-entity`) and an EnergyGuard-role LPC/LPP Use Case (e.g.
  `eebus:oh-eg-entity`)
- WHEN the binding starts up
- THEN both Use Cases share the local CEM Entity's single `DeviceDiagnosis` Server feature and its
  single Heartbeat schedule - no duplicate feature is created and no duplicate periodic timer
  runs

#### Scenario: The existing limit-write path is unaffected

- GIVEN an EnergyGuard-role Use Case with its limit-write path (Scenario 1) already established
  for a peer
- WHEN the local outgoing Heartbeat feature is added
- THEN the limit-write behavior is unchanged - the write path does not depend on, and is not
  gated by, the local Heartbeat feature

## Migration

None. This change is purely additive on the local (openHAB-side) SPINE Entity - no Thing type,
Channel, or config parameter changes; no existing behavior is altered.
