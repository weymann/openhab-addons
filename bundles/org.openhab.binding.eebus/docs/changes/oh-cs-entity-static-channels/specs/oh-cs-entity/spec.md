# Delta for `oh-cs-entity` Static-Channel Thing Type

## ADDED Requirements

### Requirement: Dedicated oh-cs-entity Thing type

The binding SHALL provide a Thing type `eebus:oh-cs-entity` ("EEBus OH Controllable System
Entity"), representing one SPINE Entity trusted by a parent `eebus:oh-cs-device` Bridge, distinct
from the generic `eebus:oh-entity`.

#### Scenario: Adding an oh-cs-entity Thing

- GIVEN a user is adding a child Thing under an `eebus:oh-cs-device` Bridge
- WHEN the Thing type picker for that Bridge is used
- THEN `eebus:oh-cs-entity` ("EEBus OH Controllable System Entity") is offered, not
  `eebus:oh-entity`

### Requirement: oh-cs-entity is exclusive to oh-cs-device

The binding SHALL restrict `eebus:oh-cs-entity` to `eebus:oh-cs-device` as its only valid parent
Bridge type, and SHALL restrict `eebus:oh-entity` to `eebus:oh-device` as its only valid parent
Bridge type.

#### Scenario: oh-entity is no longer offered under oh-cs-device

- GIVEN a user is adding a child Thing under an `eebus:oh-cs-device` Bridge
- WHEN the Thing type picker for that Bridge is used
- THEN `eebus:oh-entity` is not offered as a valid child type

#### Scenario: oh-cs-entity is not offered under oh-device

- GIVEN a user is adding a child Thing under a plain `eebus:oh-device` Bridge
- WHEN the Thing type picker for that Bridge is used
- THEN `eebus:oh-cs-entity` is not offered as a valid child type

### Requirement: oh-cs-entity's LPC/LPP Channels are statically declared

The binding SHALL declare `eebus:oh-cs-entity`'s `lpc`/`lpp` Channel Groups (each with
`limit-active`, `limit-value`, `failsafe-limit-value`, `failsafe-duration-minimum`) statically in
`thing-types.xml`, present on every instance from Thing creation, rather than created dynamically
on the first EEBus event the way `eebus:oh-entity`'s Channels are (docs/ADR/014,
docs/ADR/015).

#### Scenario: Channels exist immediately after Thing creation

- GIVEN a user creates a new `eebus:oh-cs-entity` Thing under a trusted `eebus:oh-cs-device`
  Bridge
- WHEN the Thing is created, before any LPC/LPP EEBus event has occurred
- THEN its `lpc#limit-active`, `lpc#limit-value`, `lpc#failsafe-limit-value`,
  `lpc#failsafe-duration-minimum`, `lpp#limit-active`, `lpp#limit-value`,
  `lpp#failsafe-limit-value`, and `lpp#failsafe-duration-minimum` Channels all already exist
  (state `NULL` until the first real update, same as any Channel before its first update)

#### Scenario: A CS status update still reaches a statically declared Channel

- GIVEN an `eebus:oh-cs-entity` Thing whose Channels were statically declared at creation
- WHEN `AbstractEEBusLimitControllableSystemUseCase` calls `applyLimitStatus`/
  `applyFailsafeStatus` on its resolved `EEBusOhEntityHandler`
- THEN the pre-existing Channel is updated via `updateCachedState`, without `ensureChannel`
  creating a duplicate or erroring

### Requirement: oh-cs-entity reuses oh-entity's config and handler

The binding SHALL configure and handle `eebus:oh-cs-entity` using the exact same
`EEBusOhEntityConfiguration` (`ski`, `entityAddress`, `shipId`) and `EEBusOhEntityHandler` classes
`eebus:oh-entity` uses - no separate Java classes.

#### Scenario: Trust-status derivation is identical

- GIVEN an `eebus:oh-cs-entity` Thing configured with `ski=X` under an `eebus:oh-cs-device`
  Bridge that does not currently trust `X`
- WHEN the Thing's status is derived
- THEN it goes `OFFLINE`/`CONFIGURATION_PENDING`, via the same `applyStatus()` logic
  `eebus:oh-entity` uses (`EEBusHandler#isTrusted(String)`)

#### Scenario: oh-cs-entity is resolvable by the transport layer

- GIVEN an `eebus:oh-cs-entity` Thing configured with `ski=X`, trusted by its parent
  `eebus:oh-cs-device` Bridge
- WHEN `EEBusHandler#ohEntityHandlerForSki("X")` is called (e.g. from
  `AbstractEEBusLimitControllableSystemUseCase#onEnergyGuardFound`)
- THEN the `eebus:oh-cs-entity` Thing's handler is returned, exactly as an `eebus:oh-entity`
  Thing's handler would be

## MODIFIED Requirements

### Requirement: eebus:oh-entity's supported-bridge-type-refs

The binding SHALL restrict `eebus:oh-entity`'s `supported-bridge-type-refs` to `eebus:oh-device`
only.

This narrows docs/ADR/024-oh-device-oh-entity-rename.md's Post-acceptance fix (which had widened
`eebus:oh-entity` to also accept `eebus:oh-cs-device`, as an interim fix so _some_ Entity Thing
type could be created under `eebus:oh-cs-device` at all) now that `eebus:oh-cs-entity` exists as
the type actually meant for that role.

#### Scenario: A pre-existing oh-entity under oh-cs-device is no longer valid

- GIVEN an `eebus:oh-entity` Thing that was created under an `eebus:oh-cs-device` Bridge before
  this change (only possible in the window between ADR-024's Post-acceptance fix and this
  change)
- WHEN the binding is upgraded to include this change
- THEN that Thing's bridge-type reference is no longer valid; see "Migration" in ADR-025 for the
  manual step needed to recreate it as `eebus:oh-cs-entity`

## Migration

No automated migration. An `eebus:oh-entity` Thing currently configured under an
`eebus:oh-cs-device` Bridge must be deleted and recreated as `eebus:oh-cs-entity` with the same
`ski`/`entityAddress` values after upgrading - the parent Bridge's `trustedSkis` trust state is
unaffected and does not need to be re-granted. See docs/ADR/025-oh-cs-entity-static-channels.md
"Migration".
