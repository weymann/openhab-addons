# Tasks: Derive Local Use Cases From Entity Children

## 1. thing-types.xml

- [x] 1.1 Remove the `oh-cs-device` `bridge-type` block entirely.
- [x] 1.2 Remove `supportedUseCasesClient`/`supportedUseCasesServer` `parameter`s from the
      `oh-device` `bridge-type`.
- [x] 1.3 Add `supportedUseCasesClient`/`supportedUseCasesServer` `parameter`s to the `oh-entity`
      `thing-type` (same option list/labels as previously on the Bridge).
- [x] 1.4 Add `failsafeConsumptionLimitSeedWatts`/`failsafeProductionLimitSeedWatts`/
      `failsafeDurationMinimumSeedSeconds` `parameter`s to the `oh-cs-entity` `thing-type`.
- [x] 1.5 Change `oh-cs-entity`'s `<supported-bridge-type-refs>` from `cs-device` to `oh-device`.
- [x] 1.6 Re-parse the file for well-formedness after every edit.

## 2. Config POJOs

- [x] 2.1 `EEBusConfiguration`: remove `supportedUseCasesClient`/`supportedUseCasesServer`/
      `failsafeConsumptionLimitSeedWatts`/`failsafeProductionLimitSeedWatts`/
      `failsafeDurationMinimumSeedSeconds`; update class javadoc.
- [x] 2.2 `EEBusOhEntityConfiguration`: add the same five fields; update class javadoc to explain
      which fields apply to which of the three Entity Thing types.

## 3. Local Use-Case derivation

- [x] 3.1 `EEBusHandler#initialize()`/`startShipSpine()`: replace `cfg.supportedUseCasesClient`/
      `supportedUseCasesServer` reads with a scan over `getThing().getThings()`, unioning each
      `oh-entity` child's own configured lists, each `oh-cs-entity` child's fixed LPC+LPP Server
      (seeded from that child's own failsafe fields), each `oh-eg-entity` child's fixed LPC+LPP
      Client.
- [x] 3.2 Remove the `THING_TYPE_OH_CS_DEVICE`-gated branch in `startShipSpine()`; its logic
      folds into the `oh-cs-entity`-per-child case from 3.1.
- [x] 3.3 Handle zero-children startup (no Use Cases at all) the same way the checkbox-empty case
      was handled before - no error, just an empty local Use Case list.

## 4. Change propagation

- [x] 4.1 New `EEBusEntityChangeListener` interface (`internal.handler` package).
- [x] 4.2 `EEBusHandler implements EEBusEntityChangeListener`; `onEntityChanged()` triggers the
      same dispose-then-initialize path already used for `initialize()`/`dispose()`.
- [x] 4.3 `EEBusOhEntityHandler#initialize()`/`dispose()`: call the parent Bridge's
      `onEntityChanged()` via `getBridge()`, guarding for `null` Bridge/handler.

## 5. Remove trust Actions and oh-cs-device references

- [x] 5.1 Delete `EEBusDeviceActions`.
- [x] 5.2 `EEBusHandler`: remove `getServices()`'s `EEBusDeviceActions` registration (or the
      whole override if nothing else needs it), `trust(String)`, `untrust(String)`,
      `persistTrustedSkis(List)`, `recomputeTrustedSkis()`.
- [x] 5.3 `EEBusBindingConstants`: remove `THING_TYPE_OH_CS_DEVICE`.
- [x] 5.4 `EEBusHandlerFactory`: remove the `oh-cs-device` branch and its entry in
      `SUPPORTED_THING_TYPES_UIDS`.
- [x] 5.5 `EEBusMdnsDiscoveryParticipant#isOwnService`: drop the `THING_TYPE_OH_CS_DEVICE` check.
- [x] 5.6 `EEBusSkiOptionProvider`: drop every `THING_TYPE_OH_CS_DEVICE` reference
      (`getParameterOptions`, `knownSkisExcludingAlreadyTrusted`, `targetBridgeUid`); update
      class javadoc.

## 6. Documentation

- [x] 6.1 README.md: new section on the automatic full-rebuild-on-any-change behavior and the
      optional disable-before-batching practice; remove/update any `oh-cs-device`/`trust()`/
      `untrust()` mentions.
- [x] 6.2 CONCEPT.md: append a dated addendum recording this decision.

## 7. Verification

- [x] 7.1 Re-parse `thing-types.xml`.
- [x] 7.2 Brace/paren balance check on every touched `.java` file.
- [x] 7.3 Grep for leftover `THING_TYPE_OH_CS_DEVICE`/`EEBusDeviceActions`/`trust(`/`untrust(`
      references across `src/`.
- [x] 7.4 Confirm CRLF preserved on every touched CRLF file, LF preserved on `CONCEPT.md`.

---
