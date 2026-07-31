# Proposal: Controllable System failsafe status Channel and startup/reconnect status sync

## Intent

Two related gaps found while discussing the LPC/LPP Controllable System (CS) role with the user
(`$Concept`, 2026-08-23), on the way to a possible future "convenience Thing" for the CS role:

1. The failsafe limit/duration the Energy Guard (EG) configures over EEBus (Scenario 2) is
   currently only visible in openHAB via `eebus`-tagged Item metadata
   (`LPC.failsafeConsumptionLimit`/`LPC.failsafeDurationMinimum` and the LPP equivalents). The
   user explicitly decided (2026-08-23) that these values must never be changeable from openHAB
   (no Item, no metadata, no Rule) - only a real EG writing over EEBus may change them - but
   confirmed a **read-only** visibility Channel is "definitiv umsetzen sehr wichtig" (definitely
   implement, very important), so a Rule/dashboard can see what the EG has configured without
   parsing logs.
1. Tracing `AbstractEEBusLimitControllableSystemUseCase`/`EEBusLimitControlStateMachine` for a
   related question (does a reconnecting EG need to resend all values?) surfaced a real, distinct
   gap: `setupLoadControl()` correctly resets the SPINE-visible `LoadControlLimitData` to
   `isLimitActive=false` on every restart, but the openHAB-side artifacts driven by
   `onStateChanged` (the `LPC.state`/`LPP.state` metadata Item, and the oh-entity's dynamic
   `limit-active`/`limit-value` Channel from ADR-021) are only updated on the _first_ real state
   transition - a Heartbeat timeout (120s) or a fresh EG write. Until then they keep showing
   whatever was last published before the restart, which can be stale for up to two minutes.

The state-machine's own 120s Heartbeat-timeout fallback (`INIT`/`FAILSAFE` ->
`UNLIMITED_AUTONOMOUS`) already means the binding does not need to rely on the EG proactively
resending its values after a reconnect - that was the user's original question, and the
existing design already answers it. This change closes the separate startup-staleness gap found
along the way, and adds the read-only failsafe Channel as its own, additive piece.

## Scope

In scope:

- A new read-only Channel pair, `failsafe-limit-value` (`Number:Power`) and
  `failsafe-duration-minimum` (`Number:Time`), created dynamically on the tracked Energy Guard's
  paired `eebus:oh-entity` under the existing `lpc`/`lpp` Channel Groups - mirrors the existing
  `limit-active`/`limit-value` mechanism from ADR-021, via a new
  `EEBusOhPeerHandler#applyFailsafeStatus(String, double, long)`.
- No write path added for these two Channels or their underlying `DeviceConfiguration` key/value
  data - read-only by design, per the user's explicit decision.
- Explicit initial publish of the current state/failsafe status at `setup()` (closing the
  startup-staleness gap for `LPC.state`/`LPP.state` metadata, the `limit-active`/`limit-value`
  Channel, and the two new failsafe Channels).
- The same explicit publish repeated once a tracked Energy Guard peer is freshly resolved in
  `onEnergyGuardFound`, so a peer that resolves after status was already known does not lag
  behind either.

Out of scope:

- Persisting the failsafe values (or any CS state) across restarts via `StorageService` - the
  user's direction was to rely on the existing Heartbeat-timeout fallback and a fresh
  Thing-config seed, not local persistence; revisit only if a future retest shows the 120s window
  is not acceptable in practice.
- The Energy-Guard-side (Client role) `DeviceDiagnosis`/Heartbeat gap
  (`AbstractEEBusLimitEnergyGuardUseCase` does not yet expose its own Heartbeat) - user decision
  (2026-08-23): take the low-risk fix (mirror the CS role's self-perpetuating
  `HeartbeatDataFunction#startHeartbeat()` two-liner) later, tracked as CONCEPT.md §7 item (19)
  for now, not part of this change.
- Any new "convenience Thing" type (`oh-cs-service` or similar) - still only a `$Concept`-level
  idea, not scoped.

## Open Questions

- None blocking - both scoped items build directly on already-implemented, already-reviewed
  mechanisms (ADR-021's `applyLimitStatus` pattern, `KeyValueInitialData`/`RunningKeyValue`).

---
