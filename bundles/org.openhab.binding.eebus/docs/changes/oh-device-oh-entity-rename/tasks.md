# Tasks: EEBUS-vocabulary Thing-model rename and Bridge-level trust relocation

## 1. Thing types and constants

- [x] 1.1 `EEBusBindingConstants`: `THING_TYPE_SERVICE`->`THING_TYPE_OH_DEVICE` (`oh-device`),
      `THING_TYPE_CS_SERVICE`->`THING_TYPE_OH_CS_DEVICE` (`oh-cs-device`),
      `THING_TYPE_PEER`->`THING_TYPE_HW_DEVICE` (`hw-device`),
      `THING_TYPE_OH_PEER`->`THING_TYPE_OH_ENTITY` (`oh-entity`); `PROPERTY_PAIRED` removed
      entirely (trust no longer a per-Entity Thing property)
- [x] 1.2 `thing-types.xml`: rename all four ids/labels/descriptions, `oh-entity`'s
      `<supported-bridge-type-refs>` updated to `oh-device`/`oh-cs-device`
- [x] 1.3 `EEBusHandlerFactory`: supported-types set and `createHandler` updated to the new
      constants/handler classes

## 2. Configuration

- [x] 2.1 `EEBusConfiguration` (Bridge, shared by `oh-device`/`oh-cs-device`): new
      `trustedSkis` field (`List<String>`, default `List.of()`) + `PARAM_TRUSTED_SKIS` constant,
      matching pattern of the existing `PARAM_PORT` constant
- [x] 2.2 `thing-types.xml`: new `trustedSkis` config parameter (`type="text" multiple="true"`) on
      both `oh-device` and `oh-cs-device`
- [x] 2.3 `EEBusOhPeerConfiguration` renamed to `EEBusOhEntityConfiguration` (file + class); new
      `entityAddress` field (`String`, default `""`); `ski`'s javadoc updated - configuring it no
      longer grants trust, trust now lives on the parent Bridge
- [x] 2.4 `thing-types.xml`: `oh-entity`'s `ski` parameter label changed from "Trusted SKI" to
      "Device SKI" (accurately reflects that trust is no longer this Thing's own concern); new
      `entityAddress` parameter added
- [x] 2.5 `EEBusPeerConfiguration` renamed to `EEBusHwDeviceConfiguration` (file + class, no
      field/behavior change beyond the rename)

## 3. Handler classes

- [x] 3.1 `EEBusPeerHandler` renamed to `EEBusHwDeviceHandler` (file + class, no behavior change)
- [x] 3.2 `EEBusOhPeerHandler` renamed to `EEBusOhEntityHandler` (file + class):
      `pair()`/`unpair()`/`isPaired()`/`requestTrustedSkiRecompute()` removed; `applyStatus()`
      now asks the parent `EEBusHandler` for `isTrusted(cfg.ski)` instead of its own `isPaired()`
- [x] 3.3 `EEBusHandler`: `currentPairedOhPeerSkis()` replaced by reading the Bridge's own
      `config.trustedSkis` directly; `recomputeTrustedSkis()` reads that instead of child
      Things' `paired` property, and additionally refreshes every child `EEBusOhEntityHandler`'s
      status after pushing the new trust set to `ShipCommunication` (previously this direction -
      Bridge push down to children - was not needed, since the old design only ever changed
      trust from a child Thing's own Action)
- [x] 3.4 `EEBusHandler`: new package-private `boolean isTrusted(String ski)`,
      `void trust(String ski)`, `void untrust(String ski)` - the latter two mutate
      `config.trustedSkis` via `editConfiguration()`/`updateConfiguration()` (the same
      documented, non-reentrant pattern already used by `persistAutoAssignedPort`), refresh the
      in-memory `config` field, then call `recomputeTrustedSkis()`
- [x] 3.5 `EEBusHandler`: `childHandlerInitialized`/`childHandlerDisposed` overrides removed -
      no longer needed now that trust is not derived from child Thing existence/properties
- [x] 3.6 `EEBusHandler`: `ohPeerHandlerForSki`->`ohEntityHandlerForSki`,
      `ohPeerHandlerForCommunicationAddress`->`ohEntityHandlerForCommunicationAddress`; javadoc
      on the former notes the known limitation that it resolves the first matching
      `eebus:oh-entity` child by `ski` only (Device-level), not `entityAddress` - see
      proposal.md's "Out of scope"
- [x] 3.7 `EEBusOhPeerActions` replaced by `EEBusDeviceActions` (new file, old file content
      replaced) - Bridge-level `@RuleAction`s `trust(String ski)`/`untrust(String ski)` (each
      with one `@ActionInput` for `ski`), delegating to the new `EEBusHandler#trust`/`#untrust`;
      registered via `EEBusHandler#getServices()` (new override - the Bridge did not expose any
      `ThingHandlerService` before)
- [x] 3.8 `EEBusOhPeerSkiOptionProvider` renamed to `EEBusSkiOptionProvider`, extended to serve
      two parameters instead of one: `oh-entity`'s `ski` (options = the target Bridge's own
      `trustedSkis`, i.e. only SKIs actually usable today - free text remains possible for a SKI
      not yet trusted) and `oh-device`/`oh-cs-device`'s `trustedSkis` (options = known
      `hw-device` Things + other Bridges' own `PROPERTY_LOCAL_SKI`, excluding the target
      Bridge's own SKI and SKIs already in its current `trustedSkis` - same source set as the
      old `oh-peer` ski provider, re-pointed at the Bridge-level parameter)

## 4. Discovery

- [x] 4.1 `EEBusMdnsDiscoveryParticipant`: `THING_TYPE_PEER`->`THING_TYPE_HW_DEVICE`,
      `THING_TYPE_SERVICE`->`THING_TYPE_OH_DEVICE`, `EEBusPeerConfiguration`->
      `EEBusHwDeviceConfiguration`
- [x] 4.2 (found while touching this file) `isOwnService()` only checked `THING_TYPE_SERVICE`,
      never `THING_TYPE_CS_SERVICE` - an `eebus:cs-service` Bridge's own mDNS self-announcement
      was not excluded from Inbox suggestions, unlike an `eebus:service` Bridge's. Fixed to check
      both `THING_TYPE_OH_DEVICE` and `THING_TYPE_OH_CS_DEVICE`, pre-existing gap unrelated to
      the rename itself, same "found via live testing" pattern as ADR-023 task 1.4 (here found
      via code reading rather than a live test, since no Maven/live instance is available in this
      sandbox)

## 5. Transport layer (mechanical rename only, no behavior change)

- [x] 5.1 `AbstractEEBusLimitControllableSystemUseCase`, `AbstractEEBusLimitEnergyGuardUseCase`,
      `EEBusLpcClientUseCase`, `EEBusLpcServerUseCase`, `EEBusLppClientUseCase`,
      `EEBusLppServerUseCase`, `EEBusMpcClientUseCase`: `EEBusOhPeerHandler`->
      `EEBusOhEntityHandler`, `ohPeerHandlerResolver`->`ohEntityHandlerResolver`,
      `ohPeerHandler` (local vars/params)->`ohEntityHandler`,
      `energyGuardOhPeerHandler`->`energyGuardOhEntityHandler`
- [x] 5.2 `EEBusMetadataService`: comment reference to `EEBusOhPeerActions`->`EEBusDeviceActions`
- [x] 5.3 `EEBusHandler`'s use-case wiring block: `THING_TYPE_CS_SERVICE`->
      `THING_TYPE_OH_CS_DEVICE`, `this::ohPeerHandlerForCommunicationAddress`->
      `this::ohEntityHandlerForCommunicationAddress`

## 6. Documentation

- [x] 6.1 `docs/ADR/024-oh-device-oh-entity-rename.md` - supersedes ADR-012
- [x] 6.2 `README.md`: Thing/Bridge naming table, pairing-flow description (Bridge-level trust
      instead of a per-Thing Action), Channel table's `oh-peer` column
- [x] 6.3 `CONCEPT.md`: new dated section (2026-08-23) documenting the rename + trust relocation,
      cross-reference notes added to §4.5/§4.6 pointing at it, §7 checklist entry added -
      historical entries themselves are left as a record of what was decided at the time (same
      convention as every prior ADR "amending" rather than rewriting an earlier one)

## 7. Verification (user-owned, same pattern as ADR-016 through ADR-023)

- [ ] 7.1 `mvn clean install` - no Maven available in the editing sandbox; self-review only so
      far (brace/paren balance checked per touched `.java` file, `thing-types.xml` re-parsed for
      well-formedness, CRLF preserved on every touched file, grep sweep for leftover old
      identifiers)
- [ ] 7.2 Live retest: add an `eebus:oh-device` Bridge, add an `eebus:oh-entity` Thing under it
      with an untrusted `ski`, confirm `OFFLINE`/`CONFIGURATION_PENDING`; invoke `trust(ski)` on
      the Bridge, confirm the Thing goes `ONLINE` without a Bridge restart; invoke `untrust(ski)`,
      confirm it goes back to `OFFLINE`/`CONFIGURATION_PENDING`
- [ ] 7.3 Live retest: confirm `eebus:oh-cs-device`'s task-4.2 discovery fix - its own SKI is no
      longer offered back as an `eebus:hw-device` Inbox suggestion

---
