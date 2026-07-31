# Tasks: LPC/LPP LoadControl limitId Resolution

## 1. Server-role: distinct limitId per direction

- [x] 1.1 Replace the shared `LIMIT_ID` constant in `AbstractEEBusLimitControllableSystemUseCase`
      with an abstract accessor (e.g. `getLimitId()`); `EEBusLpcServerUseCase`/
      `EEBusLppServerUseCase` supply distinct values (per `$Architect`'s ADR - see proposal.md
      Open Questions).
- [x] 1.2 Verify by structural review (no live peer needed in this sandbox) that both LPC and LPP
      Server `LoadControlLimitDescriptionData` entries coexist in the `LimitDescriptionFunction`
      data store without one overwriting the other.

## 2. Client-role: resolve limitId by limitDirection

- [x] 2.1 Add a description-read step to `AbstractEEBusLimitEnergyGuardUseCase` (mirroring
      `EEBusMpcClientUseCase#resolvePowerMeasurementId`), reading
      `loadControlLimitDescriptionListData` and resolving the `limitId` whose `limitDirection`
      matches this use case's direction (consume for LPC, produce for LPP).
- [x] 2.2 Add an abstract `getLimitDirection()` (or equivalent) so `EEBusLpcClientUseCase`/
      `EEBusLppClientUseCase` declare which direction they resolve.
- [x] 2.3 Update `subscribeLimitStatus`/`applyLimitStatus`/`sendLimitWrite` to use the resolved
      `limitId` instead of the shared `LIMIT_ID` constant.
- [x] 2.4 Handle description-read failure / no matching direction per spec Scenario "Peer's
      description read fails or contains no matching direction" (log, no status reported).

## 3. Verification

- [x] 3.1 Manual retest: toggle the LPC write-path Item only, confirm the LPP read-back status
      on the paired peer stays unchanged (spec Scenario "User toggles only the LPC write-path
      Item"). **Done 2026-08-21 ~22:22-23:08 - FAILED as originally scoped: LPP's status still
      changed.** Root-caused to a second, Server-side defect not covered by tasks 1.1-2.4 (see
      group 5 below) rather than a flaw in the Client-side fix, which is confirmed correct.
      **Re-retested 2026-08-21 ~23:06-23:09 on a build including the group-5 fix - PASSED: each
      `EEBusLimitControlStateMachine` transition now logs exactly once (no more LPC/LPP lockstep
      pairing), and `lpp.limit` never flips to `active=true` while only `LpcActive` is toggled -
      LPP's client-side use case instead correctly logs "no entry for limitId 1, ignoring" for
      every LPC-only write. Confirms the group-5/ADR-019 fix works live.** `mvn clean install`
      (task 3.2) is the only remaining item before this change can be archived.
- [ ] 3.2 `mvn clean install` passes (this sandbox has no Maven - user verifies). Run together
      with the group 5 fix below rather than twice.

## 4. Documentation

- [x] 4.1 `$Architect` records the `limitId`-assignment decision (server-side values,
      resolution-failure handling) as an ADR, referencing the two requirements in
      `specs/lpc-lpp-limit-control/spec.md` that imply it. See
      `docs/ADR/018-lpc-lpp-limitid-assignment.md`.

## 5. Server-side write-handling isolation (found while attempting task 3.1, 2026-08-21)

- [x] 5.1 In `AbstractEEBusLimitControllableSystemUseCase#onLimitWritten`, return early unless
      `Objects.equals(data.getLimitId(), getLimitId())`, so a write to one direction's `limitId`
      no longer triggers the other direction's state machine.
- [x] 5.2 Capture the index `limitFunction.addData(...)` returns in `setupLoadControl()` into a
      new instance field.
- [x] 5.3 In `onStateChanged`, replace the hardcoded `updateData(0, ...)` with
      `updateData(<captured index>, ...)`, so a state change always publishes to this instance's
      own peer-visible list entry, never the other direction's.
- [x] 5.4 Guard the case where `onStateChanged` fires before the index was captured (e.g. the
      earlier `addData` failed validation) - log and skip rather than throw or silently use a
      wrong index.
- [x] 5.5 `$Architect` records this as `docs/ADR/019-isolate-lpc-lpp-server-write-handling.md`,
      referencing the amended requirement in `specs/lpc-lpp-limit-control/spec.md`.

## 6. Deferred (not blocking this change)

- [ ] 6.1 Add unit test coverage for `AbstractEEBusLimitControllableSystemUseCase` (no tests
      exist for this class family today - pre-existing gap, flagged again here since it directly
      let the group-5 regression ship unnoticed past the original `$QA`/`$Review` pass).

---

_Stored at `org.openhab.binding.eebus/docs/changes/lpc-lpp-limitid-resolution/tasks.md`._

_Implementation ($Dev, 2026-08-21) and structural review ($QA/$Review, 2026-08-21) complete for
items 1.1-2.4, 4.1, and 5.1-5.5. Item 3.1 was attempted and failed as originally scoped, then
re-diagnosed and addressed by group 5 - re-retest still needed after rebuild. Items 3.2 and 6.1
remain open pending the user's own rebuild/retest and a future test-coverage change - see
`docs/changes/lpc-lpp-limitid-resolution/review-notes.md` for the full $QA/$Review findings._
