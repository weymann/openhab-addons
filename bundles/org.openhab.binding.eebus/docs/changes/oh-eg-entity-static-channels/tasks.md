# Tasks: `oh-eg-entity` Thing type with statically declared LPC/LPP Channels

## 1. thing-types.xml

- [x] 1.1 New `<thing-type id="oh-eg-entity">` - `supported-bridge-type-refs` = `oh-device` only
      (additional, not exclusive - `oh-entity` keeps the same ref); config-description =
      `ski`/`entityAddress`/`shipId` (same shape as `oh-entity`'s/`oh-cs-entity`'s);
      `<channel-groups>` referencing the existing `lpc`/`lpp` `channel-group-type`s (unchanged
      Channel definitions themselves)
- [x] 1.2 `oh-device`'s introducing comment updated to mention both `oh-entity` and
      `oh-eg-entity` as valid children
- [x] 1.3 `oh-entity`'s own `supported-bridge-type-refs`/description left unchanged (still
      `oh-device` only, unaffected by this change)
- [x] 1.4 Re-verified well-formed via `xml.dom.minidom.parse` after all edits

## 2. Constants and factory wiring

- [x] 2.1 `EEBusBindingConstants`: new `THING_TYPE_OH_EG_ENTITY` constant (`oh-eg-entity`)
- [x] 2.2 `EEBusHandlerFactory`: `SUPPORTED_THING_TYPES_UIDS` includes `THING_TYPE_OH_EG_ENTITY`;
      `createHandler` has a new branch constructing `EEBusOhEntityHandler` for it (same class as
      `oh-entity`/`oh-cs-entity`, no new handler class needed)

## 3. Config/handler class reuse (no behavior changes, javadoc only)

- [x] 3.1 `EEBusOhEntityConfiguration`: class javadoc updated to note it is also used by
      `oh-eg-entity`
- [x] 3.2 `EEBusOhEntityHandler`: class javadoc updated to note it also serves `oh-eg-entity`,
      including the non-exclusivity and no-guarantee-of-LPC/LPP caveats
- [x] 3.3 Confirmed no change needed in `EEBusHandler#ohEntityHandlerForSki`/
      `#ohEntityHandlerForCommunicationAddress` (already type-agnostic) or in
      `AbstractEEBusLimitEnergyGuardUseCase`/`AbstractEEBusLimitControllableSystemUseCase`
      (already call through the resolved handler without inspecting its Thing type)

## 4. Ski option provider

- [x] 4.1 `EEBusSkiOptionProvider`: `oh-eg-entity`'s `ski` parameter served the same way
      `oh-entity`'s/`oh-cs-entity`'s already are (options = target Bridge's `trustedSkis`)
- [x] 4.2 `targetBridgeUid`: resolves an `oh-eg-entity` context Thing to its parent Bridge the
      same way an `oh-entity` context Thing already does (the Bridge-type branch, `oh-device`,
      needed no change - already handled)

## 5. Documentation

- [x] 5.1 `docs/ADR/026-oh-eg-entity-static-channels.md` (new, Accepted) - refines ADR-025 by
      applying its pattern to the Client-role side, supersedes nothing
- [x] 5.2 `docs/changes/oh-eg-entity-static-channels/{proposal.md,specs/oh-eg-entity/spec.md,
      tasks.md}` (this proposal)
- [x] 5.3 `README.md`: Supported Things (new `oh-eg-entity` bullet), Trust section (step 3
      mentions the new type), Channels section (new exception paragraph + table row)
- [x] 5.4 `CONCEPT.md`: new decided-subsection (§4.9) plus §7 checklist entry (23)

## 6. Self-QA

- [x] 6.1 Brace/paren balance checked on every touched `.java` file (all balanced)
- [x] 6.2 `thing-types.xml` re-parsed with `xml.dom.minidom` after every edit (well-formed
      throughout)
- [x] 6.3 CRLF preserved on every touched `.java`/ADR/changes-doc/`thing-types.xml`/`README.md`
      file (verified via `file` before and after); `CONCEPT.md` kept LF-only
- [x] 6.4 Grep sweep confirms `THING_TYPE_OH_EG_ENTITY`/`oh-eg-entity` appear only where intended

## 7. Verification (user-owned, same pattern as every prior ADR)

- [ ] 7.1 `mvn clean install` - no Maven available in the editing sandbox
- [ ] 7.2 Live retest: under an `eebus:oh-device` Bridge, confirm both `eebus:oh-entity` and
      `eebus:oh-eg-entity` are offered; create an `oh-eg-entity`, confirm its `lpc`/`lpp` Channels
      are already visible before any EEBus event has occurred
- [ ] 7.3 Live retest: with `LPC`/`LPP` enabled in the parent Bridge's `supportedUseCasesClient`,
      pair a real/test Controllable System, confirm `applyLimitStatus`/`applyFailsafeStatus`
      correctly update the pre-existing Channels on the `oh-eg-entity` Thing (no duplicate
      Channel created, no error)
- [ ] 7.4 Live sanity check: an `eebus:oh-eg-entity` Thing created under an `oh-device` Bridge
      that does _not_ have LPC/LPP enabled stays at `NULL` on its `lpc`/`lpp` Channels, with no
      error logged - confirms the documented (accepted) consequence in ADR-026 behaves as
      expected rather than throwing

---
