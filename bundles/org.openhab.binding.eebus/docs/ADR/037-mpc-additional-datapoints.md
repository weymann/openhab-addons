# ADR-037: Additional MPC Client-role Data Points (Scenarios 2-5)

## Status

> Accepted

## Context

MPC Client has, since ADR-014, only ever resolved and exposed Scenario 1's `power` (Total Active Power, [MPC-011]). CONCEPT.md 5.4.3 documents four further Scenarios that were never implemented: phase-specific power (Scenario 1, [MPC-012]), consumed/produced energy (Scenario 2, [MPC-021/022]), phase-specific current (Scenario 3, [MPC-031]), voltage (Scenario 4, [MPC-041] - both phase-to-neutral and phase-to-phase), and grid frequency (Scenario 5, [MPC-051]).

Reading `jeebus.spine`'s `ScopeTypeEnumType` (read-only - `jeebus.spine` remains protected per project policy, no change proposed or made here) found `AC_POWER_A`/`_B`/`_C`, `AC_ENERGY_CONSUMED`, `AC_ENERGY_PRODUCED`, `AC_CURRENT_A`/`_B`/`_C`, `AC_VOLTAGE_A`/`_B`/`_C`, and `AC_FREQUENCY_GRID` - twelve distinct scope values, each resolvable the same way `power`'s own `AC_POWER_TOTAL` already is. No phase-to-phase voltage scope (an `acVoltageAB`-shaped value) exists in this enum at all - the three phase-to-phase data points ([MPC-041/4,5,6]) cannot be told apart from the phase-to-neutral ones using the mechanism the other twelve use.

The user, informed of this gap, explicitly chose (via `AskUserQuestion`): implement the twelve resolvable data points; declare Channels for the three phase-to-phase voltages anyway, as permanent stubs with no resolution logic behind them, documented as such - rather than silently omitting them or guessing at an unverified resolution mechanism.

## Decision

### 1. Twelve new data points, resolved by exact ScopeType match, no fallback

Unlike `power`'s existing 3-step fallback chain (`resolvePowerMeasurementId`: scope match, then measurement-type match, then sole-entry fallback - needed because `power` is the one _mandatory_ data point and a real peer is not guaranteed to tag its scope), the twelve new data points are matched by exact `ScopeType` only. A measurement-type-only or sole-entry fallback would be ambiguous for them: a real peer's `MeasurementDescriptionListData` can plausibly contain several entries of the same generic `MeasurementTypeEnumType` (e.g. `POWER` for the total plus three more for phases A/B/C), which is precisely the situation `power`'s own fallback chain was never asked to disambiguate (it only ever needed to find _the_ `POWER`-typed entry, singular).

### 2. `EEBusMpcClientUseCase` resolves and dispatches multiple measurement IDs per subscription

`subscribe()` now performs one `MeasurementDescriptionListData` read that resolves both the mandatory `power` id (unchanged `resolvePowerMeasurementId`) and a `Map` of any additional ids found among the twelve, then issues one initial value read and one subscription covering all of them - not one description read and one subscription per data point. `applyMeasurement` iterates every entry in a `MeasurementListData` result/notification and dispatches each one whose id was resolved, instead of filtering for a single id and returning after the first match. A peer offering only `power` sees no behavior change: the additional-ids map is simply empty.

### 3. New generic Channel-apply method on `EEBusOhEntityHandler`, `applyMpcPower` unchanged

`applyMpcPower(double watts)` keeps its exact existing signature, behavior, and log wording - it is the mandatory, most-tested data point, and `EEBusOhEntityHandlerTest`'s existing tests call it directly. A new `applyMpcMeasurement(String channelId, ChannelTypeUID channelTypeUid, String acceptedItemType, String label, double value, Unit<?> unit)` generalizes the same `ensureChannel`/`updateCachedState` pattern for the twelve new data points, parameterized instead of hardcoded to `power`/`Number:Power`/`Units.WATT`. `applyMpcPower` delegates to it internally, so the underlying Channel-creation/state-update logic exists in exactly one place.

### 4. Fifteen new `channel-type` declarations, `mpc` `channel-group-type` grows to fifteen Channels

`thing-types.xml` gains `power-phase-a`/`-b`/`-c` (`Number:Power`), `energy-consumed`/`energy-produced` (`Number:Energy`), `current-phase-a`/`-b`/`-c` (`Number:ElectricCurrent`), `voltage-phase-a`/`-b`/`-c` (`Number:ElectricPotential`), `frequency` (`Number:Frequency`), and the three stub `voltage-a-b`/`-b-c`/`-c-a` (`Number:ElectricPotential`, `description` explicitly noting they are never populated). The `mpc` `channel-group-type`'s `<channels>` list grows from one entry (`power`) to all fifteen.

### 5. Stub Channels are static-declaration-only, never dynamically created

The three phase-to-phase voltage Channels exist on `eebus:oh-mpc-entity` from Thing creation (same static-declaration mechanism ADR-036 already established for the whole `mpc` group) and stay `NULL` indefinitely - no code path ever attempts to resolve or populate them. On a plain `eebus:oh-entity` Thing, where every `mpc` Channel is created dynamically only on a resolved measurement (ADR-014), these three simply never appear at all, since no resolution is ever attempted for them.

## Consequences

### Positive

- Closes CONCEPT.md 5.4.3's implementation gap for twelve of fifteen previously-undocumented-as-implemented MPC data points, without guessing at or fabricating a resolution mechanism for the three that genuinely cannot be resolved with this environment's `jeebus.spine` version.
- `power`'s existing, already-tested behavior and public method signature are fully preserved - this change is additive to `EEBusMpcClientUseCase`/`EEBusOhEntityHandler`, not a rewrite of the working mandatory path.
- One combined description read/value read/subscription per peer, same as before - no additional SPINE round trips from resolving more data points at once.
- The three stub Channels' `description` text explains their permanent `NULL` state directly in the Main UI, rather than leaving a user to wonder why they never populate.

### Negative

- **Units unverified against a real device.** `Units.WATT_HOUR`/`AMPERE`/`VOLT`/`HERTZ` are the openHAB-core-standard choices for these physical quantities, but whether a real MPC Server peer's raw `ScaledNumberType` values are actually expressed in exactly these base units (as opposed to, say, a different energy unit) has not been confirmed against a real device or the TS PDF's raw field encoding - flagged here for the user's live retest (tasks.md 8.3), same "not yet verified against an actual openHAB core build/real device" caveat every prior ADR in this binding has carried.
- **Three permanent stub Channels** are a real, if narrow, UX wart: `eebus:oh-mpc-entity` will always show three Channels that can never receive a value in this environment, unlike every other Channel in the binding. Judged acceptable by explicit user decision over the alternatives (silently omitting them, or guessing at an untested resolution mechanism).
- `EEBusMpcClientUseCase#applyMeasurement` grows from "filter for one id, apply, return" to "iterate all entries, dispatch each resolved one" - marginally more complex control flow, though still a single, non-recursive loop.
- No automated end-to-end test exists for the new multi-data-point resolution/dispatch path (same known gap `docs/changes/dynamic-client-role-channels/tasks.md` 6.3 already carries for the single-data-point case) - covered instead by direct unit tests of the new `EEBusOhEntityHandler#applyMpcMeasurement` method, mirroring the existing `applyMpcPower` test pair.

## Migration

None needed. Purely additive: `power`'s existing behavior, Channel, and method signature are unchanged; the twelve new Channels and three stub Channels are new, not replacements.

---

_Refines docs/ADR/014-dynamic-client-role-channels.md and docs/ADR/036-oh-mpc-entity-static-channels.md, supersedes neither._
