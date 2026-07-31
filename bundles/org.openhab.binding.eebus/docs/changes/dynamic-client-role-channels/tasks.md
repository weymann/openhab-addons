# Tasks: Dynamic Channels for Client-Role Use Cases (MPC first)

## 1. Channel Type Declarations

- [x] 1.1 Add `channel-group-type` `mpc` and `channel-type` `mpc-power` (`Number:Power`,
      read-only, label "Power", category "Energy") to `thing-types.xml`. **Deviation from the
      original wording:** deliberately **not** referenced from `oh-entity`'s own `channel-groups` -
      see docs/ADR/014-dynamic-client-role-channels.md for why (would create the group
      unconditionally on every `oh-entity` Thing, breaking the gating requirement). (Scenario:
      "Channel created for a use case that is both configured and detected")

## 2. Handler Access for Client-Role Use Cases

- [x] 2.1 Change `EEBusMpcClientUseCase`'s constructor from
      `Function<String, Optional<String>> ohPeerThingUidResolver` to a resolver that returns the
      `EEBusOhPeerHandler` itself (or `Optional<EEBusOhPeerHandler>`), per CONCEPT.md §4.2.2
      point 1 - only the handler can call `editThing()`/`updateState()`. Update the call site in
      `EEBusHandler` accordingly.
- [x] 2.2 Add a method on `EEBusOhPeerHandler` (`ensureChannel(ChannelUID, ChannelTypeUID,
      String acceptedItemType, String label)`) that creates the Channel via
      `editThing()`/`ChannelBuilder` only if it does not already exist, and is safe to call
      repeatedly (idempotent - needed for "Channel appears after first successful measurement
      resolution" without special-casing "already created").

## 3. MPC Client-Role Channel Wiring

- [x] 3.1 In `EEBusMpcClientUseCase#applyMeasurement()`, replace the current
      `logger.debug(...)`-only line with a call through the resolved `EEBusOhPeerHandler` to
      create (if needed) the `mpc#power` Channel and `updateState(...)` it with the measured
      Watts value. (Scenarios: "Channel appears after first successful measurement resolution",
      "Channel state updates on subsequent notifications")
- [x] 3.2 Verified by code inspection of `EEBusHandler#startShipSpine`: `EEBusMpcClientUseCase`
      is still only ever registered when `MPC` is present in `cfg.supportedUseCasesClient` - the
      gating condition itself was not touched by this change, only the constructor argument
      passed to it. (Scenario: "No channel created for a use case not configured on the Bridge")

## 4. Persistence Across Unpair/Disconnect

- [x] 4.1 Confirmed by inspection and locked in by test
      (`whenUnpairInvokedThenMpcPowerChannelAndStateRemain`): `EEBusOhPeerHandler#unpair()` only
      touches the `paired` property and status, never Channels. (Scenario: "Channel remains after
      unpair()")
- [x] 4.2 Confirmed by inspection: no code path in `EEBusMpcClientUseCase` calls
      `updateState(..., UnDefType.UNDEF)` or otherwise resets Channel state on subscription
      failure/timeout - the `exceptionally(...)` handlers in `subscribe()` only log. (Scenario:
      "Channel retains last known state when the peer becomes unreachable")

## 5. Use-Case Visibility Property

- [x] 5.1 Added `EEBusBindingConstants.USE_CASE_KEY_MPC` (`"mpc"`) and
      `EEBusOhPeerHandler#recordDetectedUseCase(String, String)`, called from
      `EEBusMpcClientUseCase#onUseCasePartnersFound()` with value `"server"` (see proposal.md
      "Open Questions" for the reasoning - **still not confirmed by `$Review`/the user**, flagged
      again here since a second use case would be the first real test of this reading). (Scenario:
      "MPC detection is recorded as a Thing property")

## 6. Tests

- [x] 6.1 Unit test: `whenApplyMpcPowerCalledTwiceThenChannelIsNotDuplicated` -
      `EEBusOhPeerHandler`'s channel-creation helper is idempotent (calling it twice for the same
      Channel does not throw or duplicate the Channel; second call's value wins).
- [x] 6.2 Unit test: `whenUnpairInvokedThenMpcPowerChannelAndStateRemain` - `unpair()` does not
      remove or alter existing Channels or their last state.
- [ ] 6.3 **Known gap, consistent with `decouple-oh-peer-config-from-pairing/tasks.md` §4's
      precedent:** no unit test exercises `EEBusMpcClientUseCase#onUseCasePartnersFound()` /
      `subscribe()` / `applyMeasurement()` end-to-end, since that needs a working fake of
      jeebus.spine's `Device`/`NodeManagement`/`UseCasePartner` (no existing test fixture for
      this in the binding yet - `EEBusMetadataServiceTest` is the closest precedent but does not
      cover this surface). `EEBusOhPeerHandlerTest`'s new tests instead exercise
      `applyMpcPower`/`recordDetectedUseCase` directly, covering the handler-side half of the
      behavior. Revisit if/when a SPINE test harness exists for a different change.

## 7. Documentation

- [x] 7.1 `README.md` "Channels" section rewritten (was still the unedited openHAB template
      placeholder) to document the Server-role-uses-metadata / Client-role-uses-dynamic-Channels
      split and the `oh-entity`/`mpc`/`power` Channel specifically.
- [x] 7.2 CONCEPT.md §4.2.2/§5.4.2/§5.4.3 updated 2026-08-12 to reflect MPC's primary-source
      verification (was still marked "Sekundärquellen-Stand" as of this task being written) and
      the completed LPC Scenario 4 table - no further edits needed for this change beyond that.

---

_Change ID: `dynamic-client-role-channels`._
