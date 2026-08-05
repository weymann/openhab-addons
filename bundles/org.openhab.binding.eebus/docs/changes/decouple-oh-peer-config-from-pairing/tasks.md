# Tasks: Decouple eebus:oh-peer Configuration from Pairing

## 1. Configuration Options

- [x] 1.1 Implement a `ConfigOptionProvider` for `eebus:oh-peer`'s `ski` parameter, sourced from
      `eebus:peer` Things (device name + SKI) and other local `eebus:service` Bridges' own SKI
      (Bridge name + SKI). (Scenarios: "Options include a discovered real device", "Options
      include another local service's own identity") — `EEBusOhPeerSkiOptionProvider`. **Known
      gap, not found until writing this:** the target Bridge (needed for 1.2's exclusions) can
      only be resolved from the `ConfigOptionProvider` `context` argument, which is not
      guaranteed to be populated for a brand-new Thing that does not exist in the `ThingRegistry`
      yet (mid-`Add Thing`-wizard). Degrades gracefully to the full, unscoped list in that case —
      see the class javadoc "Known limitation". `$QA`/a real build should confirm what `context`
      actually contains in Main UI's Add-Thing flow against the pinned openHAB core version.
- [x] 1.2 Exclude SKIs already paired under the target `eebus:service` Bridge from the option
      list; keep SKIs paired elsewhere. (Scenarios: "Options exclude a SKI already paired under
      the target service", "Options include a SKI already paired under a different service") —
      `EEBusOhPeerSkiOptionProvider#skisAlreadyPairedUnder`/`targetBridgeUid`. Subject to the same
      `context` caveat as 1.1.
- [x] 1.3 Verify the option list does not restrict manual entry (no `limitToOptions`). (Scenario:
      "Manually entered SKI remains accepted") — `ParameterOption` contributed via
      `ConfigOptionProvider` is additive to the existing free-text `ski` parameter in
      `thing-types.xml`; no `limitToOptions` attribute was added there.

## 2. Pairing State

- [x] 2.1 Add a Thing property to `eebus:oh-peer` recording pairing state, analogous to
      `PROPERTY_LOCAL_SKI` on `eebus:service` — `EEBusBindingConstants.PROPERTY_PAIRED`.
- [x] 2.2 Update the parent Bridge's trusted-SKI recompute to only include `eebus:oh-peer`
      children whose pairing property is set. (Scenario: "eebus:oh-peer added to a running
      service is not yet trusted") — `EEBusHandler.currentOhPeerSkis()` renamed to
      `currentPairedOhPeerSkis()`, now also checks `PROPERTY_PAIRED`.
- [x] 2.3 Ensure the Bridge re-reads pairing properties from all children on its own
      initialization, so a previously paired Thing is trusted again after a restart without
      re-invoking `pair()`. (Scenario: "Paired state survives a restart") — no separate code path
      needed: `currentPairedOhPeerSkis()` reads the property fresh from each child `Thing` object
      every time it runs, and `startShipSpine()` (called from `initialize()`, i.e. on every
      Bridge start including after a restart) already calls it via `withTrustedSkis(...)`. Thing
      properties are restored by the openHAB framework before `initialize()` runs (same
      precedent as `PROPERTY_LOCAL_SKI`/ADR-008), so no explicit read-back code was required.

## 3. Thing Actions

- [x] 3.1 Add parameterless `pair()` Thing Action on the `eebus:oh-peer` handler: sets the
      pairing property, triggers a trusted-SKI recompute on the parent Bridge. (Scenarios:
      "Invoking pair() establishes trust", "Invoking pair() on an already-paired Thing is a
      no-op") — `EEBusOhPeerActions.pair()` delegates to `EEBusOhPeerHandler.pair()`.
- [x] 3.2 Add parameterless `unpair()` Thing Action on the `eebus:oh-peer` handler: clears the
      pairing property, triggers a trusted-SKI recompute on the parent Bridge, does not remove
      the Thing. (Scenarios: "Invoking unpair() revokes trust", "Invoking unpair() on an
      already-unpaired Thing is a no-op") — `EEBusOhPeerActions.unpair()` delegates to
      `EEBusOhPeerHandler.unpair()`. **Not verified against an actual openHAB core build** — no
      compiler access in this environment; the `@ThingActionsScope`/`@RuleAction`/
      `@Component(scope = ServiceScope.PROTOTYPE)` pattern follows the standard, long-established
      openHAB binding convention but was written by static inspection only (same caveat already
      recorded in ADR-002's "Known compromise" for this binding). `EEBusOhPeerHandler.getServices()`
      registers `EEBusOhPeerActions` so it is picked up without a factory change.

## 4. Tests

- [x] 4.0 (Found via a real `mvn clean install` run in the user's environment, not anticipated
      when this checklist was written) `EEBusOhPeerHandlerTest.whenSkiConfiguredAndBridgeOnlineThenStatusIsOnline`
      failed after this change - it encoded the old "Thing existence = paired" behavior from
      `separate-real-and-oh-peer-things`, which task 2.2 deliberately changes. Renamed to
      `whenSkiConfiguredAndBridgeOnlineButNotPairedThenStatusIsOffline` (now asserts `OFFLINE`/
      not paired), and added `whenPairedAndBridgeOnlineThenStatusIsOnline`,
      `whenUnpairedAfterPairingThenStatusIsOfflineAgain`,
      `whenPairInvokedOnAlreadyPairedThingThenRemainsPairedWithoutError`,
      `whenUnpairInvokedOnAlreadyUnpairedThingThenRemainsUnpairedWithoutError` - covering
      `EEBusOhPeerHandler.pair()`/`unpair()`/`isPaired()` directly via `CallbackMock`, without
      needing an `EEBusHandler`/Bridge-lifecycle mock (task 4.1-4.3's blocker below is about the
      trusted-SKI-**set** assertion specifically, not about exercising `pair()`/`unpair()` at
      all).
- [ ] 4.1 Unit test: `pair()` adds the Thing's `ski` to the parent Bridge's trusted-SKI set. **Not
      done** — same reason `separate-real-and-oh-peer-things/tasks.md` §5.1/5.2 gave: no existing
      test harness in this binding for mocking `EEBusHandler`'s Bridge/child-Thing lifecycle.
      (`EEBusOhPeerHandler.pair()` itself is covered by 4.0's new tests - what's still missing is
      asserting the _effect_ on `EEBusHandler.recomputeTrustedSkis()`'s output.)
- [ ] 4.2 Unit test: `unpair()` removes the Thing's `ski` from the parent Bridge's trusted-SKI
      set without removing the Thing. **Not done**, same reason as 4.1.
- [ ] 4.3 Unit test: creating/initializing an `eebus:oh-peer` Thing does not by itself add its
      `ski` to the trusted-SKI set. **Not done**, same reason as 4.1.
- [ ] 4.4 Unit test: `ConfigOptionProvider` excludes SKIs already paired under the target
      service, includes SKIs paired under other services. **Not done** — needs a `ThingRegistry`
      test double; no existing fixture for that in this binding's test sources yet.
- **Verified by a real `mvn clean install` run in the user's environment** (this session's own
  sandbox still had no working shell access - `mcp__workspace__bash` timed out repeatedly,
  same caveat already recorded in `separate-real-and-oh-peer-things/tasks.md` §5 and
  `eebus-network-discovery/tasks.md` §4). That run surfaced two real problems fixed during this
  pass: a wrong class name (`ConfigOptionsProvider` → `ConfigOptionProvider`, see ADR-013's
  history) and the 4.0 test regression above. `$QA`/`$Review` should re-run the full suite once
  more after these fixes to confirm a clean pass - not re-confirmed in this session.

## 5. Documentation

- [x] 5.1 Update binding `README.md` pairing instructions to describe the new dropdown +
      `pair()`/`unpair()` flow — added a dedicated "Pairing" section, split out of the previous
      "Discovery" section, including the two-local-services case.
- [x] 5.2 CONCEPT.md §4.6 already documents the decision (added 2026-08-05) — no further edits
      needed.

---

_Change ID: `decouple-oh-peer-config-from-pairing`._
