# Tasks: Serialize startShipSpine() per Thing and debounce onEntityChanged()

## 1. Per-Thing start serialization

- [x] 1.1 Add a `ReentrantLock startLock` to `Lifecycle`, held for the entire duration of a start
  attempt
- [x] 1.2 Split `startShipSpine()`'s existing body into `startShipSpineLocked()`, called only
  while holding `startLock`
- [x] 1.3 Re-check the generation counter immediately after acquiring `startLock`, before doing
  any keystore/port work, so a superseded generation exits cheaply
- [x] 1.4 Update the class javadoc's "Start/dispose concurrency" section to describe the new
  per-Thing serialization

## 2. Debounced onEntityChanged()

- [x] 2.1 Add `ENTITY_CHANGE_DEBOUNCE_MILLIS` constant and a `pendingRebuild` `ScheduledFuture`
  field to `Lifecycle`
- [x] 2.2 `onEntityChanged()` cancels any existing pending rebuild and schedules a new one after
  the debounce window
- [x] 2.3 `dispose()` cancels any pending debounced rebuild before tearing down, so a stale timer
  cannot resurrect a Thing being disposed for a real reason
- [x] 2.4 Update `EEBusEntityChangeListener`'s javadoc to describe the debounce/fire-and-forget
  contract

## 3. Documentation

- [x] 3.1 `docs/ADR/028-serialize-startshipspine-per-thing.md` (new, Accepted)
- [x] 3.2 `docs/changes/serialize-startshipspine-per-thing/{proposal.md,tasks.md,specs/thing-lifecycle/spec.md}`

## 4. Verification (user-owned, no compiler in this sandbox)

- [ ] 4.1 `mvn clean install`
- [ ] 4.2 Live retest: reproduce the original 2026-08-26 startup scenario (two Bridges, each
  gaining a child Entity at startup) and confirm no `OverlappingFileLockException` and no
  `Address already in use` in the log
- [ ] 4.3 Live retest: confirm a Bridge whose child Entities are added/removed in quick
  succession only rebuilds once, roughly 500 ms after the last change
- [ ] 4.4 Live sanity check: disabling/removing a Bridge shortly after a child-Entity change
  does not cause it to unexpectedly come back online (the cancelled-pending-rebuild path)
