# ADR-033: `limit-duration` Channel and write-Item for LPC/LPP

## Status

Accepted

## Context

User question (2026-08-27, `$Concept`): the LPC/LPP use case has three Scenario 1 entries -
`limitActive`, `limitValue`, `limitDuration` - but only the first two were covered by a Channel.
`docs/changes/lpc-lpp-client-role-channels/tasks.md` (2026-08-12) already documents why: at the
time, `TimePeriodType`/`getTimePeriod()` had zero prior usage anywhere in this codebase (unlike
`getIsLimitActive()`/`getValue()`, already proven), so the pass deliberately narrowed to exactly
two Channels rather than introduce an unverified SPINE symbol.

Two things changed since:

1. **Format is now verified against the spec, not guessed.** LimitationOfPowerConsumption TS
   V1.0.0 §3.1.8.2 ("Rules regarding the usage of time-related information"):

   > "Durations used within this Use Case SHALL be presented as relative times. The same holds
   > for the 'endTime' Element used for the duration of validity ([LPC-004]) of the Active Power
   > Consumption Limit ([LPC-011])."

   `timePeriod.endTime` is a relative `xs:duration` (e.g. `PT30M`), never an absolute timestamp -
   confirmed independently by `TimePeriodType`'s generated JAXB code (`jeebus.spine`), whose
   `getEndTime()` returns a plain `String` (the union type `AbsoluteOrRelativeTimeType` falls back
   to `String` under JAXB; the LPC spec pins which of the two forms this specific field uses). The
   same field table ([LPC-011], TS p.44) gives the absence rule: "SHALL be set if the limit has a
   duration of validity (greater than zero seconds) ... SHALL be absent or removed otherwise."

1. **ADR-031 removed the EnergyGuard/Client-role read path entirely** (`AbstractEEBus
   LimitEnergyGuardUseCase#subscribeLimitStatus`/its own `applyLimitStatus` overload) - the
   EnergyGuard side is now write-only via tagged Items. This means `limit-duration` as a _Channel_
   can only ever exist on the Controllable-System/Server side (`eebus:oh-cs-entity`) - the
   `AbstractEEBusLimitEnergyGuardUseCase`/`oh-eg-entity` half discussed in the original tasks.md
   no longer applies. This ADR supersedes that scope note for `limit-duration` (the `nominal-max`/
   `contractual-nominal-max` deferral in the same file is untouched and still stands).

Separately, while implementing the CS-side Channel it became clear the EnergyGuard write path
(`AbstractEEBusLimitEnergyGuardUseCase#sendLimitWrite`) never sent `timePeriod` at all - only
`isLimitActive`/`value`. Without a way to _write_ a duration, the new CS-side Channel could never
show anything but "unbounded" for a peer using this binding's own EG write path (a foreign,
non-this-binding EnergyGuard writing `timePeriod` directly over SPINE would still be shown
correctly, but that is not this project's primary use case). User confirmed (2026-08-27,
`AskUserQuestion`): implement both, using a third tagged-Item data point named `limitDuration` -
matching the existing `limitActive`/`limitValue` naming exactly, per
`EEBusMetadataService#findByTag`'s existing uppercase-use-case-code tag format
(`<ThingUID>:LPC:limitDuration`).

## Decision

1. **New Channel `limit-duration`** (`Number:Time`, read-only), added to the shared `lpc`/`lpp`
   `channel-group-type` in `thing-types.xml` (alongside `limit-active`/`limit-value`) - since
   ADR-031, only ever instantiated by `eebus:oh-cs-entity`'s static `<channel-groups>`
   declaration. Shows the SPINE `timePeriod.endTime` value **directly, with no `now`-relative
   derivation** - it already is the remaining duration, per §3.1.8.2 above. `UNDEF` (no state)
   when `timePeriod` is absent/removed, distinct from an explicit "0 seconds" - both are valid,
   distinguishable states per [LPC-004].

1. **`EEBusOhEntityHandler#applyLimitStatus`** gains a fourth parameter,
   `@Nullable Long durationSeconds` - the only production caller
   (`AbstractEEBusLimitControllableSystemUseCase`, ADR-031) is updated accordingly; so is the
   handler's existing unit test (which now also asserts the `limit-duration` Channel, including
   an explicit `UNDEF` case for "no duration written").

1. **`AbstractEEBusLimitControllableSystemUseCase`** (CS/Server role):
   - `onLimitWritten` now also parses `data.getTimePeriod()` via a new `parseLimitDuration`
     helper (`DatatypeFactory.newDefaultInstance().newDuration(String)` + the class's existing
     `durationToSeconds` helper, already proven for the unrelated failsafe-duration-minimum
     field), storing the result in a new `lastWrittenLimitDurationSeconds` field - mirroring
     `lastWrittenLimitValue` exactly, including being overwritten (not merged) on every write, so
     an omitted `timePeriod` on a later write correctly clears a previously-active duration.
   - `publishLimitState` gains a `durationSeconds` parameter, both existing call sites
     (`onStateChanged`, `onLimitWritten`'s ADR-032 value-only-write branch) now pass it through,
     and the method both (a) republishes it into the CS's own outgoing SPINE
     `LoadControlLimitListData` entry via `.withTimePeriod(...)` (spec-compliant `SHALL be set/
     SHALL be absent` per [LPC-011]) and (b) mirrors it onto the paired EnergyGuard's Channel via
     the extended `applyLimitStatus` call (ADR-021's mirror - unaffected by ADR-031, a distinct,
     in-process mechanism, not the removed SPINE-subscribe one).

1. **`AbstractEEBusLimitEnergyGuardUseCase`** (EnergyGuard/Client role, write path only):
   - New `DATA_POINT_LIMIT_DURATION = "limitDuration"` tag constant.
   - `registerWriteListeners` looks it up via `findByTag` (same as the other two) and registers a
     third listener; all three listeners now read all three current Item states and call the
     extended `sendLimitWrite`. No Item tagged for `limitDuration` (the default) means every write
     omits `timePeriod` - unchanged behavior from before this ADR.
   - `sendLimitWrite` gains a `durationSeconds` parameter and a new `secondsToDuration` helper
     (duplicated from, not shared with, the CS-side class's identical private helper - this
     codebase's existing per-class-self-contained convention, not changed here).

## Consequences

### Positive

- Closes the original gap: `limitActive`/`limitValue`/`limitDuration` are now uniformly covered,
  CS-side read and EnergyGuard-side write, resolving both the stale tasks.md scope note and the
  previously-undiscovered write-path gap in the same pass.
- The "relative duration, no derivation" design decision CONCEPT.md had already pencilled in
  (§5.4.3 field table, `limit-duration` row) is now spec-verified rather than "noch nicht
  verifiziert" - and the row's own draft wording ("Restdauer = `endTime - now`") is corrected: no
  subtraction is needed or correct, `endTime` already is the remaining duration.
- `UNDEF`-vs-"0 seconds" is preserved as a real distinction end-to-end (Channel, write path,
  outgoing SPINE publish), matching [LPC-004]'s own three-way state (no `timePeriod` / `0 s`,
  about to deactivate / positive duration).
- Entirely inside `org.openhab.binding.eebus` - no `jeebus.ship`/`jeebus.spine` change was needed;
  `TimePeriodType`/`getTimePeriod()`/`DatatypeFactory` were all already compiled-and-used
  elsewhere in this codebase (the failsafe-duration-minimum path), so this stays within the same
  "already-proven API surface" bar the original 2026-08-12 pass set for itself.

### Negative

- `secondsToDuration` now exists twice (CS-side and EG-side classes) - consistent with this
  codebase's existing convention of small private per-class helpers over a shared utility class,
  but worth a second look if a third such class appears.
- Source-level only, **not yet compiled or live-tested** - no local Maven/openHAB instance
  available in this environment (same limitation noted in every prior ADR in this project).
  Self-QA done: brace/paren balance verified on every touched `.java` file, `thing-types.xml`
  re-parsed with `xml.dom.minidom` (well-formed), and a byte-level CRLF-preservation check (0
  stray LF-only lines) on every touched file.

## Not yet done / user-owned

- `mvn clean install` (or the project's usual `mvn spotless:apply` -> `clean install`) - report
  back any compile errors so they can be fixed iteratively.
- Live retest: (a) an EnergyGuard write with `limitDuration` tagged shows up as
  `oh-cs-entity`'s `lpc#limit-duration`/`lpp#limit-duration` Channel value, decrementing-by-write
  (not live-decrementing - this pass does not add a countdown timer, see "Not implemented" below)
  as expected; (b) a write with `limitDuration` left untagged (or its Item at `UNDEF`) still omits
  `timePeriod` and the Channel reads `UNDEF`, not `0 s`; (c) existing `limitActive`/`limitValue`
  write/read behavior is unaffected.

## Not implemented (explicitly out of scope for this pass)

- **No live countdown.** The Channel/outgoing SPINE value reflects the _last written_ duration,
  not a continuously-decrementing remaining time, even though §3.1.8.1 of the LPC spec states the
  duration "will start decreasing immediately after the receival of the write command". Matching
  that would need a scheduled re-publish (and a decision on interval/precision) - a materially
  bigger feature than "add the missing field", not attempted here without an explicit ask.
- `nominal-max`/`contractual-nominal-max` (Scenario 4, `ElectricalConnectionCharacteristicListData`)
  remain deferred, as per the original tasks.md - untouched by this ADR.
