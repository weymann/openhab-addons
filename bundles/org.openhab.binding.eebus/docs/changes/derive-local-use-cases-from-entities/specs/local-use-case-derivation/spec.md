# Delta for Local Use-Case Derivation

## ADDED Requirements

### Requirement: Local Use Cases derived from Entity children

`eebus:oh-device` MUST derive the local SPINE `Device`'s Use-Case set from its current child
Things at every `initialize()`, instead of from a Bridge-level configuration parameter.

#### Scenario: Generic Entity child contributes its own configured Use Cases

- GIVEN an `eebus:oh-device` Bridge with one `eebus:oh-entity` child configured with
  `supportedUseCasesServer` containing `LPC`
- WHEN the Bridge initializes
- THEN the local `Device` is built with an `EEBusLpcServerUseCase`

#### Scenario: Convenience Entity children imply fixed Use Cases

- GIVEN an `eebus:oh-device` Bridge with one `eebus:oh-cs-entity` child
- WHEN the Bridge initializes
- THEN the local `Device` is built with `EEBusLpcServerUseCase` and `EEBusLppServerUseCase`,
  seeded from that child's own failsafe Thing-config values, without any checkbox configured
  anywhere

#### Scenario: No children configured

- GIVEN an `eebus:oh-device` Bridge with no child Entity Things
- WHEN the Bridge initializes
- THEN the local `Device` is built with no local Use Cases, and the Bridge still starts
  successfully

### Requirement: Any configuration change triggers a full rebuild

Every configuration change to `eebus:oh-device` or any of its child Entity Things MUST result in
a full teardown and rebuild of the local SPINE `Device`, automatically, without requiring the
user to manually disable the Bridge first.

#### Scenario: Adding an Entity child rebuilds the Bridge

- GIVEN an `eebus:oh-device` Bridge that is `ONLINE` with an existing trusted peer connection
- WHEN a new `eebus:oh-cs-entity` child Thing is added under it
- THEN the Bridge disposes its current `ShipCommunication`/`Device` and rebuilds both, briefly
  reconnecting to the existing trusted peer

#### Scenario: Editing `trustedSkis` rebuilds the Bridge

- GIVEN an `eebus:oh-device` Bridge that is `ONLINE`
- WHEN a user edits the Bridge's `trustedSkis` Thing-config directly (Main UI/REST)
- THEN openHAB's standard configuration-update cycle disposes and re-initializes the Bridge,
  which rebuilds the local `Device` with the updated trusted-SKI set

## MODIFIED Requirements

### Requirement: `eebus:oh-cs-entity` is offered under `eebus:oh-device`

`eebus:oh-cs-entity` MUST be creatable as a child of `eebus:oh-device`.
(Previously: exclusive to the now-removed `eebus:oh-cs-device` Bridge, per
docs/ADR/025-oh-cs-entity-static-channels.md Decision 1.)

#### Scenario: oh-cs-entity offered under the generic Bridge

- GIVEN an `eebus:oh-device` Bridge
- WHEN adding a child Thing
- THEN `eebus:oh-cs-entity` is offered as a valid child type, alongside `eebus:oh-entity` and
  `eebus:oh-eg-entity`

## REMOVED Requirements

### Requirement: `eebus:oh-cs-device` convenience Bridge

(Superseded by deriving the local Use-Case set from Entity children - a plain `eebus:oh-device`
with an `eebus:oh-cs-entity` child now produces the identical behavior, so the dedicated Bridge
Thing type no longer serves a purpose. See docs/ADR/027-derive-local-use-cases-from-entities.md.)

### Requirement: Live `trust()`/`untrust()` Bridge Actions

(Superseded by the uniform full-rebuild-on-any-change rule - trust changes are no longer a
special case; `trustedSkis` is edited only as ordinary Thing-config. See
docs/ADR/027-derive-local-use-cases-from-entities.md Decision 6.)

---
