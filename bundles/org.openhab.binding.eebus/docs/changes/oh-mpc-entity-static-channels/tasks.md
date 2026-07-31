# Tasks: Dedicated `oh-mpc-entity` Thing type with statically declared MPC Channel

## 1. thing-types.xml

- [x] 1.1 New `<thing-type id="oh-mpc-entity">` - `supported-bridge-type-refs` = `oh-device`; config-description = `ski`/`entityAddress`/`shipId` (same shape as `oh-entity`'s); `<channel-groups>` referencing the existing `mpc` `channel-group-type` (unchanged Channel definition itself)
- [x] 1.2 Introducing XML comment (mirrors `oh-cs-entity`/`oh-eg-entity`'s) explaining: additive (not exclusive) under `oh-device`, implies MPC Client unconditionally via `deriveLocalUseCases`, no dedicated convenience Bridge
- [x] 1.3 Re-verified well-formed via `xml.dom.minidom.parse` after all edits
- [x] 1.4 CRLF line endings preserved (verify with `file` before/after, per this repo's convention)

## 2. Constants and factory wiring

- [x] 2.1 `EEBusBindingConstants`: new `THING_TYPE_OH_MPC_ENTITY` constant (`oh-mpc-entity`)
- [x] 2.2 `EEBusHandlerFactory`: `SUPPORTED_THING_TYPES_UIDS` includes `THING_TYPE_OH_MPC_ENTITY`; `createHandler` has a new branch constructing `EEBusOhEntityHandler` for it (same class as `oh-entity`/`oh-cs-entity`/`oh-eg-entity`, no new handler class needed)

## 3. Local Use-Case derivation

- [x] 3.1 `EEBusHandler#deriveLocalUseCases`: new `else if (THING_TYPE_OH_MPC_ENTITY.equals(childType))` branch adding `"MPC"` to `clientUseCaseKeys` - mirrors the existing `oh-eg-entity` branch's `clientUseCaseKeys.add("LPC"); clientUseCaseKeys.add("LPP");` pattern
- [x] 3.2 Method javadoc's bullet list updated to document the new branch, consistent with the existing `oh-cs-entity`/`oh-eg-entity` bullets

## 4. Config/handler class reuse (no behavior changes, javadoc only)

- [x] 4.1 `EEBusOhEntityConfiguration`: class javadoc updated to note it is also used by `oh-mpc-entity`
- [x] 4.2 `EEBusOhEntityHandler`: class javadoc updated to note it also serves `oh-mpc-entity`
- [x] 4.3 Confirmed no change needed in `EEBusHandler#ohEntityHandlerForSki`/`#ohEntityHandlerForCommunicationAddress` (already type-agnostic, matches by `instanceof EEBusOhEntityHandler`) or in `EEBusMpcClientUseCase` (already calls through the resolved handler without inspecting its Thing type)

## 5. Ski option provider

- [x] 5.1 `EEBusSkiOptionProvider`: `oh-mpc-entity`'s `ski` parameter served the same way `oh-entity`/`oh-cs-entity`/`oh-eg-entity`'s already is (options = target Bridge's `trustedSkis`)
- [x] 5.2 `targetBridgeUid`: resolves an `oh-mpc-entity` context Thing to its parent Bridge the same way the other three Entity Thing types already do

## 6. Documentation

- [x] 6.1 New ADR (`docs/ADR/0XX-oh-mpc-entity-static-channels.md`, next available number) - refines ADR-014/ADR-025/ADR-026/ADR-027, supersedes none
- [x] 6.2 `docs/changes/oh-mpc-entity-static-channels/{proposal.md,specs/oh-mpc-entity/spec.md,tasks.md}` (this change)
- [x] 6.3 `README.md`: Supported Things (new `oh-mpc-entity` bullet), Channels table (note that `oh-mpc-entity`'s Channel is present from creation, not "appears once detected")
- [x] 6.4 `CONCEPT.md`: new decided-subsection plus 7 checklist entry

## 7. Self-QA

- [x] 7.1 Brace/paren balance checked on every touched `.java` file
- [x] 7.2 `thing-types.xml` re-parsed with `xml.dom.minidom` after every edit
- [x] 7.3 CRLF preserved on every touched `.java`/ADR/changes-doc/`thing-types.xml` file (verified via `file`/byte-count before and after); `CONCEPT.md` kept LF-only
- [x] 7.4 Grep sweep confirms `THING_TYPE_OH_MPC_ENTITY`/`oh-mpc-entity` appear only where intended

## 8. Verification (user-owned, same pattern as every prior ADR)

- [ ] 8.1 `mvn clean install` - no Maven available in the editing sandbox
- [ ] 8.2 Live retest: under an `eebus:oh-device` Bridge, confirm `eebus:oh-mpc-entity` is offered alongside `eebus:oh-entity`/`eebus:oh-cs-entity`/`eebus:oh-eg-entity`; create one, confirm its `mpc#power` Channel is already visible before any EEBus event has occurred
- [ ] 8.3 Live retest: pair against a real/test MPC Server peer, confirm `EEBusMpcClientUseCase` correctly updates the pre-existing `mpc#power` Channel (no duplicate Channel created, no error)
- [ ] 8.4 Live retest: confirm the Bridge's local Use-Case derivation includes `MPC` Client purely from the `oh-mpc-entity` child's presence, with no `MPC` checkbox set on any `oh-entity` child

---
