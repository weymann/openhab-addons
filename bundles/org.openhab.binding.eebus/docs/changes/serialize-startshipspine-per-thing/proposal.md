# Proposal: Serialize startShipSpine() per Thing and debounce onEntityChanged()

## Intent

A 2026-08-26 startup trace (see project memory: `eebus-startup-trace-2026-08-26.md`) confirmed
that overlapping generations of `EEBusHandler#startShipSpine()` for the same Thing can run
concurrently, because `dispose()`/`initialize()` never block on an in-flight start attempt
(`docs/ADR/005-supersede-stale-start-on-dispose.md`) and ADR-027's `onEntityChanged()` fires a
full `dispose()`/`initialize()` rebuild on every single child Entity change. Two concrete,
confirmed bugs resulted:

- Two overlapping generations for the same Thing opened the same SHIP keystore (`.jks`) file
  concurrently, and one lost a JVM-wide file-lock race, throwing `OverlappingFileLockException`.
  In one observed case the Thing that lost this race was the _current_ generation, leaving the
  Thing stuck offline until an unrelated later event happened to trigger another rebuild.
- `EEBusPortPool` logged spurious "not reserved from the pool" warnings, and in one case an
  actual `Address already in use` failure, from a stale generation's own port re-acquisition
  running concurrently with a newer one.

## Scope

In scope:

- Serialize `startShipSpine()`'s actual body (now `startShipSpineLocked()`) per Thing, via a lock
  held for the entire duration of a start attempt, so two generations for the same Thing never
  touch the keystore file or bind a port at the same time.
- Debounce `onEntityChanged()` so a burst of near-simultaneous child-Entity change notifications
  coalesces into a single `dispose()`/`initialize()` rebuild instead of one per notification.

Out of scope:

- The empty-`supportedFormats`/`IllegalArgumentException: Formats should not be empty` symptom
  from the same trace - traced to `jeebus.ship`'s `StaticConfiguration` failing to find
  `config.properties` via `getResourceAsStream()` under still-unconfirmed circumstances (suspected
  classloading contention during the same startup burst, not confirmed). This change reduces the
  concurrency pressure that may contribute to it, but does not claim to fix it, and no
  `jeebus.ship` code is touched here (protected, human approval required per project
  instructions).
- The `EEBusPortPool` "not reserved from the pool" log line itself is largely a separate, benign
  side effect of the pool's existing by-design port-retention behavior (a Thing's port is only
  released in `handleRemoval()`, never `dispose()` - see `EEBusPortPool`'s class javadoc)
  re-triggering `reservePort()` on every `initialize()`, including debounce-coalesced ones; the
  debounce reduces how often this fires, but changing the pool's own re-reservation/logging
  behavior is not part of this change.
- Any change to `jeebus.ship`/`jeebus.spine` (protected, human approval required).

## Open Questions

- None outstanding - design confirmed with the user via `AskUserQuestion` 2026-08-26 (combined
  mutex + debounce, over "mutex only" or "debounce only").
