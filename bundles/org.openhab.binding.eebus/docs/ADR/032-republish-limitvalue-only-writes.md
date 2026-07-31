# ADR-032: Republish `limitValue`-only writes even when `limitActive` stays unchanged

## Status

Accepted

## Context

User report (2026-08-27, chat, EG-side tagged Item): changing `limitValue` while `limitActive`
is already ON never "arrives" at the CS side - repeated log lines were seen:

```text
[WARN ] [ine.spi.function.DataFeatureFunction] - isLimitChangeable is not set
```

That WARN turned out to be an unrelated red herring: `LimitListDataFunction.validateData`
(jeebus.spine) logs it whenever the object being validated has `isLimitChangeable == null`, and
never throws for it. `AbstractEEBusLimitEnergyGuardUseCase#sendLimitWrite` (the EG-side write
path) deliberately constructs its outgoing `LoadControlLimitDataType` with only `limitId`/
`isLimitActive`/`value` set, so this WARN fires on every single Client-role write by design - it
is expected noise, not evidence of a rejected write.

The actual cause, found by tracing the CS-side receive path:

`AbstractEEBusLimitControllableSystemUseCase#onLimitWritten` always updated
`lastWrittenLimitValue` and forwarded the new value to the CS's own local Item via
`metadataService.sendCommand(...)` - that part worked regardless. It then computed
`active = isLimitActive && hasValue` and called `EEBusLimitControlStateMachine#onLimitWritten
(active)`, which calls `transitionTo(active ? LIMITED : UNLIMITED_CONTROLLED)`.
`EEBusLimitControlStateMachine#transitionTo` is **edge-triggered**:

```java
private void transitionTo(EEBusLimitControlState newState) {
    if (newState == state) {
        return;
    }
    ...
    listener.onStateChanged(newState);
}
```

A `limitValue`-only write while `limitActive` stays ON re-requests `transitionTo(LIMITED)` while
already in `LIMITED` - a no-op, so `onStateChanged` is never invoked. `onStateChanged` was the
only place that (a) republished the value into the locally exposed SPINE
`LoadControlLimitListData` entry via `LimitListDataFunction#updateData` (what a peer sees on a
fresh SPINE read of this CS) and (b) mirrored the confirmed value onto the paired
`eebus:oh-entity`'s dynamic Channel via `EEBusOhEntityHandler#applyLimitStatus` (ADR-021). Neither
ran for this case - matching the reported symptom exactly.

## Decision

Decouple "publish the current active/value pair" from "the FSM actually transitioned":

1. Extract the publish body of `onStateChanged` (the SPINE `updateData` call and the
   `applyLimitStatus` mirror) into a new private method,
   `publishLimitState(boolean active, @Nullable ScaledNumberType value)`.
1. `onStateChanged` now computes `active` from the new state, as before, and calls
   `publishLimitState(active, lastWrittenLimitValue)` - unchanged behavior for every FSM
   transition (Energy Guard activates/deactivates a limit, Heartbeat-timeout to `FAILSAFE`,
   failsafe-duration expiry to `UNLIMITED_AUTONOMOUS`, etc.).
1. `onLimitWritten` captures the state machine's state immediately before calling
   `machine.onLimitWritten(active)`, and immediately after. If the state did not change (and the
   write carried a value), it calls `publishLimitState(active, data.getValue())` itself - the
   exact case `onStateChanged` was not invoked for.

`EEBusLimitControlStateMachine` itself is left edge-triggered - its notify-on-change contract is
correct and load-bearing elsewhere (e.g. the Heartbeat watchdog only wants a callback on an
actual transition into `FAILSAFE`, not on every timer tick). Weakening it globally to fire on
every call, rather than fixing the one caller that needed a value-only path, would have been the
wrong scope for this bug.

### Alternative considered: make the state machine level-triggered for this call

Change `EEBusLimitControlStateMachine#onLimitWritten` to always notify the listener, not just on
an actual state change.

- **Pros:** single change, no new method.
- **Cons:** `EEBusLimitControlStateMachine` is deliberately reusable/protocol-agnostic (see its
  own class javadoc) and its `Listener` contract ("called whenever the state changes") is relied
  on elsewhere to avoid duplicate work - this project has already hit and fixed a duplicate-
  notification bug from a similar cause (`AbstractEEBusLimitEnergyGuardUseCase`'s
  `subscribedPartners` guard, see its own 2026-08-21 javadoc note). Loosening the contract for one
  caller's convenience risks reintroducing that class of bug for every other listener. Rejected.

## Consequences

### Positive

- Closes the reported symptom: a `limitValue`-only write while `limitActive` stays ON now
  reaches both the SPINE-exposed `LoadControlLimitListData` entry and the peer's dynamic Channel
  immediately, instead of only updating the CS's own local Item.
- `onStateChanged` and `onLimitWritten` can no longer independently drift on how they publish -
  both go through the one `publishLimitState` method.
- No double-publish for the case that already worked: when a write _does_ flip `limitActive`
  (causing an actual FSM transition), `onStateChanged` alone (invoked synchronously from inside
  `transitionTo`) still fires exactly once - `onLimitWritten`'s own `publishLimitState` call is
  gated on the state having stayed the same, so nothing runs twice.
- `EEBusLimitControlStateMachine` and its notify-on-change contract are unchanged - no risk to
  its other callers (Heartbeat watchdog, failsafe-duration watchdog).
- Entirely inside `org.openhab.binding.eebus` - no `jeebus.ship`/`jeebus.spine` or `pom.xml`
  change.

### Negative

- `onLimitWritten` now reads `machine.getState()` twice (before/after) per write - negligible
  cost (a synchronized field read), not worth optimizing away.
- Source-level only, **not yet compiled or live-tested** - no local Maven/openHAB instance
  available in this environment (same limitation noted in every prior ADR in this project).
  Self-QA done: brace/paren balance (229/229, 419/419) and LF-only line endings (0 CRLF, 0 tabs)
  verified on the touched file before/after the edit.

## Diagram

```mermaid
sequenceDiagram
    participant EG as Energy Guard<br/>(tagged Item write)
    participant Spine as jeebus.spine<br/>LimitListDataFunction
    participant CS as AbstractEEBusLimitControllableSystemUseCase
    participant FSM as EEBusLimitControlStateMachine
    participant Peer as paired oh-entity Channel

    EG->>Spine: WriteCmd (limitId, isLimitActive=true, value=newWatts)
    Spine->>CS: onLimitWritten(data)
    CS->>CS: lastWrittenLimitValue = newWatts; sendCommand to local Item
    CS->>FSM: previousState = getState()  (LIMITED)
    CS->>FSM: onLimitWritten(active=true)
    FSM->>FSM: transitionTo(LIMITED) - already LIMITED, no-op, listener NOT called
    CS->>FSM: getState() == previousState -> true
    Note over CS: ADR-032: state didn't change, publish explicitly
    CS->>Spine: publishLimitState(true, newWatts) -> updateData(idx, ...)
    CS->>Peer: publishLimitState(true, newWatts) -> applyLimitStatus(...)
```

## Not yet done / user-owned

- `mvn clean install` + a live retest: change `limitValue` via the tagged EG Item while
  `limitActive` stays ON, and confirm the CS's `lpc#limit-value`/`lpp#limit-value` Channel (and a
  fresh SPINE read of the CS) reflect the new value without needing an active-toggle round-trip.
