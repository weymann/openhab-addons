# ADR-042: Configurable `entityType` on the `oh-...-entity` convenience Things

## Status

Accepted

## Context

Following a `$Concept` discussion and a follow-up spec check (see project memory
`eebus-gridguard-allowedentitytypes-2026-09-03.md` and
`eebus-oh-xxx-entity-allowed-entitytypes-2026-09-04.md`), the user asked to make the SPINE
`entityType` of the shared local Entity configurable per convenience Thing, instead of the
hardcoded `EntityTypeEnumType.CEM` `EEBusHandler#startShipSpine()` has always used.

The primary TS PDFs (staged from `C:\Projects\eebus`, extracted with `pdftotext -layout`) were
checked for every actor the four `oh-...-entity` convenience Things imply, correcting an
unverified 2026-09-03 assumption along the way:

- Energy Guard actor (`oh-eg-entity`, LPC+LPP Client): LPC TS §3.2.1.1.1 = LPP TS §3.2.1.1.1 -
  **CEM, GridGuard**.
- Controllable System actor (`oh-cs-entity`, LPC+LPP Server simultaneously): LPC TS §3.2.2.1.1
  permits CEM/Compressor/EVSE/HeatPumpAppliance/Inverter/SmartEnergyAppliance/SubMeterElectricity
  (7 types); LPP TS §3.2.2.1.1 permits CEM/EVSE/Inverter/SmartEnergyAppliance/SubMeterElectricity
  (5 types, no Compressor/HeatPumpAppliance). Since one `oh-cs-entity` binds both simultaneously on
  the same shared Entity, only the **intersection** (CEM, EVSE, Inverter, SmartEnergyAppliance,
  SubMeterElectricity) is actually legal - **this corrects the 2026-09-03 note's unverified "CS is
  CEM-only" assumption**.
- Monitoring Appliance actor (`oh-mpc-entity`, MPC Client role only - confirmed from
  `deriveLocalUseCases()`, this Thing never contributes a server role): MPC TS §3.2.1 states
  plainly "the Use Case specific data follows behind **any** entityType" - no restriction at all.
  Same wording found for MGCP's own "Monitoring Appliance" actor.
- Monitored Unit actor (`oh-entity`'s MPC-Server role, `EEBusMpcServerUseCase` - not reachable via
  `oh-mpc-entity`): MPC TS §3.2.2.1.1 permits 7 concrete appliance types - **CEM is not legal here
  at all**.

Architectural constraint (unchanged from the 2026-09-03 note, still real): `EEBusHandler` builds
exactly one shared local SPINE Entity per Bridge (`startShipSpine()`), and every attached Use Case
from every child Entity Thing (`oh-entity`/`oh-cs-entity`/`oh-eg-entity`/`oh-mpc-entity`) lands on
that same Entity (`deriveLocalUseCases()`). Making `entityType` genuinely safe for every possible
combination of children would require either splitting into one local Entity per distinct role
present, or a full cross-child/cross-Use-Case legality check - both bigger changes than this ADR
covers. The user explicitly asked to defer that check and land the configuration surface first.

## Decision

Add an `entityType` config parameter (shared `EEBusOhEntityConfiguration.entityType`, default
`"CEM"`, matching prior hardcoded behaviour) to all four convenience Thing types, each restricted
in `thing-types.xml` to that Thing's own actor's spec-permitted subset:

- `oh-eg-entity`: CEM, GridGuard.
- `oh-cs-entity`: CEM, EVSE, Inverter, SmartEnergyAppliance, SubMeterElectricity (the LPC/LPP
  intersection above).
- `oh-mpc-entity` / `oh-entity`: the full generated `EntityTypeEnumType` set (47 values, CEM
  first then alphabetical), since neither actor's spec restricts it - marked `advanced` given the
  list's size, unlike the two short lists above.

`EEBusHandler#deriveLocalUseCases()` resolves the actual value to build the shared Entity with in
the same pass it already uses to collect Use-Case keys and CS failsafe seed values: it fetches
every child's `EEBusOhEntityConfiguration` unconditionally (safe - every Thing type allowed as a
child of `eebus:oh-device` declares this same config class), and the **first** child found with a
non-blank, non-`"CEM"` value wins - parsed via the generated `EntityTypeEnumType.fromValue(...)`,
falling back to `CEM` with a WARN log if the configured string does not match a known constant.
The result is returned alongside the existing `List<UseCase>` via a new small
`LocalUseCaseDerivation(List<UseCase> useCases, EntityTypeEnumType entityType)` record (method
name kept unchanged - many ADRs/docs/README already reference `deriveLocalUseCases` by name).
`startShipSpine()`'s `Device.getBuilder()...setType(...)` call uses `localDerivation.entityType()`
instead of the hardcoded `EntityTypeEnumType.CEM`.

**Deliberately not implemented (per explicit user instruction, "skip consistency checking for
now"):** no validation that

- the winning `entityType` is actually spec-permitted for the Use Case combination
  `deriveLocalUseCases()` ends up deriving (e.g. an `oh-mpc-entity` configured to `HeatPumpAppliance`
  sitting on a Bridge that also has an `oh-cs-entity`, whose own dropdown would never have offered
  that value, silently wins if it happens to be the first child in iteration order), or
- two children configuring two different non-default values are actually compatible with each
  other (the first one found silently wins, the rest are silently ignored - no error, no log
  beyond the "unknown value" WARN case).

`getThing().getThings()` iteration order is not itself contractually stable across openHAB
versions, so which child "wins" when more than one configures a conflicting value is not
guaranteed to be deterministic across restarts either - acceptable for now since this is
explicitly a first landing, not the final behaviour.

## Consequences

### Positive

- Closes the gap the 2026-09-03 investigation flagged: widening an `@AllowedEntityTypes`
  annotation alone was a no-op while `entityType` was hardcoded; it is no longer hardcoded.
- Corrects a previously-unverified assumption (Controllable System is _not_ CEM-only) before it
  could mislead a future change.
- Each Thing's dropdown only ever offers values legal for its own single actor in isolation, so a
  single-role Bridge (the common case, e.g. a pure `oh-eg-entity` EnergyGuard bridge modelled after
  cbsim/evcc) gets a fully spec-correct choice with zero risk of an inconsistent combination.
- No `jeebus.ship`/`jeebus.spine` change.

### Negative / follow-up

- **Not yet compiled or live-tested** - no local Maven/JDK in this environment; verified only by
  manual review (brace/paren count, targeted anchor-based edits applied and re-read back
  unchanged). Needs `mvn clean install` + a real Bridge/Thing restart before relying on this.
- No cross-child consistency check, by explicit user request - a mixed-role Bridge (e.g.
  `oh-eg-entity` configured to GridGuard alongside an `oh-cs-entity`, which is CEM-only among its
  own legal options but would still see the Bridge's Entity presented as GridGuard if the EG child
  is iterated first) can silently end up with a spec-illegal or simply confusing `entityType` for
  the roles that did not choose it. Revisit if this is ever actually hit in practice - the
  2026-09-03 note already sketched the two bigger options (a startup guard that refuses such a
  combination, or splitting into one local Entity per role).
- `oh-mpc-entity`/`oh-entity`'s 47-option dropdown is exhaustive rather than curated - some listed
  values (e.g. `DeviceInformation`, a SPINE-protocol-reserved type) are unlikely to make sense in
  practice even though the spec text technically imposes no restriction; not filtered out, to stay
  literally faithful to "any entityType" rather than second-guessing which subset a user might want.

## Update 2026-09-04: `@AllowedEntityTypes` on the EnergyGuard use cases never matched this ADR's own claim - fixed

This ADR's own Context section already established, from the LPC/LPP TS §3.2.1.1.1 text, that
both `CEM` and `GridGuard` are spec-legal entity types for the EnergyGuard actor, and the Decision
section widened `oh-eg-entity`'s `entityType` dropdown accordingly. What this ADR did not do -
and should have - was also widen the actual runtime-enforcing
`@AllowedEntityTypes({ EntityTypeEnumType.CEM })` annotation on `AbstractEEBusLimitEnergyGuardUseCase`
(base of both `EEBusLpcClientUseCase` and `EEBusLppClientUseCase`) to match. This meant choosing
`GridGuard` in the dropdown - a value the binding's own UI presented as legal - reliably threw
`IllegalArgumentException: Use case EEBusLpcClientUseCase does not allow entity type GRID_GUARD!`
from `jeebus.spine`'s `EntityImpl.addFeaturesForUseCase` at connect time. This was live-confirmed
2026-09-04 on a Bridge (`energy-guard-sim`) configured with `entityType=GridGuard` on its
`oh-eg-entity` child, immediately after the ADR-043 topology gate let that Bridge attempt its
first-ever real connection (see docs/ADR/043-topology-phase-gate.md and project memory).

Before fixing, checked whether `GridGuard` was actually intended here or just a stray/incorrect
config value: the real Hager S10's own discovery capture (`hagers10.json`) uses `CEM` for every
entity hosting an `EnergyGuard` actor, never `GridGuard` - so real-world precedent alone would not
have justified this. However, the user confirmed a second discovery capture,
`GridGuard.json` (`d:_n:OPHAB-Energy Guard-0001`, this binding's own simulated EnergyGuard
device), is a deliberate GridGuard-actor scenario, not a mistake - `GridGuard` should genuinely be
usable here.

Also checked (before fixing) whether `deviceType` needs to change alongside `entityType` to
"support" GridGuard - it does not. `deviceType` (`DeviceTypeEnumType`: physical/system device
category - `Generic`, `EnergyManagementSystem`, `ChargingStation`, `Dishwasher`, ...) and
`entityType` (`EntityTypeEnumType`: the SPINE actor/role an Entity plays - `CEM`, `GridGuard`,
`EVSE`, ...) are independent SPINE classification axes on two different objects (`Device` vs.
`Entity`); `jeebus.spine`'s own `EntityImpl.addFeaturesForUseCase` reads only the Entity's
`getType()`, never anything from the `Device`. There is no `GridGuard`-specific value in
`DeviceTypeEnumType` at all (it enumerates physical appliance categories plus a few generic
system types) - `EnergyManagementSystem`, already hardcoded in
`EEBusHandler#startShipSpineLocked()` and already what `GridGuard.json`'s own capture shows, needs
no change.

**Fix:** widened `AbstractEEBusLimitEnergyGuardUseCase`'s annotation to
`@AllowedEntityTypes({ EntityTypeEnumType.CEM, EntityTypeEnumType.GRID_GUARD })`, matching what
this ADR's Decision section already declared legal. Confirmed safe: this class's
`getFeatureRequirements(EntityTypeEnumType entityType)` does not branch on the `entityType`
parameter at all (returns the same fixed requirement set regardless), so `GridGuard` gets
identical treatment to `CEM` here - no further type-specific logic needed. Entirely confined to
`org.openhab.binding.eebus` (`AbstractEEBusLimitEnergyGuardUseCase.java`); no `jeebus.spine`
change (the `@AllowedEntityTypes` annotation _type_ lives there, but it is only ever _applied_ in
our own project).

This ADR's existing "Negative/follow-up" caveat about no cross-child consistency check still
applies unchanged - a Bridge mixing a `GridGuard`-configured `oh-eg-entity` with another child
whose own use case does not tolerate `GridGuard` (e.g. `oh-cs-entity`, whose legal set per this
ADR is CEM/EVSE/Inverter/SmartEnergyAppliance/SubMeterElectricity - no GridGuard) would still fail
the same way, silently as far as config validation goes, surfacing only as this same exception at
connect time. Not addressed here, unchanged scope from the original ADR.

## Update 2026-09-04 (continued): the remaining four `@AllowedEntityTypes` annotations never matched this ADR's own tables either - fixed

Live-confirmed the same day, immediately after the EnergyGuard fix above: enabling the
`energy-guard-sim` Bridge's `settleTopology()` action (ADR-043) a second time - after the
EnergyGuard-use-case fix above had already resolved the first crash - hit an almost identical
exception, just from a different class:

```text
java.lang.IllegalArgumentException: Use case EEBusMpcClientUseCase does not allow entity type GRID_GUARD!
    at org.openmuc.jeebus.spine.impl.EntityImpl.addFeaturesForUseCase(EntityImpl.java:300)
```

This ADR's own Context section (and the more detailed
`eebus-oh-xxx-entity-allowed-entitytypes-2026-09-04.md` project-memory note it draws from) had
already established, from the primary TS PDFs, that every one of the five `@AllowedEntityTypes`-
annotated use-case classes needed a value other than the original hardcoded `CEM`-only - the first
update above only actually fixed one of the five
(`AbstractEEBusLimitEnergyGuardUseCase`). The remaining four were audited and fixed in this pass:

- **`EEBusMpcClientUseCase`** (Monitoring Appliance actor, MPC Client role, reachable via
  `oh-mpc-entity`): MPC TS §3.2.1 states plainly "the Use Case specific data follows behind any
  entityType" - no restriction at all. Read `jeebus.spine`'s own
  `EntityImpl#addFeaturesForUseCase` (read-only, no `jeebus.spine` change) to confirm what a
  _missing_ annotation actually does: `allowed == null` skips the check entirely (DEBUG log only),
  which is exactly "no restriction" - so the correct fix is to remove the annotation, not to
  enumerate an allow-list. `@AllowedEntityTypes({ EntityTypeEnumType.CEM })` and its now-unused
  import removed; a comment left in its place explaining why no annotation is present, citing this
  ADR and the `jeebus.spine` source finding.
- **`EEBusMgcpClientUseCase`** (Monitoring Appliance actor, MGCP's own client role, reachable via
  the generic `oh-entity`): this ADR's Context section already noted "same wording found for
  MGCP's own 'Monitoring Appliance' actor" - i.e. this class's fix was already fully diagnosed,
  just not yet applied. Fixed identically to `EEBusMpcClientUseCase` (annotation + import removed,
  same explanatory comment).
- **`AbstractEEBusLimitControllableSystemUseCase`** (Controllable System actor, base of
  `EEBusLpcServerUseCase`/`EEBusLppServerUseCase`, used by `oh-cs-entity` and the CS-role half of
  `oh-entity`): this ADR's Decision section already declared `oh-cs-entity`'s config dropdown as
  CEM/EVSE/Inverter/SmartEnergyAppliance/SubMeterElectricity (the LPC∩LPP intersection), but the
  runtime-enforcing annotation was still `{ CEM }` only - meaning choosing any of the other four
  legal dropdown values would have crashed identically at connect time, just not yet live-hit by
  any test so far. Widened to
  `{ CEM, EVSE, INVERTER, SMART_ENERGY_APPLIANCE, SUB_METER_ELECTRICITY }` to match the dropdown
  the Decision section already promised.
- **`EEBusMpcServerUseCase`** (Monitored Unit actor, reachable only via plain `oh-entity`, not
  `oh-mpc-entity`): this one was backwards, not just incomplete - MPC TS §3.2.2.1.1 ("List of
  permitted entityTypes for Actor Monitored Unit") lists exactly **Compressor,
  ElectricalImmersionHeater, EVSE, HeatPumpAppliance, Inverter, SmartEnergyAppliance,
  SubMeterElectricity** (verified 2026-09-04 directly against the primary PDF,
  `EEBus_UC_TS_MonitoringOfPowerConsumption_V1.0.0_public.pdf` §3.2.2.1.1) - **`CEM` is not legal
  for this actor at all**. The old `{ CEM }` annotation therefore allowed the one value the spec
  forbids and disallowed all seven values the spec actually permits. Not yet live-hit as a crash
  (no test has exercised this Server role's entity type yet), but fixed proactively while already
  in this file family for the other four, using the newly-confirmed exact 7-value list.

Confirmed safe for all four, the same way as the first `AbstractEEBusLimitEnergyGuardUseCase` fix:
none of their `getFeatureRequirements(EntityTypeEnumType entityType)` implementations branch on
the `entityType` parameter - each returns the same fixed requirement set regardless, so widening
(or removing) the allow-list needs no companion type-specific logic. Self-QA: brace/paren counts
balanced in all four edited files (`EEBusMpcClientUseCase.java`,
`AbstractEEBusLimitControllableSystemUseCase.java`, `EEBusMgcpClientUseCase.java`,
`EEBusMpcServerUseCase.java`). Entirely confined to `org.openhab.binding.eebus`; no
`jeebus.spine`/`jeebus.ship` change. **Not yet compiled or live-tested** - needs `mvn clean
install` + a retest of `energy-guard-sim`'s `settleTopology()` action before relying on this (the
immediate next step: confirm the Bridge now reaches a connected state with no further
`@AllowedEntityTypes`-related crash for whichever use cases that Bridge's children actually
contribute).

All five `@AllowedEntityTypes` annotations in the codebase are now believed consistent with this
ADR's own Context/Decision tables. The existing "no cross-child consistency check" caveat (both in
this ADR and in the first 2026-09-04 update above) still applies unchanged - a Bridge mixing
children whose actors have genuinely incompatible legal sets (e.g. a `GridGuard`-configured
`oh-eg-entity` alongside an `oh-cs-entity`, whose own legal set has no `GridGuard`) will still fail
the same way, correctly, since `GridGuard` really is illegal for the Controllable System actor -
that is expected spec-driven behavior, not a bug like the four fixed here.
