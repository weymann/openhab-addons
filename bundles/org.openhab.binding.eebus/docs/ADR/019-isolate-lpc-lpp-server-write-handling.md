# ADR-019: Isolate LPC/LPP Server-role write handling per direction

## Status

Accepted (2026-08-21)

## Context

ADR-018 gave `AbstractEEBusLimitControllableSystemUseCase` (Server role) a distinct, stable
`getLimitId()` per direction (`0` for LPC, `1` for LPP) and fixed the Client role to resolve
`limitId` by `limitDirection` instead of assuming a fixed value. That fix is confirmed correct
live: `AbstractEEBusLimitEnergyGuardUseCase#resolveLimitId` correctly filters incoming
`loadControlLimitListData` notifications by `limitId`.

Attempting task 3.1's live retest (toggle the LPC write-path Item only, confirm LPP's read-back
status is unaffected) on 2026-08-21 (~22:22-23:08) still failed: the LPP status briefly showed
`active=true` at the exact moment the LPC Item was toggled, and both use cases'
`EEBusLimitControlStateMachine` instances transitioned in lockstep on every observed state change
(initial subscribe, the toggle, and two later automatic failsafe transitions).

Direct source review of `AbstractEEBusLimitControllableSystemUseCase` (unchanged by ADR-018)
found two compounding defects, both on the Server side, both independent of the Client-role fix:

**Defect 1 — unfiltered write listener.** `setupLoadControl()` registers
`limitFunction.addUseCaseWriteDataListener((data, updateType, idx) -> onLimitWritten(data))`.
Because LPC's and LPP's Server use-case instances both call `setupLoadControl()` on the _same_
shared `LoadControl` Server feature (confirmed: this is the same sharing ADR-018's own Context
described as the root collision mechanism), both instances' listeners are registered on the same
`LimitListDataFunction` and both receive _every_ write to _either_ `limitId`. `onLimitWritten`
never checks `data.getLimitId()` against `this.getLimitId()`, so a write to LPC's `limitId=0`
also invokes LPP's `onLimitWritten`, which transitions LPP's own state machine and pushes the
(LPC-owned) value/active flag onto LPP's Item via `metadataService.sendCommand(...)`.

**Defect 2 — hardcoded publish index.** `onStateChanged` publishes a state transition back to
the peer via:

```java
function.updateData(0, new LoadControlLimitDataType().withLimitId(getLimitId())...);
```

`updateData(int idx, DATA update)` is confirmed literal-index-based by reading jeebus.spine's
`DataListHolder` source (`dataList.set(entry.getKey(), ...)` — a plain `List.set`, not a lookup
by `limitId`). `setupLoadControl()`'s `limitFunction.addData(...)` call (also unchanged by
ADR-018) discards the index it returns, and `onStateChanged` always writes to literal index `0`
regardless of which direction's own entry was assigned that index. Since LPC and LPP call
`addData()` in setup order (not a fixed order), whichever direction registers _second_ is
assigned index `1` — yet its own `onStateChanged` still writes to index `0`, silently overwriting
the _other_ direction's peer-visible entry (including that entry's `limitId`) every time its own
state changes, while its own true index (`1`) is never updated again after the initial `addData`.

These two defects compound and fully reproduce the observed log: a write to LPC (Defect 1) also
fires LPP's state machine; LPP's resulting `onStateChanged` (Defect 2) overwrites index `0`
(LPC's slot) with LPP's `limitId=1`, `isLimitActive=true` — so a peer reading the list afterwards
sees an entry claiming `limitId=1` active, and no entry left claiming `limitId=0` at all, exactly
matching the Client-side log's `lpp.limit = active=true` and `lpc ... no entry for limitId 0,
ignoring` lines observed back-to-back during the same toggle.

This ADR addresses the "Server-role LPC and LPP write handling stays isolated per direction"
requirement added to
`docs/changes/lpc-lpp-limitid-resolution/specs/lpc-lpp-limit-control/spec.md` alongside this
change.

## Decision

**1. Filter `onLimitWritten` by `limitId`.**

```java
private void onLimitWritten(LoadControlLimitDataType data) {
    if (!Objects.equals(data.getLimitId(), getLimitId())) {
        return;
    }
    // ... unchanged from here
}
```

A write to the other direction's `limitId` is now a no-op for this instance, matching how the
Client-role fix in ADR-018 already filters incoming notifications.

**2. Track this instance's own list index; never hardcode it.**

```java
private int limitDataIndex = -1;
```

`setupLoadControl()` captures the index `limitFunction.addData(...)` returns into this field
instead of discarding it. `onStateChanged` reads the field and uses it instead of the literal
`0`:

```java
int idx = this.limitDataIndex;
if (idx < 0) {
    logger.warn("Cannot publish {} limit state change - list index not yet assigned", getShortCode());
    return;
}
function.updateData(idx, new LoadControlLimitDataType().withLimitId(getLimitId())...);
```

The `idx < 0` guard covers the case where the earlier `addData()` call failed
`DataValidationException` (already caught and logged in `setupLoadControl()`) and so never
assigned an index - `onStateChanged` must not fall back to a wrong index (e.g. `0`) in that case
either, since that is exactly the class of bug being fixed here.

No change to the Client role, to `getLimitId()`/`getLimitDirection()`, to the `limitId` values
chosen by ADR-018, or to `EEBusLpcServerUseCase`/`EEBusLppServerUseCase` - both defects and their
fix live entirely inside the shared `AbstractEEBusLimitControllableSystemUseCase` base class.

## Consequences

### Positive

- A write to one direction's limit can no longer transition, or publish state for, the other
  direction - Defect 1 and Defect 2 both become structurally impossible, not just less likely,
  matching the same standard ADR-018 already set for the Client role.
- No new abstractions, no new subclass methods to implement, no behavior change for any
  single-direction (LPC-only or LPP-only) deployment - the fix only changes behavior when both
  directions are active on the same Bridge, which is exactly the case that was broken.
- Closes the loop on the amended `lpc-lpp-limitid-resolution` change - the Client-role half
  (ADR-018) and the Server-role half (this ADR) together make the full "isolated per direction"
  requirement structurally guaranteed on both roles.

### Negative

- `limitDataIndex` is one more piece of mutable instance state to reason about; if
  `LimitListDataFunction` were ever changed upstream to reorder or compact its internal list
  after `addData()` (no evidence today that it does - `DataListHolder#removeData` nulls entries
  in place rather than compacting, per source read), a captured index could go stale. Not a
  concern for this binding's current usage (entries are never removed after setup).
- `onStateChanged`'s new "index not yet assigned" branch is an additional untested code path
  (see the deferred unit-test task in `tasks.md`) - low risk given it can only be reached if the
  initial `addData()` already failed and logged a warning, but it is new branching that a future
  test should cover.

## Diagram

```mermaid
sequenceDiagram
    participant Guard as Energy Guard (peer)
    participant Lpc as EEBusLpcServerUseCase (limitId=0)
    participant Lpp as EEBusLppServerUseCase (limitId=1)
    participant List as Shared LimitListDataFunction

    Note over Lpc,Lpp: Before this ADR
    Guard->>List: write LoadControlLimitData(limitId=0, active=true)
    List-->>Lpc: onLimitWritten(data) [unfiltered]
    List-->>Lpp: onLimitWritten(data) [unfiltered - BUG]
    Lpc->>Lpc: stateMachine.onLimitWritten(true) -> LIMITED
    Lpp->>Lpp: stateMachine.onLimitWritten(true) -> LIMITED [BUG]
    Lpc->>List: updateData(0, limitId=0, active=true) [correct, index 0 is Lpc's]
    Lpp->>List: updateData(0, limitId=1, active=true) [BUG - overwrites Lpc's slot]
    Note over List: index 0 now claims limitId=1; index 1 (Lpp's real slot) stale

    Note over Lpc,Lpp: After this ADR
    Guard->>List: write LoadControlLimitData(limitId=0, active=true)
    List-->>Lpc: onLimitWritten(data)
    List-->>Lpp: onLimitWritten(data)
    Lpc->>Lpc: data.limitId==0==getLimitId() -> proceed -> LIMITED
    Lpp->>Lpp: data.limitId==0 != getLimitId()==1 -> return (no-op)
    Lpc->>List: updateData(limitDataIndex=0, limitId=0, active=true)
    Note over List: index 1 (Lpp) untouched
```

---

_Stored at `org.openhab.binding.eebus/docs/ADR/019-isolate-lpc-lpp-server-write-handling.md`._
