# Delta for `oh-mpc-entity` Static-Channel Thing Type

## ADDED Requirements

### Requirement: Dedicated oh-mpc-entity Thing type

The binding SHALL provide a Thing type `eebus:oh-mpc-entity`, representing one SPINE Entity trusted by a parent `eebus:oh-device` Bridge, distinct from the generic `eebus:oh-entity`, dedicated to the MPC Client role.

#### Scenario: Adding an oh-mpc-entity Thing

- GIVEN a user is adding a child Thing under an `eebus:oh-device` Bridge
- WHEN the Thing type picker for that Bridge is used
- THEN `eebus:oh-mpc-entity` is offered alongside `eebus:oh-entity`, `eebus:oh-cs-entity`, and `eebus:oh-eg-entity`

### Requirement: oh-mpc-entity is additive under oh-device, not exclusive

The binding SHALL allow `eebus:oh-mpc-entity` and `eebus:oh-entity` to coexist as children of the same `eebus:oh-device` Bridge - neither restricts or replaces the other.

#### Scenario: oh-entity remains available alongside oh-mpc-entity

- GIVEN an `eebus:oh-device` Bridge with an existing `eebus:oh-mpc-entity` child
- WHEN a user adds another child Thing under the same Bridge
- THEN `eebus:oh-entity` is still offered as a valid choice

### Requirement: oh-mpc-entity's MPC Channel is statically declared

The binding SHALL declare `eebus:oh-mpc-entity`'s `mpc` Channel Group (`mpc#power`, `Number:Power`, read-only - the existing `channel-group-type`/`channel-type` from `docs/ADR/014-dynamic-client-role-channels.md`, unchanged) statically in `thing-types.xml`, present on every instance from Thing creation, rather than created dynamically on the first successful measurement resolution the way `eebus:oh-entity`'s `mpc` Channel is.

#### Scenario: Channel exists immediately after Thing creation

- GIVEN a user creates a new `eebus:oh-mpc-entity` Thing under a trusted `eebus:oh-device` Bridge
- WHEN the Thing is created, before any MPC measurement has been resolved
- THEN its `mpc#power` Channel already exists (state `NULL` until the first real update, same as any Channel before its first update)

#### Scenario: A measurement update still reaches the statically declared Channel

- GIVEN an `eebus:oh-mpc-entity` Thing whose `mpc#power` Channel was statically declared at creation
- WHEN `EEBusMpcClientUseCase` resolves a measurement and calls `applyMpcPower` on its resolved `EEBusOhEntityHandler`
- THEN the pre-existing Channel is updated, without a duplicate Channel being created or an error occurring

### Requirement: oh-mpc-entity reuses oh-entity's config and handler

The binding SHALL configure and handle `eebus:oh-mpc-entity` using the exact same `EEBusOhEntityConfiguration` (`ski`, `entityAddress`, `shipId`) and `EEBusOhEntityHandler` classes `eebus:oh-entity`/`eebus:oh-cs-entity`/`eebus:oh-eg-entity` use - no separate Java classes.

#### Scenario: Trust-status derivation is identical

- GIVEN an `eebus:oh-mpc-entity` Thing configured with `ski=X` under an `eebus:oh-device` Bridge that does not currently trust `X`
- WHEN the Thing's status is derived
- THEN it goes `OFFLINE`/`CONFIGURATION_PENDING`, via the same status logic `eebus:oh-entity` uses

#### Scenario: oh-mpc-entity is resolvable by the transport layer

- GIVEN an `eebus:oh-mpc-entity` Thing configured with `ski=X`, trusted by its parent `eebus:oh-device` Bridge
- WHEN `EEBusHandler#ohEntityHandlerForSki("X")` (or the communication-address equivalent) is called from `EEBusMpcClientUseCase`
- THEN the `eebus:oh-mpc-entity` Thing's handler is returned, exactly as an `eebus:oh-entity` Thing's handler would be

### Requirement: oh-mpc-entity's presence implies MPC Client on the parent Bridge

The binding SHALL treat an `eebus:oh-mpc-entity` child's mere presence under an `eebus:oh-device` Bridge as sufficient, by itself, to include `MPC` in that Bridge's derived local Client Use-Case set (`docs/ADR/027-derive-local-use-cases-from-entities.md`) - no checkbox required.

#### Scenario: Bridge derives MPC Client from an oh-mpc-entity child

- GIVEN an `eebus:oh-device` Bridge with an `eebus:oh-mpc-entity` child, and no `eebus:oh-entity` child configured with `MPC` in `supportedUseCasesClient`
- WHEN the Bridge's local SPINE Device is (re)built
- THEN `MPC` Client is included in the built Device's local Use Cases, exactly as if it had been checked on an `eebus:oh-entity` child

#### Scenario: No duplicate MPC Client Use Case when both sources contribute it

- GIVEN an `eebus:oh-device` Bridge with both an `eebus:oh-mpc-entity` child and an `eebus:oh-entity` child that also has `MPC` checked in `supportedUseCasesClient`
- WHEN the Bridge's local SPINE Device is (re)built
- THEN exactly one `MPC` Client Use-Case implementation is built for the local CEM Entity, not two

## Migration

None needed. This is purely additive: no existing Thing type's `supported-bridge-type-refs` or behavior changes. An existing `eebus:oh-entity` Thing configured for MPC Client continues to work unchanged; a user who wants the statically-declared Channel instead may create a new `eebus:oh-mpc-entity` Thing alongside or in place of it.
