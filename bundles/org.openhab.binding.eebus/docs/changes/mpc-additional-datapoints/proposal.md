# Proposal: Additional MPC Client-role data points (Scenarios 2-5)

## Intent

MPC Client (`EEBusMpcClientUseCase`, `eebus:oh-entity`'s dynamic `mpc` group, `eebus:oh-mpc-entity`'s static one) has so far only ever resolved and exposed Scenario 1's `power` (Total Active Power, [MPC-011]). CONCEPT.md 5.4.3 documents four further MPC Scenarios/data points that were never implemented: phase-specific power (Scenario 1, [MPC-012]), consumed/produced energy (Scenario 2, [MPC-021/022]), phase-specific current (Scenario 3, [MPC-031]), phase-to-neutral and phase-to-phase voltage (Scenario 4, [MPC-041]), and grid frequency (Scenario 5, [MPC-051]).

Investigating `jeebus.spine`'s `ScopeTypeEnumType` (read-only - `jeebus.spine` remains protected, no change proposed or made here) found that 12 of these 15 remaining data points have their own distinct `ScopeType` value and can be resolved the same way `power` already is (by scope, not by guessing a fixed measurement ID). The three phase-to-phase voltage data points (A-B/B-C/C-A, [MPC-041/4,5,6]) do **not** have a distinct `ScopeType` in this `jeebus.spine` version - there is no way to reliably tell them apart from the phase-to-neutral voltages using the mechanism the other 12 data points use.

The user explicitly decided (via `AskUserQuestion`): implement the 12 resolvable data points fully; declare Channels for the 3 phase-to-phase voltages anyway, as stubs that are never populated (no resolution attempted, no fabricated/guessed mapping) - documented as such rather than silently omitted or silently wrong.

## Scope

In scope:

- 12 new MPC data points, each resolved by a distinct `ScopeType` match against `MeasurementDescriptionListData` (no measurement-type-only fallback, unlike Scenario 1's `power` - see "Open Questions" for why): `power-phase-a`/`-b`/`-c` (`AC_POWER_A`/`_B`/`_C`), `energy-consumed` (`AC_ENERGY_CONSUMED`), `energy-produced` (`AC_ENERGY_PRODUCED`), `current-phase-a`/`-b`/`-c` (`AC_CURRENT_A`/`_B`/`_C`), `voltage-phase-a`/`-b`/`-c` (`AC_VOLTAGE_A`/`_B`/`_C`), `frequency` (`AC_FREQUENCY_GRID`).
- 3 stub Channels with no resolution logic behind them: `voltage-a-b`, `voltage-b-c`, `voltage-c-a` - declared in `thing-types.xml` (both `eebus:oh-mpc-entity`'s static group and available to `eebus:oh-entity`'s dynamic one, though the latter can in practice never create them, since dynamic creation only ever happens on a resolved measurement), documented as permanently `NULL` pending a `jeebus.spine`/SPINE mechanism that does not exist yet in this environment.
- `EEBusMpcClientUseCase#subscribe()`/`applyMeasurement()` restructured to resolve and dispatch multiple measurement IDs per peer instead of exactly one - a single `MeasurementListData` notification can (and, once these data points exist, typically will) carry several `MeasurementData` entries at once.
- `EEBusOhEntityHandler` gains a generic channel-apply method for the 12 new data points (`applyMpcPower` stays as-is, unchanged signature, for Scenario 1 and existing test compatibility).
- 15 new `channel-type` declarations in `thing-types.xml`, and the existing `mpc` `channel-group-type`'s `<channels>` list extended to include all of them.
- Documentation: this proposal, a new ADR (next available number), README.md (Channels table), CONCEPT.md (5.4.3 "implementiert" markers, new decided-subsection, 7 checklist entry).

Out of scope:

- The 3 phase-to-phase voltage data points' actual resolution - no `ElectricalConnection`-based (or other) mechanism is implemented, investigated live, or guessed at. If `jeebus.spine` gains a way to resolve them later, or a real device is found to expose them some other way, that is a separate follow-up change.
- Any change to `jeebus.ship`/`jeebus.spine` - both remain protected, read-only for this investigation.
- Any change to the MPC **Server** role (`EEBusMpcServerUseCase`, Item-metadata-based) - this proposal is Client-role/Channel-only, matching the receiving-side scope the user asked about.
- Any change to `eebus:oh-mpc-entity`'s Thing-type declaration beyond its `<channel-groups>` reference picking up the now-larger `mpc` group automatically - no new config fields, no new Thing type.

## Open Questions

- Scenario 1's existing `power` resolution (`resolvePowerMeasurementId`) uses a 3-step fallback chain (scope match -> measurement-type match -> sole-entry fallback), needed because a real peer is not guaranteed to tag `power`'s scope correctly and `power` is the one **mandatory** data point. The 12 new data points are all optional/recommended (CONCEPT.md 5.4.3's O/O*/R markers) and several share the same generic `MeasurementTypeEnumType` with each other and with `power` itself (e.g. three separate `POWER`-typed entries for phases A/B/C, plus the total) - a measurement-type-only fallback would be ambiguous for them in a way it never was for the single mandatory `power` case. Proposed: scope-match only for these 12, no type-only fallback, no sole-entry fallback - flagged here for `$Architect` to confirm as a deliberate, documented asymmetry rather than an oversight.
  **Resolved by `$Architect`:** confirmed as deliberate - see `docs/ADR/037-mpc-additional-datapoints.md` Decision 1. Implemented as proposed in `EEBusMpcClientUseCase#resolveAdditionalMeasurementIds`.
