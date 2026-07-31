# ADR-046: Controllable System State Channel

## Status

Accepted

## Context

`EEBusLimitControlStateMachine` tracks which of the five LPC/LPP states
(`EEBusLimitControlState`: `INIT`, `UNLIMITED_CONTROLLED`, `LIMITED`, `FAILSAFE`,
`UNLIMITED_AUTONOMOUS`) a Controllable System Entity is currently in, but nothing publishes
that state directly to openHAB. Today it is only observable:

- Indirectly, through the `limit-active`/`failsafe-limit-value`/`failsafe-duration-minimum`
  Channels (ADR-021/022) - a user has to infer which of the five states is active from the
  combination, which is not a 1:1 mapping (`UNLIMITED_CONTROLLED` and `UNLIMITED_AUTONOMOUS`
  look identical on those Channels, for example).
- Via the `LPC.state`/`LPP.state` metadata Item (`DATA_POINT_STATE`, a `StringType` of the
  enum's `name()`) - but only if a user tags an Item for it first; nothing is created
  automatically, and nothing is scoped to a specific peer, matching the exact gap ADR-021
  already fixed for `limit-active`/`limit-value` before this ADR.

The user asked for the raw state machine state itself to be visible as a Channel, encoded as a
number, with each number offering a readable alias as an option - i.e. openHAB's standard
`state`/`<options>` mechanism for a `Number` Channel-Type, so Main UI shows a readable label
while a Rule or a chart can still work with a plain, stable number.

## Decision

### 1. New `state` Channel, `Number` item type, `<options>` for readable labels

A new `channel-type id="state"` (`item-type` `Number`) is added to `thing-types.xml`,
referenced from both the `lpc` and `lpp` `channel-group-type` blocks - same
shared-channel-type-across-both-groups pattern as every other lpc/lpp Channel. Its
`<state><options>` block gives each numeric value a readable label:

```xml
<option value="0">Init</option>
<option value="1">Unlimited (Controlled)</option>
<option value="2">Limited</option>
<option value="3">Failsafe</option>
<option value="4">Unlimited (Autonomous)</option>
```

### 2. The number is `EEBusLimitControlState#ordinal()` - declaration order becomes a contract

Rather than inventing a separate numeric code, the Channel publishes the enum's own
`ordinal()`. This is the simplest option that still gives a stable number, but it means the
enum's declaration order is now a public contract: reordering, inserting, or removing a
constant changes every ordinal after it and silently breaks the `<option>` mapping (and
anything a Rule already keyed off a specific number). `EEBusLimitControlState`'s javadoc is
updated to say so explicitly - only ever append a new constant at the end, with a matching new
`<option>`.

### 3. `EEBusOhEntityHandler#applyLimitControlState`, called from `onStateChanged`

A new public `applyLimitControlState(String channelGroup, EEBusLimitControlState state)`
method (parallel to `applyLimitStatus`/`applyFailsafeStatus`) creates the Channel if missing
and publishes `new DecimalType(state.ordinal())`. `AbstractEEBusLimitControllableSystemUseCase#onStateChanged` -
the single place that already computes the new state for the SPINE-feature
echo-back and the `LPC.state`/`LPP.state` metadata push - calls it too, on the same
already-resolved `energyGuardOhEntityHandler` field `publishLimitState` uses, with the same
tolerance for an unresolved peer (nothing fires until a peer is resolved; the existing
immediate-push-on-resolution call in `resolveEnergyGuardOhEntityHandler` covers that case for
free, since it already calls `onStateChanged(machine.getState())`).

Both existing outputs (SPINE echo, `LPC.state`/`LPP.state` metadata) are kept unchanged; the
Channel is additive, matching the precedent ADR-021 already set for `limit-active`/
`limit-value`.

## Consequences

### Positive

- The state machine's actual current state is now directly visible per peer, without a user
  having to infer it from other Channels or tag an Item first.
- A stable, small number is friendly to Rules, sitemaps, and persistence/charting, while
  `<options>` still gives Main UI a readable label - no separate `String` Channel needed
  alongside it.

### Negative

- The enum's declaration order is now load-bearing in a way it previously was not (only
  `name()` - stable regardless of order - was ever published before this ADR, via the
  metadata Item). Documented in the enum's own javadoc as the mitigation; no compile-time
  enforcement exists that `thing-types.xml`'s `<options>` stay in sync with the enum if it is
  ever extended.
- Adds a fifth Channel to already-large `lpc`/`lpp` Channel Groups (now: `state`,
  `limit-active`, `limit-value`, `limit-duration`, `failsafe-limit-value`,
  `failsafe-duration-minimum`, `heartbeat`) - accepted as consistent with the existing
  additive pattern rather than a reason to consolidate.

---

_Refines docs/ADR/021-controllable-system-limit-status-channel.md and
docs/ADR/025-oh-cs-entity-static-channels.md (both remain in force) by adding a further
Channel to the same `lpc`/`lpp` Channel Groups those ADRs established - same relationship
docs/ADR/045-controllable-system-heartbeat-channel.md already has to them._
