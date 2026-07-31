# ADR-017: Idempotent Client-Role Use-Case-Partner Subscription

## Status

> Accepted (2026-08-21)

## Context

After the pom.xml build fix (ADR-016) and a fresh restart, live logs showed the Client-role
LPC/LPP read path firing multiple times for a single real state change:

```text
21:19:25.899 lpc.limit = active=false value=0.0 W for oh-entity '...e554d8b410'
21:19:25.901 lpp.limit = active=false value=0.0 W for oh-entity '...e554d8b410'
21:19:25.928 lpc.limit = active=false value=0.0 W for oh-entity '...e554d8b410'
21:19:25.931 lpp.limit = active=false value=0.0 W for oh-entity '...e554d8b410'
21:19:25.945 lpc.limit = active=false value=0.0 W for oh-entity '...e554d8b410'
21:19:25.948 lpp.limit = active=false value=0.0 W for oh-entity '...e554d8b410'
```

Six lines for one logical event: `lpc.limit`/`lpp.limit` each three times, ~25-50ms apart. LPC
and LPP appearing together is expected (they are separate use cases, each logging
independently); the anomaly is each one individually firing three times.

Root cause: `{@code onUseCasePartnersFound(List)}`'s own documented contract (see
`EEBusMpcClientUseCase`'s class javadoc, "How detection works") is that jeebus.spine "calls
`onUseCasePartnersFound(List)` **whenever the set of matching peers changes**" - it is not a
one-shot callback, and each re-invocation passes the _current full partner list_ again,
including partners already seen in an earlier invocation. Both `AbstractEEBusLimitEnergyGuardUseCase`
(shared by the LPC/LPP Client use cases) and `EEBusMpcClientUseCase` processed every partner in
the list unconditionally on every invocation:

- `AbstractEEBusLimitEnergyGuardUseCase#onUseCasePartnersFound` called `subscribeLimitStatus`
  (a brand new `NodeManagement#requestSubscription`) and `registerWriteListeners` (a brand new
  `EEBusMetadataService#registerItemStateListener`, stacked on top of any still-registered
  listener from a previous invocation - `writeListenerCleanup` is only drained by `close()`) for
  _every_ partner in the list, already-subscribed or not.
- `EEBusMpcClientUseCase#onUseCasePartnersFound` had the identical unguarded pattern via
  `subscribe`.

Each re-invocation therefore created one more independent SPINE subscription (explaining the
observed 3x duplicate `applyLimitStatus`/`applyMeasurement` log lines) and, for the LPC/LPP
write path specifically, one more `Item` state listener - meaning a single Item toggle would
have produced a multiplying number of outbound SPINE `LoadControlLimitData` writes to the real
peer, not just duplicate logging. This was not caught earlier because it only manifests once
`onUseCasePartnersFound` is actually invoked more than once for the same partner, which requires
a live SPINE connection with a peer whose use-case-partner set is re-announced - not exercised
by the build-only checks that were possible before ADR-016 fixed `karaf-feature-verification`.

## Decision

Track already-processed partners per use case instance (`Set<String> subscribedPartners`, keyed
by `UseCasePartner#getCommunicationAddress()`, backed by `ConcurrentHashMap.newKeySet()` since
`onUseCasePartnersFound` is invoked from a jeebus.spine callback thread). A partner already
present in the set is skipped on a later invocation instead of being re-subscribed. A partner
that fails to resolve to a paired `eebus:oh-entity` Thing (the existing `ohPeerHandler.isEmpty()`
branch) is deliberately _not_ added to the set, so a later invocation can still succeed for it
once, for example, mDNS has caught up.

Applied identically to both call sites that share this pattern:

- `AbstractEEBusLimitEnergyGuardUseCase` (LPC/LPP Client role - the class actually observed
  misbehaving).
- `EEBusMpcClientUseCase` (MPC Client role - same unguarded pattern, same latent bug, not yet
  observed to misbehave live only because it has no write-path side effect to make the
  duplication as consequential, but the duplicate-subscription/duplicate-log behavior is
  identical).

Considered and rejected: tearing down and re-establishing the old subscription/listeners on
every re-invocation instead of skipping. This would correctly handle a partner's feature address
genuinely changing between invocations, but jeebus.spine's `NodeManagement` API exposes no
verified unsubscribe operation for a `requestSubscription`-created subscription, and introducing
one would be new, unverified SPINE API surface. The simpler dedupe-by-communication-address
guard is strictly better than the previous unguarded behavior in every case observed so far, and
does not require touching jeebus.spine.

## Consequences

### Positive

- A single real SPINE notification now produces exactly one `applyLimitStatus`/`applyMeasurement`
  log line per use case, not N.
- The Client-role write path (`registerWriteListeners`) now registers each tagged Item's state
  listener exactly once per Bridge session, so a single Item toggle sends exactly one SPINE
  write, not a multiplying number of them.
- No new SPINE API surface introduced; `jeebus.spine`/`jeebus.ship` untouched.

### Negative

- If a partner's `LoadControl`/`Measurement` feature address genuinely changes while the Bridge
  stays connected (e.g. the peer restarts and re-announces with a new address), this binding will
  not re-subscribe to the new address until the Bridge itself is restarted (`close()`/`setup()`
  cycle, which clears `subscribedPartners`). Not previously handled correctly either (the old
  code would have added an extra subscription for the new address on top of a now-stale one
  pointing at the old address, not replaced it), so this is not a regression - just an
  still-open gap, not tracked as a proposal since no concrete case of this happening has been
  observed.

---

_Adds `AbstractEEBusLimitEnergyGuardUseCase#subscribedPartners` and
`EEBusMpcClientUseCase#subscribedPartners`; both `onUseCasePartnersFound` methods gain a guard
against re-processing an already-subscribed partner. No other file changes._
