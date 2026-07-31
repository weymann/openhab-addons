# Tasks: LPC/LPP Client-Role Monitoring Channels

**Further scope cut made during $Dev (2026-08-12), not yet reflected in proposal.md/spec.md
above:** Scenario 4 (constraints, `nominal-max`/`contractual-nominal-max` via
`ElectricalConnectionCharacteristicListData`) is **also** deferred, alongside Scenario 2/3. Only
Scenario 1 (`limit-active`/`limit-value`/`limit-duration` via `LoadControlLimitListData`) is
implemented in this pass. Reason found while starting task 2.2: `FeatureTypeEnumType
.ELECTRICAL_CONNECTION`/`ElectricalConnectionCharacteristicListDataType` have **zero** prior
usage anywhere in this codebase (confirmed by search) - `EEBusMpcServerUseCase`'s own class
javadoc already explicitly defers `ElectricalConnection` characteristic/parameter setup as a
follow-up on the Server-role side ("the exact `ElectricalConnection` characteristic/parameter
setup ... left as a follow-up"). `LoadControl`, by contrast, is proven: `LoadControlLimitDataType`/
`LoadControlLimitDescriptionDataType`/`FeatureTypeEnumType.LOAD_CONTROL` are already compiled-and-
used by `AbstractEEBusLimitControllableSystemUseCase`. Implementing the unverified
`ElectricalConnection` read now would repeat a risk the codebase's own author already chose to
avoid elsewhere - deferring it here is consistent with that established judgment, not a new one.
`thing-types.xml` therefore only gains the Scenario 1 Channels in this pass; `nominal-max`/
`contractual-nominal-max` move to the same follow-up as Scenario 2.

**Second cut, same reasoning, found while implementing task 2.2:** `limit-duration`
(`timePeriod.endTime`) is **also** deferred. `TimePeriodType`/`getTimePeriod()` have zero prior
usage anywhere in this codebase (confirmed by search), unlike `getIsLimitActive()`/`getValue()`
on `LoadControlLimitDataType`, which are directly proven (used by
`AbstractEEBusLimitControllableSystemUseCase#onLimitWritten`/`#onStateChanged`). This pass
therefore implements exactly two Channels - `limit-active` (`Switch`) and `limit-value`
(`Number:Power`) - built entirely from API surface already compiled and exercised elsewhere in
this codebase, no new unverified symbol beyond the `LOAD_CONTROL_LIMIT_LIST_DATA`/
`LoadControlLimitListDataType`/`CmdType.withLoadControlLimitListData(...)` "list wrapper" naming
(same confidence tier as ADR-015 already accepted, now the _only_ remaining unverified surface
in this change instead of one of four).

## 1. Channel Type Declarations

- [x] 1.1 Added `channel-group-type` `lpc` and `lpp`, each referencing shared `channel-type`s
      `limit-active` (`Switch`, read-only) and `limit-value` (`Number:Power`, read-only) to
      `thing-types.xml` - **narrowed during $Dev**: `limit-duration`/`nominal-max`/
      `contractual-nominal-max` deferred (see note above). Same
      not-referenced-from-`oh-entity`-`channel-groups` pattern as `mpc` (docs/ADR/014). (Scenario:
      "LPC Channel Group created for a use case that is both configured and detected")
- [x] 1.2 `lpp` channel-group-type added with identical Channel structure (shares the same
      `limit-active`/`limit-value` channel-types as `lpc`, since both groups are structurally
      identical - simpler than duplicating the channel-types). (Scenario: "LPP status Channels
      appear after first successful resolution")
- [x] 1.3 Semantic tags added: `Status`/`Power` on `limit-active`, `Measurement`/`Power` on
      `limit-value`.

## 2. AbstractEEBusLimitEnergyGuardUseCase + EEBusLpcClientUseCase / EEBusLppClientUseCase

- [x] 2.1 Created `AbstractEEBusLimitEnergyGuardUseCase` (package `internal.transport`), mirroring
      `AbstractEEBusLimitControllableSystemUseCase` + `EEBusLpcServerUseCase`/
      `EEBusLppServerUseCase`. `getActor()` returns `"EnergyGuard"`. `getScenarioSupport()`
      covers Scenario 1 only (**narrowed twice during $Dev** - Scenario 4/Constraints also
      deferred, see note at top of this file; not just Scenario 2/3 as originally planned).
- [x] 2.2 `onUseCasePartnersFound`/`subscribeLimitStatus`/`applyLimitStatus` resolve only the
      peer's `LoadControl` feature (`loadControlLimitListData`) - `ElectricalConnection` dropped
      entirely from this pass (see note at top of this file). `CmdType.withLoadControlLimitListData`/
      `LoadControlLimitListDataType` remain unverified against the compiled jar - flagged in the
      class javadoc, gated by task 6.3.
- [x] 2.3 Created `EEBusLpcClientUseCase`/`EEBusLppClientUseCase` as thin subclasses.
- [x] 2.4 Constructor takes `Function<String, Optional<EEBusOhPeerHandler>>`, reusing
      `EEBusHandler#ohPeerHandlerForCommunicationAddress` unchanged.

## 3. EEBusOhPeerHandler Extensions

- [x] 3.1 Added `applyLimitStatus(String channelGroup, boolean active, double watts)` - **signature
      narrowed** from the originally planned `(..., @Nullable Instant endTime)`: no
      `limit-duration` parameter, since that Channel is deferred. Ensures/updates
      `<group>#limit-active`/`limit-value` via the existing `ensureChannel` helper.
- [ ] 3.2 `applyConstraints(...)` - **deferred**, see note at top of this file (Scenario 4 out of
      scope for this pass).
- [x] 3.3 Reused `recordDetectedUseCase` unchanged, with new keys
      `EEBusBindingConstants.USE_CASE_KEY_LPC`/`USE_CASE_KEY_LPP`.

## 4. Wiring in EEBusHandler

- [x] 4.1 Registered `EEBusLpcClientUseCase`/`EEBusLppClientUseCase` in `clientUseCases` when
      `LPC`/`LPP` is present in `cfg.supportedUseCasesClient`; `unimplementedClientUseCases`
      filter updated to `Set.of("MPC", "LPC", "LPP")`.

## 5. Persistence (reused, verify unchanged)

- [x] 5.1 Confirmed: `applyLimitStatus`/`recordDetectedUseCase` only ever touch Channels/
      properties they own; `unpair()` itself was not modified by this change.

## 6. Tests

- [x] 6.1 Added `whenApplyLimitStatusCalledForLpcThenChannelsCreatedAndStateUpdated`,
      `whenApplyLimitStatusCalledForLppThenChannelsCreatedAndStateUpdated`,
      `whenApplyLimitStatusCalledTwiceThenChannelsAreNotDuplicated` to `EEBusOhPeerHandlerTest`.
- [x] 6.2 Reused MPC's `whenRecordDetectedUseCaseCalledThenThingPropertyIsSet` pattern - not
      duplicated with an LPC-specific test since `recordDetectedUseCase` takes the key as a plain
      `String` parameter with no LPC/LPP-specific branching to exercise separately.
- [ ] 6.3 **Known gap, larger than MPC's:** no SPINE-level end-to-end test for
      `EEBusLpcClientUseCase`/`EEBusLppClientUseCase`, and - unlike MPC - the
      `CmdType`/`LoadControlLimitListDataType` surface used in
      `AbstractEEBusLimitEnergyGuardUseCase` has not been compile-checked at all in this session
      (no Maven/local `.m2` in this sandbox). **`mvn compile` (or a full `mvn spotless:apply` →
      `clean install` per the project's `$Release` process) must be run locally before this
      change is considered done** - this is the single most important open item from this `$Dev`
      pass.

## 7. Documentation

- [x] 7.1 `README.md` Channels table updated with `oh-entity`/`lpc`/`lpp` rows, noting
      `limit-duration` and Scenario 2/4 are not yet exposed.
- [x] 7.2 CONCEPT.md §5.4.3 already documents the field-level mapping (2026-08-12) - no further
      edits made; the Scenario 4/timePeriod deferrals are tracked here in tasks.md, not
      backported into CONCEPT.md's reference table (which still correctly describes the full
      spec, independent of what this implementation pass chose to build first).

---

_Change ID: `lpc-lpp-client-role-channels`._
