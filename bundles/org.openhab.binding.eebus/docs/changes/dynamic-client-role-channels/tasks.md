# Tasks: Dynamic Channels for Client-Role Use Cases (MPC first)

## 1. Channel Type Declarations

- [ ] 1.1 Add `channel-group-type` `mpc` and `channel-type` `mpc-power` (`Number:Power`,
      read-only, label "Power", category "Energy") to `thing-types.xml`, referenced from the
      `oh-peer` thing-type's `channel-groups`. (Scenario: "Channel created for a use case that is
      both configured and detected")

## 2. Handler Access for Client-Role Use Cases

- [ ] 2.1 Change `EEBusMpcClientUseCase`'s constructor from
      `Function<String, Optional<String>> ohPeerThingUidResolver` to a resolver that returns the
      `EEBusOhPeerHandler` itself (or `Optional<EEBusOhPeerHandler>`), per CONCEPT.md §4.2.2
      point 1 - only the handler can call `editThing()`/`updateState()`. Update the call site in
      `EEBusHandler` accordingly.
- [ ] 2.2 Add a method on `EEBusOhPeerHandler` (e.g. `ensureChannel(ChannelGroupUID group,
      String channelId, ChannelTypeUID type)` or similar) that creates the Channel via
      `editThing()`/`ChannelBuilder` only if it does not already exist, and is safe to call
      repeatedly (idempotent - needed for "Channel appears after first successful measurement
      resolution" without special-casing "already created").

## 3. MPC Client-Role Channel Wiring

- [ ] 3.1 In `EEBusMpcClientUseCase#applyMeasurement()`, replace the current
      `logger.debug(...)`-only line with a call through the resolved `EEBusOhPeerHandler` to
      create (if needed) the `mpc#power` Channel and `updateState(...)` it with the measured
      Watts value. (Scenarios: "Channel appears after first successful measurement resolution",
      "Channel state updates on subsequent notifications")
- [ ] 3.2 Verify (by code inspection of `EEBusHandler#startShipSpine`/use case registration)
      that `EEBusMpcClientUseCase` is only ever registered when `MPC` is present in the Bridge's
      `supportedUseCasesClient` - this should already hold per CONCEPT.md §4.2.2 point 2's
      description of the existing code, but confirm no change broke it. (Scenario: "No channel
      created for a use case not configured on the Bridge")

## 4. Persistence Across Unpair/Disconnect

- [ ] 4.1 Confirm (by inspection, and ideally a test) that `EEBusOhPeerHandler#unpair()` (from
      `decouple-oh-peer-config-from-pairing`) does not touch Channels - it currently only clears
      the `paired` property and updates status, which should already satisfy this, but add an
      explicit test to lock it in. (Scenario: "Channel remains after unpair()")
- [ ] 4.2 Confirm no code path removes or resets Channel state when a peer becomes unreachable
      (e.g. no `updateState(..., UnDefType.UNDEF)` on subscription failure/timeout in
      `EEBusMpcClientUseCase`). (Scenario: "Channel retains last known state when the peer
      becomes unreachable")

## 5. Use-Case Visibility Property

- [ ] 5.1 Add a constant for the property-key convention (lowercase use-case name) and set it
      from `EEBusMpcClientUseCase#onUseCasePartnersFound()` via the resolved
      `EEBusOhPeerHandler`, value `"server"` (see proposal.md "Open Questions" for the reasoning
      and the outstanding confirmation needed from `$Review`/the user). (Scenario: "MPC detection
      is recorded as a Thing property")

## 6. Tests

- [ ] 6.1 Unit test: `EEBusOhPeerHandler`'s new channel-creation helper is idempotent (calling it
      twice for the same Channel does not throw or duplicate the Channel).
- [ ] 6.2 Unit test: `unpair()` does not remove or alter existing Channels or their last state.
- [ ] 6.3 Unit test (if feasible without a full SPINE test harness - otherwise document as a
      known gap, consistent with `decouple-oh-peer-config-from-pairing/tasks.md` §4's precedent):
      `EEBusMpcClientUseCase#applyMeasurement()` calls through to the handler with the correct
      Watts value and Channel ID.

## 7. Documentation

- [ ] 7.1 Update `README.md` if it documents available Channels per Thing type, to mention that
      `mpc#power` appears dynamically after pairing with an MPC-capable peer (not statically
      listed like Server-role channels).
- [ ] 7.2 CONCEPT.md §4.2.2/§5.4.3 already document the decision and the LPC reference table
      (added 2026-08-06) - no further edits needed unless implementation reveals a deviation.

---

_Change ID: `dynamic-client-role-channels`._
