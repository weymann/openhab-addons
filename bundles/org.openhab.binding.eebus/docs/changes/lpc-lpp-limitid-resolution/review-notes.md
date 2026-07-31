# $QA / $Review Notes: LPC/LPP LoadControl limitId Resolution

## Status

Reviewed 2026-08-21, alongside `$Dev` implementation of `tasks.md` items 1.1-2.4 and 4.1 (ADR-018).

## $QA: edge cases considered

- **Peer supports only one direction** (e.g. LPC but not LPP): the non-supported use case's
  `resolveLimitId` finds no matching `limitDirection` entry and correctly reports no status /
  disables the write path for that peer, per spec Scenario "Peer's description read fails or
  contains no matching direction." This is new, _correct_ behavior - before this change, the
  non-supported direction would have piggybacked on the other direction's `limitId=0` entry and
  shown a bogus status.
- **Deliberate no-fallback**: unlike `EEBusMpcClientUseCase#resolvePowerMeasurementId`, the new
  `resolveLimitId` does **not** fall back to a sole remaining description entry when no
  `limitDirection` match is found. A sole-entry fallback would silently reintroduce the exact
  failure mode this change fixes (assuming an entry belongs to a direction without confirming
  it), so it is intentionally omitted - confirmed against ADR-018's ratified failure mode.
- **Thread-safety**: `subscribedPartners` (unchanged, `ConcurrentHashMap.newKeySet()`) still
  guards against re-entrant `onUseCasePartnersFound` invocations; the new
  `resolveLimitIdAndSubscribe` runs entirely inside that guard, so no new race is introduced.
- **`setup()`'s `CommunicationPartnerFeatureRequirement`** now additionally requires
  `LOAD_CONTROL_LIMIT_DESCRIPTION_LIST_DATA` (mandatory) alongside the existing
  `LOAD_CONTROL_LIMIT_LIST_DATA`, mirroring `EEBusMpcClientUseCase`'s identical dual requirement
  for `MEASUREMENT_LIST_DATA`/`MEASUREMENT_DESCRIPTION_LIST_DATA`. This is a necessary
  consequence of the fix (a peer that cannot answer a description read cannot be safely
  resolved) and matches the real Hager S10 discovery capture, which already advertises both
  functions on its `LoadControl` feature - no regression expected against the binding's primary
  real-hardware validation target.
- **No test coverage added**: `resolveLimitId`/`resolveLimitIdAndSubscribe` have no unit test,
  consistent with the pre-existing state of this class family (`AbstractEEBusLimitEnergyGuardUseCase`,
  `AbstractEEBusLimitControllableSystemUseCase`, and `EEBusMpcClientUseCase#resolvePowerMeasurementId`
  are all untested today - `src/test/java/.../transport/` only covers `EEBusMetadataService`).
  Flagged as a pre-existing gap, not a regression introduced by this change; a follow-up change
  to add SPINE-mock-based unit tests for this class family would be reasonable but is out of
  scope here.

## $Review: 44-point checklist (items applicable to this change)

Only transport/use-case Java files were touched - no `pom.xml`, `thing-types.xml`, `addon.xml`,
README, or i18n changes, so most Structure/Documentation/Thing-Design checklist items are not
applicable to this change.

- ✅ `@NonNullByDefault` present on all touched classes (unchanged).
- ✅ camelCase naming for all new identifiers (`resolveLimitIdAndSubscribe`, `resolveLimitId`,
  `getLimitId`, `getLimitDirection`, `limitId` parameters).
- ✅ Primitive `long limitId` used for all new parameters; `Optional<Long>` return type on
  `resolveLimitId` matches the established `Optional<Long>` convention already used by
  `resolvePowerMeasurementId` in the sibling MPC class.
- ✅ Conservative log levels: `debug` for the expected "no match" case, `warn` (with the causing
  exception) only for the genuine description-read failure - matches the existing convention in
  `subscribeLimitStatus`'s own read/subscribe handlers.
- ✅ No empty catch blocks, no `Throwable` catching, no new `Closeable` resources, no new threads
  - none of these were touched or introduced.
- ⚠️ **Duplication**: `resolveLimitIdAndSubscribe`/`resolveLimitId` closely mirror
  `EEBusMpcClientUseCase#subscribe`/`resolvePowerMeasurementId`. This mirrors pre-existing
  duplication already present between the LPC/LPP and MPC Client-role classes (e.g.
  `subscribeLimitStatus` vs. `subscribe` already duplicated the read-then-subscribe wiring
  before this change) - not a new problem introduced here, but worth a future shared-helper
  extraction if a fourth Client-role use case is ever added.
- ⚠️ **Static analysis not run**: this sandbox has no local Maven/`.m2`, so `mvn spotless:check`,
  `checkstyle`, and `javadoc:javadoc` could not be executed - same caveat already recorded for
  ADR-016/017. The user's own `mvn clean install` (tasks.md 3.2) is the first real compiler/
  checkstyle/spotless pass this change will get.
- N/A: all Handler/Runtime, Thing/Channel, i18n, and pom.xml-related checklist items (no
  `ThingHandler`, Channel, or dependency changes in this change).

## Spec-compliance check (`specs/lpc-lpp-limit-control/spec.md`)

All three Requirements / four Scenarios are structurally satisfied by the implementation:

- "Server-role LPC and LPP limits use distinct limitId values" - `getLimitId()` returns `0L`/`1L`
  respectively (ADR-018).
- "Client-role LPC/LPP resolve limitId by limitDirection instead of assuming it" - both the
  happy-path Scenario and the "no matching direction" failure-mode Scenario are implemented by
  `resolveLimitIdAndSubscribe`/`resolveLimitId`.
- "LPC and LPP Client-role write paths remain independent per direction" - `sendLimitWrite` now
  always uses the resolved, direction-specific `limitId`, so a write for one direction can no
  longer touch the other direction's entry - the originally reported symptom (toggling LPC also
  flipping LPP's read-back status) is now structurally impossible, not just less likely.

## Outcome

No additional code changes made as a result of this review - the `$Dev` implementation already
matches ADR-018 and the change's spec. Remaining open items are external to this sandbox: tasks
3.1 (live retest) and 3.2 (`mvn clean install`), both owned by the user.

## Addendum (2026-08-21, ~22:35): $QA / $Review of the group-5 Server-side write-handling fix

### Status

Follow-up review after task 3.1's live retest found a second, Server-side defect (see
`proposal.md` Amendment and `docs/ADR/019-isolate-lpc-lpp-server-write-handling.md`). This
addendum covers `$Dev`'s implementation of `tasks.md` group 5 only - groups 1-4 above are
unchanged and remain valid.

### $QA: edge cases considered

- **Write with no `limitId` set.** `Objects.equals(data.getLimitId(), getLimitId())` returns
  `false` when `data.getLimitId()` is `null` (rather than throwing), so an incoming write that
  omits `limitId` is safely ignored by both directions rather than crashing or being guessed at.
- **`onStateChanged` firing before `limitDataIndex` is assigned.** Covered explicitly by the
  `idx < 0` guard added in `onStateChanged` - logs a warning and skips the publish rather than
  falling back to a wrong index (which is exactly the class of bug this change fixes). This path
  is reachable if the initial `addData()` in `setupLoadControl()` throws
  `DataValidationException` (already caught/logged there) - `EEBusLimitControlStateMachine`'s
  constructor synchronously arms a heartbeat watchdog and can in principle transition state
  before `setup()` returns, so this guard is not purely defensive.
- **Cross-thread visibility of `limitDataIndex`.** Caught during this review: `limitDataIndex` is
  written once during `setup()` (via `setupLoadControl()`) and read later from `onStateChanged`,
  which runs as an `EEBusLimitControlStateMachine.Listener` callback - potentially on the
  scheduler thread, not the thread that ran `setup()`. The initial implementation declared the
  field as plain `int`, which does not guarantee the writing thread's value is visible to a
  later reader on a different thread. Fixed by declaring it `volatile`, matching this class's
  existing precedent for `failsafeDurationMinimumSeconds` (also written during setup/writes and
  read from callback methods). No `synchronized` block was needed - a single writer-then-many-
  readers `volatile` field is sufficient here, and matches the existing style of this class
  rather than introducing a new locking pattern.
- **Interaction with the existing `subscribedPartners` de-duplication (ADR-017).** Not
  applicable here - `subscribedPartners` guards the Client-role `onUseCasePartnersFound`
  duplicate-subscription bug; this fix is entirely on the Server role's write/publish path,
  which is a different code path and was not touched by ADR-017.
- **No test coverage added.** Consistent with the pre-existing state of this class family, and
  flagged again (harder this time) in `tasks.md` item 6.1, since the lack of tests is exactly
  what let this regression ship past the original ADR-018 review undetected. A reasonable first
  test for a future pass: two use-case instances sharing a mocked `LimitListDataFunction`,
  asserting that writing one's `limitId` never invokes the other's `onLimitWritten`/publishes to
  the other's index.

### $Review: 44-point checklist (items applicable to this change)

Only `AbstractEEBusLimitControllableSystemUseCase.java` was touched - no `pom.xml`,
`thing-types.xml`, `addon.xml`, README, or i18n changes.

- ✅ `@NonNullByDefault` present (unchanged, class-level).
- ✅ camelCase naming for the new identifiers (`limitDataIndex`, `idx`).
- ✅ Primitive `int`/`long` used throughout, no unnecessary boxing (`Objects.equals` is the one
  deliberate exception, needed to compare a possibly-`null` `Long` against a primitive `long`
  safely).
- ✅ Conservative log levels: `warn` only for the two genuinely unexpected cases (unassigned
  index, existing `DataValidationException` catch) - matches this class's existing convention.
  No new `debug`/`info` logging added, and none needed (the early-return in `onLimitWritten` is
  an expected, frequent no-op for the non-matching direction, not worth logging every time).
- ✅ No empty catch blocks, no `Throwable` catching, no new `Closeable` resources, no new
  threads - the existing `try`/`catch (DataValidationException e)` block is preserved unchanged,
  just re-indented one level deeper inside the new `else` branch.
- ✅ Field visibility (`volatile`) - see $QA note above; caught and fixed during this same
  review pass rather than shipped and found later.
- ⚠️ **Duplication**: the new `onLimitWritten` filter and `onStateChanged` index guard are small,
  self-contained additions with no meaningful duplication introduced.
- ⚠️ **Static analysis not run**: this sandbox has no local Maven/`.m2`, so `mvn spotless:check`,
  checkstyle, and `javadoc:javadoc` could not be executed - same caveat already recorded for
  ADR-016/017/018. Verified manually instead: brace/paren balance checked programmatically,
  CRLF line endings preserved (matches the rest of this file and the surrounding repository),
  full diff reviewed line-by-line against the original file. The user's own `mvn clean install`
  (task 3.2) remains the first real compiler/checkstyle/spotless pass this change will get.
- N/A: all Handler/Runtime, Thing/Channel, i18n, and pom.xml-related checklist items (no
  `ThingHandler`, Channel, or dependency changes in this change).

### Spec-compliance check (new requirement in `specs/lpc-lpp-limit-control/spec.md`)

"Server-role LPC and LPP write handling stays isolated per direction" - both Scenarios verified
against the implementation:

- "Energy Guard writes only the LPC limit" - `onLimitWritten`'s new `Objects.equals` filter makes
  it structurally impossible for a write to one `limitId` to reach the other direction's state
  machine.
- "A Server-role use case publishes a state change" - `onStateChanged` now always publishes to
  `this.limitDataIndex`, the index this instance's own `addData()` call was actually assigned,
  never a value shared with or guessed from the other direction.

### Outcome

`$Dev` implementation for `tasks.md` group 5 (items 5.1-5.5) complete, reviewed, and one issue
(missing `volatile`) found and fixed within this same pass. Remaining open items are external to
this sandbox: task 3.2 (`mvn clean install`) and a re-run of task 3.1's live retest, plus the
deferred task 6.1 (unit test coverage) - all owned by the user.

## Live re-retest confirmation (2026-08-21, ~23:06-23:09)

Task 3.1's live retest was re-run against a build including the group-5/ADR-019 fix (bundle
`5.3.0.202608212202`, toggling the `LpcActive` write-path Item only). Result: **PASSED.**

- Every `EEBusLimitControlStateMachine` transition in this log (`INIT -> UNLIMITED_CONTROLLED`,
  `UNLIMITED_CONTROLLED -> LIMITED`, `INIT -> UNLIMITED_AUTONOMOUS`, `LIMITED -> FAILSAFE`,
  `FAILSAFE -> UNLIMITED_AUTONOMOUS`) logs exactly once - the LPC/LPP lockstep-pairing symptom
  from the original 22:22-23:08 retest (each transition logging twice) is gone, consistent with
  Defect 1's fix (`onLimitWritten` no longer reacting to the other direction's write).
- `lpp.limit` never reports `active=true` while only `LpcActive` is toggled; LPP's client-side use
  case instead correctly logs `"lpp LoadControlLimitListData notification had no entry for
  limitId 1, ignoring"` for every LPC-only write, and LPP's own state (`INIT -> UNLIMITED_AUTONOMOUS`
  at 23:06:59.961, independent of LPC's toggle) now evolves entirely on its own schedule.
- Symmetric evidence in the other direction too: once LPP settles, `lpc` correctly logs `"no entry
  for limitId 0, ignoring"` for a notification that only carries LPP's entry.

**Residual, not newly introduced by this change:** the client-side `lpc.limit`/`lpp ... ignoring`
log lines still appear exactly 2x (15-19ms apart) per event, not 1x. In the original 22:22-23:08
retest this was hypothesized to be explained by the 5-8x cross-talk-driven duplication; that
hypothesis is now disproven, since the same clean 2x pattern persists here with cross-talk fixed.
Not root-caused - possibly two distinct SPINE notification deliveries per write event at the
protocol/peer level rather than a binding-side bug - flagged as a new, non-blocking follow-up
candidate rather than fixed in this change.

### Outcome

Task 3.1 fully verified live. `mvn clean install` (task 3.2) is the one remaining item before
`docs/changes/lpc-lpp-limitid-resolution/` can be archived.

---

_Stored at `org.openhab.binding.eebus/docs/changes/lpc-lpp-limitid-resolution/review-notes.md`._
