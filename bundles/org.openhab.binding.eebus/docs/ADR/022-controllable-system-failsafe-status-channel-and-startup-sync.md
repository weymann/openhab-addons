# ADR-022: Controllable System failsafe status Channel, and explicit status publish at startup/reconnect

## Status

Accepted (2026-08-23)

## Context

Discussing a possible future "convenience Thing" for the Controllable System (CS) role
(`$Concept`, 2026-08-23) surfaced two related, but distinct, gaps in the existing LPC/LPP CS
implementation (`AbstractEEBusLimitControllableSystemUseCase`, ADR-019/021):

1. **No visibility into the Energy Guard's failsafe configuration.** The EG's Scenario-2 writes
   (`FailsafeConsumptionActivePowerLimit`/`FailsafeProductionActivePowerLimit`,
   `FailsafeDurationMinimum`) are only forwarded to `eebus`-tagged Item metadata
   (`onFailsafeLimitWritten`/`onFailsafeDurationWritten`). The user explicitly decided these
   values must stay strictly EEBus-writable - no Item, metadata, or Rule may change them, only a
   real Energy Guard over EEBus - but also confirmed a read-only visibility Channel is important,
   so a Rule/dashboard can see the current configuration without an Item being tagged first
   (mirrors the zero-configuration experience ADR-021 already gives `limit-active`/`limit-value`).
1. **Startup/reconnect staleness.** Tracing the code for a related question (does a reconnecting
   Energy Guard need to resend everything?) found that `setupLoadControl()` correctly resets the
   SPINE-visible `LoadControlLimitData` to `isLimitActive=false` on every restart, but
   `onStateChanged` - the single place that updates the `LPC.state`/`LPP.state` metadata Item and
   the oh-entity's `limit-active`/`limit-value` Channel (ADR-021) - is only invoked on the _first_
   real state transition (a 120s Heartbeat timeout, or a fresh Energy Guard write). Until then,
   those two openHAB-side artifacts keep showing whatever was last published before the restart,
   for up to two minutes, even though the SPINE wire protocol itself is already correctly reset.

On the reconnect question itself: `EEBusLimitControlStateMachine`'s existing Heartbeat-timeout
fallback (`INIT`/`FAILSAFE` -> `UNLIMITED_AUTONOMOUS` after 120s of Heartbeat silence) already
means the binding does not depend on the Energy Guard proactively resending its values after a
reconnect - a well-behaved but silent-until-timeout EG still results in a safe, bounded fallback.
No new "ask the peer to resync" mechanism is needed; this ADR only closes the separate
local-publish-timing gap found while confirming that.

## Decision

**Add two read-only Channels, `failsafe-limit-value` (`Number:Power`) and
`failsafe-duration-minimum` (`Number:Time`)**, created dynamically on the tracked Energy Guard's
paired `eebus:oh-entity` under the existing `lpc`/`lpp` Channel Groups - via a new
`EEBusOhPeerHandler#applyFailsafeStatus(String, double, long)`, following the exact
`ensureChannel`/`updateCachedState` pattern `applyLimitStatus` (ADR-021) already established. No
`handleCommand` forwarding is added for these Channels - they are populated exclusively from
`onFailsafeLimitWritten`/`onFailsafeDurationWritten` (i.e. from the Energy Guard's own EEBus
writes), matching the user's explicit "EEBus-only, never openHAB" decision.

**Publish the current state and failsafe status explicitly at two points**, instead of only
inside the reactive write/transition handlers:

- In `setup()`, immediately after constructing the state machine (and after `setupLoadControl`/
  `setupDeviceConfiguration` have already reset the SPINE-visible data): call
  `onStateChanged(newStateMachine.getState())` and `publishFailsafeStatus()` once, unconditionally.
- In `onEnergyGuardFound`, immediately after a partner resolves to a non-null
  `EEBusOhPeerHandler`: the same two calls, so a peer that resolves _after_ status was already
  known (e.g. status changed once already, before this specific peer was found) is not left
  showing stale data either.

`publishFailsafeStatus()` is a new private helper that reads the tracked
`energyGuardOhPeerHandler` field and, if non-null, calls `applyFailsafeStatus` with the current
`lastFailsafeLimitWatts` (new field, mirrors the existing `lastWrittenLimitValue` pattern) and
`failsafeDurationMinimumSeconds` (existing field). No-op if no peer is resolved yet - mirrors the
null-check idiom `onStateChanged` already uses for the same field.

## Consequences

### Positive

- A user pairing openHAB (Controllable System) against a real Energy Guard now gets automatic,
  discoverable visibility into the currently configured failsafe limit/duration - no Item tagging
  required, and no way to accidentally override it from openHAB (Items, metadata, Rules), matching
  the safety property the user asked for: only a real Energy Guard over EEBus can change it.
- Closes a real, previously undocumented staleness window: after an openHAB restart, a Rule
  reacting to `lpc#limit-active`/`lpp#limit-active` (or the new failsafe Channels) no longer sees
  a value from before the restart for up to 120 seconds.
- Reuses the exact `ensureChannel`/`updateCachedState`/null-check patterns already established by
  ADR-021 - no new Channel-creation mechanism, no new idempotency logic.
- Confirms (rather than changes) the existing Heartbeat-timeout fallback design as the answer to
  "what if the Energy Guard doesn't resend its values on reconnect" - no new resync protocol
  needed.

### Negative

- `AbstractEEBusLimitControllableSystemUseCase` gains one more field (`lastFailsafeLimitWatts`)
  and one more helper method; `setup()`/`onEnergyGuardFound` both grow slightly to make the
  explicit publish calls. No constructor signature change this time (unlike ADR-021) - no
  lockstep changes needed in `EEBusLpcServerUseCase`/`EEBusLppServerUseCase`/`EEBusHandler`.
- The explicit `setup()`-time publish always logs/updates `LPC.state`/`LPP.state` to `INIT` on
  every restart, even when nothing actually changed from before - a harmless, idempotent
  re-publish, but worth noting as intentional, not an oversight.
- Not yet compiled (no Maven in the editing sandbox, same limitation as ADR-016 through ADR-021)
  or live-retested - reviewed manually only (brace/paren balance checked programmatically per
  touched file, `thing-types.xml` re-parsed for well-formedness, CRLF preserved throughout).

## Verification status

Not yet verified: `mvn clean install`, and a live retest confirming (a) the new failsafe Channels
populate when a real or self-built Energy Guard writes a new failsafe limit/duration, and (b) an
openHAB restart while a limit was previously active no longer leaves `LPC.state`/`limit-active`
showing the pre-restart value for up to 120 seconds - both user-owned, same pattern as every prior
ADR in this series.

## Related, explicitly deferred

While investigating this area, a third gap was confirmed: `AbstractEEBusLimitEnergyGuardUseCase`
(the Client/Energy-Guard role) has no `DeviceDiagnosis` Server feature and never calls
`HeartbeatDataFunction#startHeartbeat()` - i.e. it does not send its own half of the mutual
Heartbeat the LPC/LPP spec requires (Scenario 3), even though the Controllable System role's own
Heartbeat is a proven, self-perpetuating two-line fix (`setupDeviceDiagnosis()`). The user
decided (2026-08-23) to defer implementing this, but explicitly rejected a `ThingAction`-plus-Rule
design for it (a missed or misconfigured Rule would silently break a safety-relevant mechanism);
the low-risk fix is to mirror the Controllable System's existing self-perpetuating
`setupDeviceDiagnosis()` pattern once this is picked up. Tracked as CONCEPT.md §7 item (19), not
implemented as part of this change.
