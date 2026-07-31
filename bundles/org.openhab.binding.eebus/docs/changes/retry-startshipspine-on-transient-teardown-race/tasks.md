# Tasks: Retry startShipSpine() on transient teardown-race exceptions

## 1. Retry-with-backoff

- [x] 1.1 Add `START_RETRY_MAX_ATTEMPTS` (3) and `START_RETRY_BACKOFF_BASE_MILLIS` (250) constants
- [x] 1.2 `startShipSpine()` retries `startShipSpineLocked()` on `BindException` or
  `OverlappingFileLockException`, up to the max attempts, sleeping `attempt * backoff` between
  tries
- [x] 1.3 Re-check the generation under `lifecycle.lock` before each retry; give up immediately
  (without a further attempt) if superseded
- [x] 1.4 Any other exception, or exhausting all attempts, rethrows exactly as before (no change
  to the existing outer `catch (Exception e)` in `initialize()`'s scheduled task)
- [x] 1.5 Update the class javadoc to describe the retry and reference this ADR

## 2. Documentation

- [x] 2.1 `docs/ADR/029-retry-startshipspine-on-transient-teardown-race.md` (new, Accepted)
- [x] 2.2 `docs/changes/retry-startshipspine-on-transient-teardown-race/{proposal.md,tasks.md,specs/thing-lifecycle/spec.md}`

## 3. Verification (user-owned, no compiler in this sandbox)

- [ ] 3.1 `mvn clean install`
- [ ] 3.2 Live retest: reproduce a Bridge rebuild (e.g. a child-Entity change) and confirm the
  `BindException`/`OverlappingFileLockException` either no longer occurs, or is now followed by a
  logged retry that succeeds within the 3-attempt budget
- [ ] 3.3 Live sanity check: confirm a genuinely superseded generation's retry loop gives up
  immediately (a debug log line) rather than retrying to exhaustion
