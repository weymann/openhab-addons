# Delta for Thing-Model Rename and Bridge-Level Trust

## ADDED Requirements

### Requirement: Bridge/Thing type identifiers match EEBUS/SHIP/SPINE spec vocabulary

The binding SHALL name its Bridge and Thing types after formally defined EEBUS/SHIP/SPINE
concepts instead of the binding-internal `service`/`peer` vocabulary: `eebus:oh-device` and
`eebus:oh-cs-device` (Bridges, each one local SHIP/SPINE service instance), `eebus:hw-device`
(bridgeless Thing, a real device seen on the network), and `eebus:oh-entity` (Thing, child of a
Bridge, one SPINE Entity).

#### Scenario: Adding an oh-device Bridge

- GIVEN a user adds a new Bridge Thing of the "EEBus OH Device" type
- WHEN the Thing type picker is used
- THEN the Bridge's Thing type id is `eebus:oh-device`, not `eebus:service`

#### Scenario: Adding an hw-device Thing via Inbox

- GIVEN `EEBusMdnsDiscoveryParticipant` surfaces a newly seen SHIP device
- WHEN the Inbox suggestion is created
- THEN its Thing type id is `eebus:hw-device`, not `eebus:eebus-peer`

### Requirement: Bridge-level trust via a trusted-SKI config list

The binding SHALL manage trust (which SKIs an `eebus:oh-device`/`eebus:oh-cs-device` Bridge
accepts/dials as a Trusted SHIP Node) via a `trustedSkis` Bridge config parameter (a list of
SKIs), which is the source of truth for `EEBusHandler#recomputeTrustedSkis()`.

#### Scenario: Bridge starts with a pre-configured trusted-SKI list

- GIVEN an `eebus:oh-device` Bridge configured with `trustedSkis` containing SKI `X`
- WHEN the Bridge starts
- THEN `ShipCommunication` is constructed with `X` in its trusted-SKI set, without any child
  `eebus:oh-entity` Thing needing to exist first

#### Scenario: Editing trustedSkis in the Bridge's own config form

- GIVEN a running `eebus:oh-device` Bridge
- WHEN a user adds or removes a SKI directly in the Bridge's `trustedSkis` config parameter and
  saves
- THEN the change takes effect the same way any other Thing-config edit does (a normal
  dispose/initialize cycle), with no separate confirmation step required

### Requirement: trust()/untrust() Bridge Actions as convenience mutators of trustedSkis

The binding SHALL expose `trust(ski)`/`untrust(ski)` Thing Actions on `eebus:oh-device`/
`eebus:oh-cs-device` Bridges that add/remove a single SKI from the same `trustedSkis` config list
`EEBusHandler#recomputeTrustedSkis()` reads - not a second, independently persisted trust
mechanism - and take effect live, without requiring a full Bridge restart.

#### Scenario: Granting trust via the trust() Bridge Action

- GIVEN a running `eebus:oh-device` Bridge that does not yet trust SKI `Y`
- WHEN the Bridge's `trust(Y)` Thing Action is invoked
- THEN `Y` is added to the Bridge's persisted `trustedSkis` config
- AND `ShipCommunication`'s live trusted-SKI set is updated immediately, without a Bridge restart
- AND any `eebus:oh-entity` child Thing configured with `ski=Y` transitions out of "not yet
  trusted" status without needing its own action invoked

#### Scenario: Revoking trust via the untrust() Bridge Action

- GIVEN a running `eebus:oh-device` Bridge that currently trusts SKI `Y` (via `trustedSkis`)
- WHEN the Bridge's `untrust(Y)` Thing Action is invoked
- THEN `Y` is removed from the Bridge's persisted `trustedSkis` config
- AND `ShipCommunication`'s live trusted-SKI set no longer includes `Y`
- AND any `eebus:oh-entity` child Thing configured with `ski=Y` moves back to "not yet trusted"
  status
- AND no `eebus:oh-entity` Thing is removed or has its own configuration changed

#### Scenario: Invoking trust()/untrust() is idempotent

- GIVEN a SKI already present (or already absent) in `trustedSkis`
- WHEN `trust()` (or `untrust()`) is invoked again for that same SKI
- THEN the call is a harmless no-op - the list is not duplicated, and no error is raised

### Requirement: eebus:oh-entity carries an entityAddress config field

The binding SHALL provide an `entityAddress` config parameter on `eebus:oh-entity`, recording
which SPINE Entity on the trusted device this Thing represents, alongside its existing `ski`.

#### Scenario: Creating an oh-entity Thing

- GIVEN a user creates a new `eebus:oh-entity` Thing under an `eebus:oh-device` Bridge
- WHEN the Thing's config form is filled in
- THEN both `ski` (the trusted device) and `entityAddress` (the Entity on that device) are
  available as config parameters

## MODIFIED Requirements

### Requirement: eebus:oh-entity's status reflects Bridge-level trust, not its own property

The binding SHALL derive `eebus:oh-entity`'s `ThingStatus` from whether its parent Bridge
currently trusts its configured `ski` (`EEBusHandler#isTrusted(String)`), instead of a
per-Thing `paired` property set by its own Thing Actions.

This modifies docs/ADR/012-pairing-trust-property-and-actions.md's decision (see docs/ADR/024,
which supersedes it): `eebus:oh-peer`/`eebus:oh-entity` no longer owns `pair()`/`unpair()`
Actions or a `paired` property at all - see the REMOVED requirement below.

#### Scenario: oh-entity configured with an untrusted SKI

- GIVEN an `eebus:oh-entity` Thing configured with `ski=Z`, under a Bridge whose `trustedSkis`
  does not contain `Z`
- WHEN the Thing initializes (Bridge is ONLINE)
- THEN its status is `OFFLINE`/`CONFIGURATION_PENDING`, with a message pointing at the Bridge's
  `trust()` Action or `trustedSkis` config - not at an Action on this Thing itself

#### Scenario: oh-entity configured with a trusted SKI

- GIVEN an `eebus:oh-entity` Thing configured with `ski=Z`, under a Bridge whose `trustedSkis`
  contains `Z`
- WHEN the Thing initializes (Bridge is ONLINE)
- THEN its status is `ONLINE`

## REMOVED Requirements

### Requirement: pair()/unpair() Thing Actions and a paired Thing property on the Entity Thing

**Reason**: SKI-based trust is Device-granular in SHIP (one SHIP Node, one certificate/SKI), not
Entity-granular in SPINE (an Entity is a sub-part of one already-trusted Device) - see the
grounding in docs/ADR/024 and CONCEPT.md's 2026-08-23 addendum. Granting/revoking trust from a
Thing that represents a single SPINE Entity was structurally the wrong level once `eebus:oh-peer`
was re-scoped to represent one Entity instead of a whole paired device.

**Migration**: `EEBusOhPeerActions#pair()`/`#unpair()` and the `paired` Thing property are
removed from the Entity Thing entirely - see the ADDED "trust()/untrust() Bridge Actions"
requirement above for the replacement, now on the parent Bridge. An existing `eebus:oh-peer`
Thing's `paired=true` property has no successor value to migrate to automatically; the
equivalent SKI must be (re-)added to the new parent Bridge's `trustedSkis` config once, after
upgrading - this is a manual one-time step, not automated by this change (no data migration
script - out of scope, consistent with how ADR-020's Thing-type merge was also not
auto-migrated).

---
