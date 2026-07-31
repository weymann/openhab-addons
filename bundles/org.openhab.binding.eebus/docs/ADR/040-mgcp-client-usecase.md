# ADR-040: MGCP Client-role Use Case (Total Active Power)

## Status

> Accepted

## Context

The real Hager Energy S10's LPC write path has an open, unresolved `COMMAND_REJECTED` issue
(project memory `eebus-lpc-command-rejected-2026-09-02.md`) - every diagnostic points at general
SHIP/SPINE communication being healthy (Binding requests, multiple diagnostic Feature reads all
succeed live against the S10), but there is no _use-case-level_ read confirming this end-to-end
against the same device, since this device's own discovery dump (`hagers10.json`) shows no entity
offering the MPC Server role ("Monitored Unit") this binding's existing MPC Client could pair
with.

`hagers10.json` entity `[6]` (actor `GridConnectionPoint`) does declare
`monitoringOfGridConnectionPoint` (MGCP), `scenarioSupport: [1,2,3,4,5,6,7]`, backed by real
`Measurement`/`ElectricalConnection` server Features - a use case this binding has never
implemented a Client-role reader for. `docs/changes/mgcp-client-usecase/proposal.md` scopes an MVP
to Scenario 2 (Total Active Power, [MGCP-021], Mandatory) only, deferring the other six Scenarios
(one of which, Scenario 1, is DeviceConfiguration-based rather than Measurement-based, and two of
which, Scenarios 5/6, need a genuinely new phase-disambiguation mechanism this binding has never
built - see that proposal's "Out of scope" for the primary-source detail).

Primary source: `EEBus_UC_TS_MonitoringOfGridConnectionPoint_V1.0.0_public.pdf`.

## Decision

### 1. New `EEBusMgcpClientUseCase`, structurally mirrors `EEBusMpcClientUseCase`

Same shape as the existing Client-role use cases: `@AllowedEntityTypes({EntityTypeEnumType.CEM})`,
`getActor()` returns `"MonitoringAppliance"` - the **same literal string** `EEBusMpcClientUseCase`
already returns, since the SPINE use-case-actor catalog names this actor identically across MPC
and MGCP (confirmed via `hagers10.json`: the S10's own entities `[1]`-`[4]` declare exactly this
actor for their `monitoringOfPowerConsumption` support). `getName()` returns
`"monitoringOfGridConnectionPoint"` and the expected peer actor is `"GridConnectionPoint"` - both
taken directly from the real device's own discovery dump, not inferred/guessed (unlike MPC's
`getName()`, which needed a live-device retest to confirm lowerCamelCase, ADR-011). This also
means the actor-name concern flagged for MPC ("CEM" vs the catalog's "Monitored Unit", still
unresolved) does not recur here: `"GridConnectionPoint"` matches the real device's own declared
actor string exactly.

`getScenarioSupport()` returns `List.of(2L)` - Scenario 2 only, matching this MVP's scope.

### 2. Total Active Power resolved via the same `AC_POWER_TOTAL` scope match `power` already uses

MGCP's Scenario 2 `measurementDescriptionData.scopeType` is `"acPowerTotal"` - the exact same
`ScopeTypeEnumType.AC_POWER_TOTAL` constant `EEBusMpcClientUseCase#resolvePowerMeasurementId`
already matches for MPC's own `power` (the SPINE `ScopeTypeEnumType` enum is shared across use
cases, not use-case-specific). `EEBusMgcpClientUseCase#resolveTotalActivePowerMeasurementId` reuses
the identical 3-step fallback chain (scope match -> measurement-type match -> sole-entry fallback)
for the identical reason: Total Active Power is this MVP's one mandatory data point, and a real
peer is not guaranteed to tag its scope correctly.

### 3. `communicationPartnerFeatureRequirements` declares both `Measurement` and `ElectricalConnection`

Table 22 of the MGCP TS marks both `ElectricalConnectionDescriptionListData` and
`ElectricalConnectionParameterDescriptionListData` Mandatory for the peer's Scenario 2 support,
alongside `Measurement`'s `measurementDescriptionListData`/`measurementListData`. `setup()`
declares both feature type's mandatory functions in `communicationPartnerFeatureRequirements`,
spec-faithfully expressing what a compliant peer must offer - even though this MVP's actual value
resolution (Decision 2) never reads `ElectricalConnection` data, since Scenario 2's Total Active
Power is not phase-disambiguated and needs no `ElectricalConnection` join to produce a usable
value. Declaring a requirement this use case does not itself consume mirrors the existing
precedent in `AbstractEEBusLimitEnergyGuardUseCase` (ADR-039's `DeviceConfiguration` bind exists
to satisfy a peer precondition, not to enable a read/write path).

### 4. New sibling method `EEBusOhEntityHandler#applyMgcpMeasurement`, not a generalized shared method

`applyMpcMeasurement` (ADR-037) already implements the exact `ensureChannel`/`updateCachedState`
pattern this MVP's single Channel needs, but hardcodes `EEBusBindingConstants.CHANNEL_GROUP_MPC`.
Rather than generalizing it into a channel-group-parameterized method (which `EEBusOhEntityHandler#applyLimitStatus` already proves works well, shared between LPC/LPP per ADR-015), this ADR adds a
new sibling method with the same body, hardcoded to `CHANNEL_GROUP_MGCP` - a handful of duplicated
lines, but zero risk to `applyMpcMeasurement`'s existing call site and its two just-added unit
tests (docs/ADR/037). Generalizing across MPC and MGCP is deferred: MGCP has exactly one Channel
today, so the duplication cost is minimal, and the shape a shared method should take is clearer
once MGCP's later Scenarios (energy, phase current/voltage) are added and it is known whether they
fit `applyMpcMeasurement`'s single-value signature at all. Revisit if/when MGCP grows a second
Channel.

### 5. New dynamic `mgcp` Channel Group on `eebus:oh-entity`, no static convenience entity yet

One Channel, `total-active-power` (`Number:Power`, read-only), created on first resolved
measurement via the existing `ensureChannel`/`updateCachedState` pattern (ADR-014) - the same
phased approach MPC took (`dynamic-client-role-channels` before `oh-mpc-entity-static-channels`).
A static `oh-mgcp-entity` is out of scope until (if) MGCP's data-point set grows enough to justify
one, mirroring MPC's own precedent.

### 6. `EEBusHandler#deriveLocalUseCases` gains an `"MGCP"` branch

`if (clientUseCaseKeys.contains("MGCP"))` instantiates `EEBusMgcpClientUseCase` exactly like the
existing `"MPC"`/`"LPC"`/`"LPP"` branches; `"MGCP"` is added to the implemented-use-cases filter set
so it no longer logs as "not yet implemented". `"MGCP"` was already a recognized checkbox option in
`EEBusOhEntityConfiguration#supportedUseCasesClient` (added with the full 43-entry catalog,
CONCEPT.md §5.4.1) - this ADR is purely about giving that existing checkbox a working
implementation, not adding new configuration surface.

## Consequences

### Positive

- Gives a second, architecturally independent Client-role use case to test against the real
  Hager S10 - if it detects and reads cleanly, that is strong additional evidence the still-open
  LPC `COMMAND_REJECTED` issue is scoped to that one write path, not a general communication
  problem (docs/changes/mgcp-client-usecase/tasks.md 8.2/8.3).
- Reuses `power`'s exact resolution chain and `applyMpcMeasurement`'s exact
  `ensureChannel`/`updateCachedState` shape - low-risk, well-precedented implementation.
- `"MGCP"` checkbox option (already present, previously a no-op) now does something.

### Negative

- MVP scope only: Scenario 1 (PV curtailment factor, DeviceConfiguration-based), Scenarios 3/4
  (feed-in/consumed energy), and Scenario 7 (frequency) are documented-but-unimplemented, same
  category of gap CONCEPT.md already tracks for other use cases. Scenarios 5/6 (phase-specific
  current/voltage) need genuinely new resolution logic (an `ElectricalConnection`
  `parameterId`/`measurementId` join against `acMeasuredPhases`/`acMeasuredInReferenceTo`) not
  built here at all.
- `applyMgcpMeasurement` duplicates `applyMpcMeasurement`'s body (Decision 4) - a deliberate,
  flagged trade-off, not an oversight.
- No automated end-to-end test for the new resolution/dispatch path - same known gap every prior
  Client-role use case in this binding carries (no `jeebus.spine` `Device`/`NodeManagement`/
  `UseCasePartner` test fixture exists).
- Units unverified against a real device beyond the S10's own declared `"W"` in the TS - same
  "not yet verified" caveat every prior ADR in this binding carries.

## Migration

None needed. Purely additive: no existing Channel, method, or Thing type changes behavior.

---

_Refines docs/ADR/014-dynamic-client-role-channels.md and docs/ADR/037-mpc-additional-datapoints.md, supersedes neither._
