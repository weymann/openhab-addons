# Delta for `oh-eg-entity` Static-Channel Thing Type

## ADDED Requirements

### Requirement: oh-eg-entity Thing type

The binding SHALL provide a Thing type `eebus:oh-eg-entity` ("EEBus OH Energy Guard Entity"),
representing one SPINE Entity trusted by a parent `eebus:oh-device` Bridge, as an alternative to
the generic `eebus:oh-entity` for the EnergyGuard (LPC/LPP Client role) case.

#### Scenario: Adding an oh-eg-entity Thing

- GIVEN a user is adding a child Thing under an `eebus:oh-device` Bridge
- WHEN the Thing type picker for that Bridge is used
- THEN `eebus:oh-eg-entity` ("EEBus OH Energy Guard Entity") is offered, alongside
  `eebus:oh-entity`

### Requirement: oh-eg-entity is additional, not exclusive, under oh-device

The binding SHALL NOT restrict `eebus:oh-device` to only accepting `eebus:oh-eg-entity` as a
child - `eebus:oh-entity` SHALL remain equally valid under the same Bridge. `eebus:oh-eg-entity`
SHALL NOT be offered under `eebus:oh-cs-device`.

#### Scenario: oh-entity remains available under oh-device after oh-eg-entity is added

- GIVEN a user is adding a child Thing under an `eebus:oh-device` Bridge
- WHEN the Thing type picker for that Bridge is used
- THEN both `eebus:oh-entity` and `eebus:oh-eg-entity` are offered as valid child types

#### Scenario: oh-eg-entity is not offered under oh-cs-device

- GIVEN a user is adding a child Thing under an `eebus:oh-cs-device` Bridge
- WHEN the Thing type picker for that Bridge is used
- THEN `eebus:oh-eg-entity` is not offered as a valid child type (only `eebus:oh-cs-entity` is)

### Requirement: oh-eg-entity's LPC/LPP Channels are statically declared

The binding SHALL declare `eebus:oh-eg-entity`'s `lpc`/`lpp` Channel Groups (each with
`limit-active`, `limit-value`, `failsafe-limit-value`, `failsafe-duration-minimum`) statically in
`thing-types.xml`, present on every instance from Thing creation, rather than created dynamically
on the first EEBus event the way `eebus:oh-entity`'s Channels are (docs/ADR/014,
docs/ADR/015) - the same mechanism docs/ADR/025 introduced for `eebus:oh-cs-entity`.

#### Scenario: Channels exist immediately after Thing creation

- GIVEN a user creates a new `eebus:oh-eg-entity` Thing under a trusted `eebus:oh-device` Bridge
- WHEN the Thing is created, before any LPC/LPP EEBus event has occurred
- THEN its `lpc#limit-active`, `lpc#limit-value`, `lpc#failsafe-limit-value`,
  `lpc#failsafe-duration-minimum`, `lpp#limit-active`, `lpp#limit-value`,
  `lpp#failsafe-limit-value`, and `lpp#failsafe-duration-minimum` Channels all already exist
  (state `NULL` until the first real update, same as any Channel before its first update)

#### Scenario: Channels stay NULL if LPC/LPP is never enabled on the parent Bridge

- GIVEN an `eebus:oh-eg-entity` Thing whose parent `eebus:oh-device` Bridge does not have `LPC`
  or `LPP` enabled in `supportedUseCasesClient`
- WHEN any amount of time passes
- THEN its `lpc`/`lpp` Channels remain statically present but never receive an update (stay
  `NULL`) - this is an accepted, documented consequence of not having a dedicated convenience
  Bridge (see docs/ADR/026-oh-eg-entity-static-channels.md "Consequences")

#### Scenario: An EnergyGuard-role status update still reaches a statically declared Channel

- GIVEN an `eebus:oh-eg-entity` Thing under an `eebus:oh-device` Bridge with `LPC`/`LPP` enabled
  in `supportedUseCasesClient`, whose Channels were statically declared at creation
- WHEN `AbstractEEBusLimitEnergyGuardUseCase` calls `applyLimitStatus`/`applyFailsafeStatus` on
  its resolved `EEBusOhEntityHandler`
- THEN the pre-existing Channel is updated via `updateCachedState`, without `ensureChannel`
  creating a duplicate or erroring

### Requirement: oh-eg-entity reuses oh-entity's config and handler

The binding SHALL configure and handle `eebus:oh-eg-entity` using the exact same
`EEBusOhEntityConfiguration` (`ski`, `entityAddress`, `shipId`) and `EEBusOhEntityHandler` classes
`eebus:oh-entity`/`eebus:oh-cs-entity` use - no separate Java classes.

#### Scenario: Trust-status derivation is identical

- GIVEN an `eebus:oh-eg-entity` Thing configured with `ski=X` under an `eebus:oh-device` Bridge
  that does not currently trust `X`
- WHEN the Thing's status is derived
- THEN it goes `OFFLINE`/`CONFIGURATION_PENDING`, via the same `applyStatus()` logic
  `eebus:oh-entity` uses (`EEBusHandler#isTrusted(String)`)

#### Scenario: oh-eg-entity is resolvable by the transport layer

- GIVEN an `eebus:oh-eg-entity` Thing configured with `ski=X`, trusted by its parent
  `eebus:oh-device` Bridge
- WHEN `EEBusHandler#ohEntityHandlerForCommunicationAddress` resolves the paired
  ControllableSystem peer (e.g. from `AbstractEEBusLimitEnergyGuardUseCase`)
- THEN the `eebus:oh-eg-entity` Thing's handler is returned, exactly as an `eebus:oh-entity`
  Thing's handler would be

### Requirement: oh-eg-entity's ski parameter offers the parent Bridge's trusted SKIs

The binding SHALL offer `eebus:oh-eg-entity`'s `ski` config parameter the same selectable list of
the parent `eebus:oh-device` Bridge's currently trusted SKIs that `eebus:oh-entity`'s `ski`
parameter already offers.

#### Scenario: ski options resolve via the parent oh-device Bridge

- GIVEN a user is configuring a new `eebus:oh-eg-entity` Thing under an `eebus:oh-device` Bridge
- WHEN the `ski` parameter's option list is requested
- THEN it returns the parent Bridge's currently configured `trustedSkis`, the same way it would
  for an `eebus:oh-entity` Thing under the same Bridge

## Migration

None. This change is purely additive - no existing Thing type's `supported-bridge-type-refs` or
runtime behavior changes. See docs/ADR/026-oh-eg-entity-static-channels.md "Migration".
