# Tasks: Dedicated `oh-cs-entity` Thing type with statically declared LPC/LPP Channels

## 1. thing-types.xml

- [x] 1.1 New `<thing-type id="oh-cs-entity">` - `supported-bridge-type-refs` = `oh-cs-device`
      only; config-description = `ski`/`entityAddress`/`shipId` (same shape as `oh-entity`'s);
      `<channel-groups>` referencing the existing `lpc`/`lpp` `channel-group-type`s (unchanged
      Channel definitions themselves)
- [x] 1.2 `oh-entity`'s `<supported-bridge-type-refs>` narrowed back to `oh-device` only (removes
      the `oh-cs-device` ref added as ADR-024's Post-acceptance fix)
- [x] 1.3 `oh-cs-device`'s introducing comment updated to point at `oh-cs-entity` instead of
      `oh-entity` as its child Entity Thing
- [x] 1.4 Re-verified well-formed via `xml.dom.minidom.parse` after all edits

## 2. Constants and factory wiring

- [x] 2.1 `EEBusBindingConstants`: new `THING_TYPE_OH_CS_ENTITY` constant (`oh-cs-entity`);
      `THING_TYPE_OH_ENTITY`'s javadoc updated (child of `oh-device` only now)
- [x] 2.2 `EEBusHandlerFactory`: `SUPPORTED_THING_TYPES_UIDS` includes `THING_TYPE_OH_CS_ENTITY`;
      `createHandler` has a new branch constructing `EEBusOhEntityHandler` for it (same class as
      `oh-entity`, no new handler class needed)

## 3. Config/handler class reuse (no behavior changes, javadoc only)

- [x] 3.1 `EEBusOhEntityConfiguration`: class javadoc updated to note it is also used by
      `oh-cs-entity`
- [x] 3.2 `EEBusOhEntityHandler`: class javadoc updated to note it also serves `oh-cs-entity`,
      and to explain why `ensureChannel` needs no special-casing for its statically declared
      Channels
- [x] 3.3 Confirmed no change needed in `EEBusHandler#ohEntityHandlerForSki`/
      `#ohEntityHandlerForCommunicationAddress` (already type-agnostic, matches by
      `instanceof EEBusOhEntityHandler`) or in `AbstractEEBusLimitControllableSystemUseCase`
      (already calls through the resolved handler without inspecting its Thing type)

## 4. Ski option provider

- [x] 4.1 `EEBusSkiOptionProvider`: `oh-cs-entity`'s `ski` parameter served the same way
      `oh-entity`'s already is (options = target Bridge's `trustedSkis`)
- [x] 4.2 `targetBridgeUid`: resolves an `oh-cs-entity` context Thing to its parent Bridge the
      same way an `oh-entity` context Thing already does

## 5. Documentation

- [x] 5.1 `docs/ADR/025-oh-cs-entity-static-channels.md` (new, Accepted) - refines ADR-023/024,
      supersedes neither
- [x] 5.2 `docs/changes/oh-cs-entity-static-channels/{proposal.md,specs/oh-cs-entity/spec.md,
      tasks.md}` (this proposal)
- [x] 5.3 `README.md`: Supported Things (new `oh-cs-entity` bullet, `oh-entity`/`oh-cs-device`
      bullets updated for the narrowed pairing), Trust section (step 3 differentiated by Bridge
      type), Channels table (note that `oh-cs-entity`'s Channels are present from creation, not
      "appears once detected")
- [x] 5.4 `CONCEPT.md`: new decided-subsection (§4.8) plus §7 checklist entry (22)

## 6. Self-QA

- [x] 6.1 Brace/paren balance checked on every touched `.java` file (all balanced)
- [x] 6.2 `thing-types.xml` re-parsed with `xml.dom.minidom` after every edit (well-formed
      throughout)
- [x] 6.3 CRLF preserved on every touched `.java`/ADR/changes-doc/`thing-types.xml` file
      (verified via `file`/byte-count before and after); `CONCEPT.md` kept LF-only
- [x] 6.4 Grep sweep confirms `THING_TYPE_OH_CS_ENTITY`/`oh-cs-entity` appear only where
      intended, and no stale `oh-entity`-under-`oh-cs-device` assumption remains in source

## 7. Verification (user-owned, same pattern as every prior ADR)

- [ ] 7.1 `mvn clean install` - no Maven available in the editing sandbox
- [ ] 7.2 Live retest: under an `eebus:oh-cs-device` Bridge, confirm `eebus:oh-cs-entity` is
      offered (and `eebus:oh-entity` is not); create one, confirm its `lpc`/`lpp` Channels are
      already visible before any EEBus event has occurred
- [ ] 7.3 Live retest: pair a real/test Energy Guard against the Bridge, confirm
      `applyLimitStatus`/`applyFailsafeStatus` correctly update the pre-existing Channels (no
      duplicate Channel created, no error)
- [ ] 7.4 If any `eebus:oh-entity` Thing exists under an `eebus:oh-cs-device` Bridge from before
      this change (only possible in the ADR-024-Post-acceptance-fix-to-here window): delete and
      recreate it as `eebus:oh-cs-entity` per ADR-025 "Migration"

---
