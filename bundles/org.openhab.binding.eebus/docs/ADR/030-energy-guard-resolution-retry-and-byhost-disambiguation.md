# ADR-030: Retry Energy Guard resolution with backoff, disambiguate same-host mDNS peers

## Status

Accepted

## Context

A 2026-08-27 00:11 live retest (the first restart after the channel-group-casing fix - see
project memory `eebus-lpc-lpp-channelgroup-casing-bug-2026-08-26.md`) showed the "Controllable
System Entity" Thing's `lpc#limit-active`/`lpp#limit-active` Channels still not updating, even
though its Item's Channel Link was verified correct (the real lowercase Channel, not an orphaned
one - so the casing fix was not the cause here). The full startup-window log for the CS Bridge
(`eebus:oh-device:ba56bcd5a0`) showed the actual cause:

```text
LPC: Energy Guard partner at 192.168.1.25:55312 could not be resolved to a paired eebus:oh-entity
  handler - its limit-active/limit-value Channel will not be updated
LPC: Energy Guard partner at 192.168.1.25:55320 could not be resolved to a paired eebus:oh-entity
  handler - its limit-active/limit-value Channel will not be updated
```

(same pair for LPP), both from `AbstractEEBusLimitControllableSystemUseCase#onEnergyGuardFound`,
across two back-to-back generations caused by the usual `onEntityChanged()` churn (ADR-027).

Traced to two compounding bugs, both entirely inside this binding (not `jeebus.ship`/
`jeebus.spine` - the SPINE discovery notification itself was delivered correctly and promptly):

1. **One-shot resolution, no retry.** `onEnergyGuardFound` resolves the discovered partner's
   `communicationAddress` to a paired `eebus:oh-entity` handler exactly once, via
   `EEBusHandler#ohEntityHandlerForCommunicationAddress` -> `EEBusMdnsBrowser
   #skiForCommunicationAddress`. At `00:10:43.786`/`00:10:48.364` this Bridge's own
   `EEBusMdnsBrowser` instance had not yet resolved the Energy Guard's mDNS record at all (that
   only happened later, in a burst at `00:10:51.9xx` - roughly 3.6-8.2 s after the failed
   attempts). Since nothing retries the resolution afterward, it stays permanently unresolved for
   that generation, and `applyLimitStatus`/`applyFailsafeStatus` are simply never called.
1. **`byHost` ambiguity.** `EEBusMdnsBrowser`'s host-only fallback match (needed because a live
   SPINE connection's `communicationAddress` is the peer's ephemeral outbound port, never its
   mDNS-advertised one - see that class's own "Live vs. advertised address mismatch" javadoc) was
   a `Map<String, DiscoveredService>` keyed by bare host only - one entry per host. Any deployment
   with more than one EEBUS device behind the same host IP (this project's own EG/CS self-test
   rig included: `energy-guard-sim` and the CS Bridge both resolve to `192.168.1.25`) means the
   second same-host service to resolve silently overwrites the first, so even once populated the
   map could return the _wrong_ peer's SKI instead of failing safe. This was already flagged as a
   known risk in the class's own javadoc, but not yet fixed.

## Decision

### 1. Retry `onEnergyGuardFound`'s handler resolution with backoff

`onEnergyGuardFound` now delegates the resolution step to a new
`resolveEnergyGuardOhEntityHandler(UseCasePartner partner, int attempt)`. On a miss, it
reschedules itself via the existing per-instance `scheduler` (already used for the
`EEBusLimitControlStateMachine` Heartbeat watchdog) with a bounded backoff of
`{ 2, 4, 8, 16, 30 }` seconds (5 attempts, ~60 s total) - deliberately more generous than
ADR-029's 250/500/750 ms `startShipSpine()` retry, since mDNS resolution timing is inherently
less predictable than outlasting a previous generation's teardown tail, and the ~8 s gap actually
observed live needs real headroom above it.

A new `latestEnergyGuardCommunicationAddress` field, set by `onEnergyGuardFound` immediately
before delegating, lets a pending retry recognize it has been superseded by a newer discovery
notification (e.g. a reconnect with a different live `communicationAddress`) and drop itself
instead of risking a stale resolution overwriting a newer one - the same spirit as ADR-028/029's
generation rechecks before each retry, adapted to this class (which has no existing generation
counter of its own).

On success at any attempt, the resolution pushes the currently known status immediately (the
same behavior `onEnergyGuardFound` always had for an immediate success - ADR-022).
`RejectedExecutionException` from `scheduler.schedule(...)` (thrown if `close()` has already shut
the scheduler down while a retry was pending) is caught and dropped silently - nothing left to
update.

### 2. Disambiguate same-host mDNS peers in `EEBusMdnsBrowser`

`byHost` is now `Map<String, Map<String, DiscoveredService>>` - host to (SKI to service), so more
than one distinct peer sharing a host is tracked rather than one overwriting the other.
`skiForCommunicationAddress`'s host-fallback path now checks candidate count for the matched
host: zero candidates behaves exactly as before (debug log, `Optional.empty()`); exactly one
candidate returns it (unchanged common case); **more than one candidate now fails closed** - logs
a distinct "ambiguous - N distinct SKIs share this host" debug line and returns
`Optional.empty()`, instead of returning whichever service happened to resolve most recently.
`serviceResolved`/`serviceRemoved` updated accordingly (`computeIfAbsent(...).put(...)` /
per-host `remove(ski)` + prune empty host entries).

In the specific rig that surfaced this (CEM/CS and Energy Guard sharing `192.168.1.25`, KeoApp on
a separate `192.168.1.30`), the CS Bridge's own browser instance only ever has _one_ non-self
candidate for `192.168.1.25` (Energy Guard) once resolved, so this change does not by itself
block that resolution - it only changes behavior for a genuinely ambiguous host, converting a
silent wrong-peer risk into a safe, loud failure.

## Consequences

### Positive

- Closes the confirmed 2026-08-27 failure: the CS entity's `lpc#limit-active`/`lpp#limit-active`
  Channels get updated once mDNS resolution catches up, without requiring another restart or a
  manual Bridge disable/enable.
- `byHost`'s silent-wrong-peer risk (already flagged in its own javadoc, not previously fixed) is
  closed - an ambiguous host now fails safe and loud instead of guessing.
- Narrow, bounded, and self-cancelling: retries stop as soon as resolution succeeds, are capped at
  5 attempts (~60 s), and a superseded partner's retry chain drops itself rather than running
  forever or clobbering a newer resolution.
- No `jeebus.ship`/`jeebus.spine` code touched (protected, human approval required) - both fixes
  are entirely inside `org.openhab.binding.eebus`.
- No `pom.xml`/dependency change.

### Negative

- Does not _guarantee_ eventual resolution - still a bounded retry against a timing race, not a
  synchronization fix (SPINE's discovery layer exposes no "mDNS has caught up" signal this
  binding could wait on instead). A sufficiently slow/flaky mDNS environment could still exhaust
  all 5 attempts and fail, same failure mode as before but delayed ~60 s.
- The `byHost` disambiguation fix means a genuinely ambiguous same-host deployment (more than one
  trusted peer behind one IP) will now _never_ resolve via the host-only fallback, where before it
  had a 50/50-ish chance of accidentally getting the right one. This is intentional (silent wrong
  behavior is worse than a clear failure for something that gates a real load-control action), but
  is a strictly narrower fallback than before for that specific deployment shape.
- `resolveEnergyGuardOhEntityHandler`'s retries add up to ~60 s of extra delay in the worst case
  before the CS entity's Channels first populate after a Bridge (re)start - judged acceptable
  since the previous behavior was an unbounded, permanent failure for the same case.
- Source-level only, **not yet compiled or live-tested** - no local Maven/openHAB instance
  available in this environment (same limitation noted in every prior ADR in this project).
  Self-QA done: brace/paren balance and CRLF-only line endings verified on both touched files
  (`AbstractEEBusLimitControllableSystemUseCase.java`, `EEBusMdnsBrowser.java`) before/after the
  edits.

## Diagram

```mermaid
sequenceDiagram
    participant Spine as jeebus.spine<br/>(UseCasePartner discovery)
    participant CS as AbstractEEBusLimitControllableSystemUseCase<br/>onEnergyGuardFound
    participant Browser as EEBusMdnsBrowser<br/>(this Bridge's own instance)

    Spine->>CS: onEnergyGuardFound([partner])
    CS->>Browser: skiForCommunicationAddress(partner.address)
    Browser-->>CS: empty (peer's mDNS record not resolved yet)
    Note over CS: attempt 0 failed - schedule retry in 2s
    Browser->>Browser: serviceResolved(Energy Guard) - byHost populated
    CS->>Browser: skiForCommunicationAddress(partner.address)  (attempt 1, +2s)
    Browser-->>CS: ski (exactly one candidate for this host)
    CS->>CS: energyGuardOhEntityHandler = resolved; push status (ADR-021/022)
```

## Not yet done / user-owned

- `mvn clean install` + a live redeploy/restart retest, to confirm the CS entity's
  `lpc#limit-active`/`lpp#limit-active` Channels now populate on a normal restart without needing
  the previously-documented Bridge disable/enable workaround.
