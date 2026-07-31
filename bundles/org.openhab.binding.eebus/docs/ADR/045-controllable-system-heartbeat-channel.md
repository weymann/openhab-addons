# ADR-045: Controllable System Heartbeat Channel

## Status

Accepted

## Context

`AbstractEEBusLimitControllableSystemUseCase` (LPC/LPP Server role, "Controllable System"
actor) already subscribes to the paired Energy Guard's `DeviceDiagnosis` feature and receives
a Heartbeat notification roughly every 60 seconds (confirmed flowing reliably on both the
local simulation rig and a real Hager Energy S10). Today that notification is used for exactly
one thing: rearming `EEBusLimitControlStateMachine`'s Heartbeat watchdog
(`onHeartbeatNotification()` calls `machine.onHeartbeatReceived()`). The `RequestResult`
argument the SPINE subscription callback receives is discarded entirely - nothing in openHAB
ever observes that a Heartbeat arrived, only the indirect consequence of one going missing
(a failsafe state transition after the watchdog times out).

The user asked for a Channel on the Controllable System side that signals each received
Heartbeat, fired as a trigger Channel, and whether the EEBus Heartbeat carries data usable as
that trigger. It does: SPINE's `DeviceDiagnosisHeartbeatData` (jeebus.spine
`HeartbeatDataFunction`) carries a monotonically incrementing `heartbeatCounter`
(`BigInteger`) and a `timestamp` on every notification, reachable in the binding via
`RequestResult.getCmd().getDeviceDiagnosisHeartbeatData()`.

A state Channel (e.g. `Switch`/`DateTime`) was considered and rejected: a periodic liveness
signal has no natural persistent state - a `Switch` would need an artificial "reset after N
seconds" rule to ever go back to a meaningful value, and a `DateTime` "last seen" Channel
duplicates exactly what the existing Heartbeat watchdog / failsafe status Channel
(docs/ADR/022) already expresses, just less directly. A trigger Channel, which only ever
fires an event with no state to maintain, is the better fit and requires no additional
bookkeeping.

**Note on where this Channel lives:** the initial concept draft for this change assumed
`eebus:oh-cs-entity` represents openHAB's own local Controllable System Entity, and proposed a
new resolver in `EEBusHandler` to reach "the local CS Thing's handler". That assumption was
wrong. Per docs/ADR/021 and docs/ADR/025, `eebus:oh-cs-entity` (like a generic
`eebus:oh-entity` configured the same way) represents the **trusted remote peer device** (the
Energy Guard) - "Controllable System" names the SPINE actor role openHAB itself plays for that
peer, not an identity of the Thing. `AbstractEEBusLimitControllableSystemUseCase` already
resolves and holds this exact Thing's handler in its existing `energyGuardOhEntityHandler`
field (used today by `publishLimitState` to call `peerHandler.applyLimitStatus(...)`), so no
new resolver was needed.

## Decision

### 1. New shared `heartbeat` trigger Channel, referenced from both `lpc` and `lpp`

A new `channel-type id="heartbeat"` with a `<kind>trigger</kind>` child element is declared
in `thing-types.xml` and added
to the `<channels>` list of both the `lpc` and `lpp` `channel-group-type` blocks - the same
shared-channel-type-across-both-groups pattern already used for `limit-active`/`limit-value`/
`limit-duration` (LPC/LPP are structurally identical, CONCEPT.md §5.4.2). This gives
`eebus:oh-cs-entity`'s statically-declared Channel Groups `lpc#heartbeat`/`lpp#heartbeat` for
free, and lets `EEBusOhEntityHandler#ensureChannel`'s dynamic-creation path (used for a
generic `eebus:oh-entity` peer) create the same Channel on first Heartbeat, exactly like every
other lpc/lpp Channel already does.

### 2. Fired once per Channel Group, not once per Entity

`EEBusLpcServerUseCase` and `EEBusLppServerUseCase` are two independent instances of
`AbstractEEBusLimitControllableSystemUseCase`, each with its own `onEnergyGuardFound`
subscription and its own `EEBusLimitControlStateMachine` watchdog. Both happen to subscribe to
the same physical partner's `DeviceDiagnosis` feature; jeebus.spine's
`FeatureImpl#requestSubscription` deduplicates the wire-level SPINE subscription per address
and fans the notification out to every registered listener, so both instances'
`onHeartbeatNotification` still fire independently per real Heartbeat with no extra SPINE
traffic. Firing `lpc#heartbeat` and `lpp#heartbeat` independently from each instance needed no
new coordination code, and matches the existing per-role independence (each Channel Group
already reports its own `limit-active`/`limit-value` independently).

### 3. `EEBusOhEntityHandler` gains a trigger-Channel counterpart to `ensureChannel`

`ensureChannel` builds a Channel via `ChannelBuilder.create(channelUID, acceptedItemType)`,
which requires a state item type a trigger Channel does not have. A new
`ensureTriggerChannel(ChannelUID, ChannelTypeUID, String label)` private helper uses the
item-type-less `ChannelBuilder.create(channelUID)` overload and sets `ChannelKind.TRIGGER`
explicitly instead, otherwise mirroring `ensureChannel`'s no-op-if-already-exists behavior. A
new public `triggerHeartbeat(String channelGroup, @Nullable BigInteger heartbeatCounter)`
method ensures the Channel then calls `triggerChannel(uid, heartbeatCounter != null ?
heartbeatCounter.toString() : "")` - the counter is sent as the event payload so a Rule can
detect a skipped Heartbeat (a gap in the counter) if it ever wants to, without requiring it.

### 4. `onHeartbeatNotification` stops discarding the SPINE notification

The subscription lambda in `maybeSubscribeToEnergyGuardHeartbeat` changes from
`notification -> onHeartbeatNotification()` to `notification -> onHeartbeatNotification(notification)`.
`onHeartbeatNotification(RequestResult)` extracts
`notification.getCmd().getDeviceDiagnosisHeartbeatData()` for the counter (tolerated as
`null` - a real peer that omits the field, or a `DeviceDiagnosisHeartbeatData` future version
without it, still gets the trigger fired, just without a payload) and calls
`energyGuardOhEntityHandler.triggerHeartbeat(getShortCode(), counter)` if that field is
already resolved. A Heartbeat arriving before peer resolution completes still rearms the
watchdog as before; the trigger simply does not fire yet, since there is nothing to fire it
on - the same tolerance `publishLimitState` already has for an unresolved peer.

## Consequences

### Positive

- A user Rule can now react directly to "the paired Energy Guard is alive", without waiting
  for (or depending on) a failsafe state transition, which only ever signals the _absence_ of
  a Heartbeat after a 60s+ timeout.
- No new persistent state to maintain, no new field, no new resolver/wiring through
  `EEBusHandler` - the change is contained to `EEBusOhEntityHandler` and
  `AbstractEEBusLimitControllableSystemUseCase`.
- The optional counter payload gives a Rule author a way to notice a skipped Heartbeat without
  the binding needing to detect and report that itself.

### Negative

- `lpc#heartbeat` and `lpp#heartbeat` fire independently for what is, from the wire's
  perspective, the same physical Heartbeat - a Rule listening on both Channel Groups sees two
  near-simultaneous events per actual Heartbeat. Accepted as consistent with the existing
  per-role independence of every other lpc/lpp Channel, rather than introduced specially for
  this one.
- A trigger Channel's event history is not queryable the way a state Channel's persisted state
  is (no "when did the last Heartbeat arrive" query without a Rule capturing it into an Item)
  - acceptable since docs/ADR/022's failsafe status Channel already covers the "is the limit
  currently being enforced due to a stale connection" question a state-based view would
  otherwise be asked to answer.

---

_Refines docs/ADR/021-controllable-system-limit-status-channel.md and
docs/ADR/025-oh-cs-entity-static-channels.md (both remain in force) by adding a further
Channel to the same `lpc`/`lpp` Channel Groups those ADRs established._
