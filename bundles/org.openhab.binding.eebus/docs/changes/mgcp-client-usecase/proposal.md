# Proposal: MGCP Client-role Use Case (Total Active Power)

## Intent

`$Concept`/`$Review` asked, given the real Hager Energy S10's LPC writes are stuck on an open,
unresolved `COMMAND_REJECTED` issue (`docs/changes/mpc-additional-datapoints/` follow-up
discussion, project memory `eebus-lpc-command-rejected-2026-09-02.md`), whether there is anything
else that could be read from the same real device to confirm general SHIP/SPINE communication is
healthy, independent of the stuck LPC write path. Investigation of `hagers10.json` (the S10's own
discovery dump) found that no entity offers the MPC Server role ("Monitored Unit") - so the MPC
Client Channels this binding already has cannot be exercised against this specific device either.

Entity `[6]` of the same dump _does_ declare actor `GridConnectionPoint`, use case
`monitoringOfGridConnectionPoint` (MGCP), `scenarioSupport: [1,2,3,4,5,6,7]`, and hosts real
`Measurement`/`ElectricalConnection` server Features backing it - a genuinely different, entirely
unimplemented use case this binding could add a Client-role reader for, giving an independent,
real, end-to-end read path against the same device.

`MGCP` is already a recognized abbreviation in `EEBusOhEntityConfiguration`'s
`supportedUseCasesClient`/`supportedUseCasesServer` checkbox options (added with the full 43-entry
catalog, CONCEPT.md §5.4.1) but has no `UseCase` implementation behind it yet - checking it today
is a no-op logged as "not yet implemented" by `EEBusHandler#deriveLocalUseCases`.

Primary source: `EEBus_UC_TS_MonitoringOfGridConnectionPoint_V1.0.0_public.pdf` (from the user's
`eebus` reference folder, same folder used to verify LPC/MPC).

## Scope

In scope (MVP - Scenario 2 only, mirrors how MPC Client started with just `power`):

- New `EEBusMgcpClientUseCase` (Client role, actor `MonitoringAppliance` - reusing the exact actor
  string MPC Client already returns, since the SPINE catalog names this actor identically across
  use cases; peer actor expected: `GridConnectionPoint`, use case name
  `monitoringOfGridConnectionPoint` - both taken directly from `hagers10.json`, no naming
  investigation needed this time).
- Scenario 2 ("Monitor momentary power consumption/production", [MGCP-021], Mandatory) only: Total
  Active Power at the Grid Connection Point, resolved via `MeasurementDescriptionListData` whose
  `scopeType` is `acPowerTotal` - the exact same `ScopeTypeEnumType` constant
  `EEBusMpcClientUseCase#resolvePowerMeasurementId` already matches for MPC's own `power` (the
  catalog reuses this scope value across use cases). Same fallback chain (scope match -> type
  match -> sole-entry) as `power`, for the same reason: this is the one mandatory data point.
- New dynamic Channel Group `mgcp` on `eebus:oh-entity` (no static convenience entity yet - same
  phased approach as MPC, which only grew an `oh-mpc-entity` in a later change), one Channel
  `total-active-power` (`Number:Power`), created on first resolved measurement via the existing
  `EEBusOhEntityHandler#ensureChannel`/`updateCachedState` pattern (docs/ADR/014).
- `EEBusHandler#deriveLocalUseCases`: new `if (clientUseCaseKeys.contains("MGCP"))` branch;
  `"MGCP"` removed from the `unimplementedClientUseCases` filter list.
- Sign convention note in the Channel description: MGCP uses the "load convention" - positive =
  consumption from the grid, negative = feed-in to the grid ([MGCP-001]) - the **opposite** sign
  meaning from a household's intuitive "how much am I feeding in", worth stating explicitly since
  it is easy to misread.

Out of scope (explicitly deferred to later changes, mirroring the `mpc-additional-datapoints`
precedent):

- Scenario 1 (PV Feed-In Power Limitation Factor, [MGCP-011]) - structurally different: read via
  `DeviceConfiguration.deviceConfigurationKeyValueListData` (`keyName: "pvCurtailmentLimitFactor"`,
  unit `pct`), not `Measurement` at all. Interesting aside: the S10's own `DeviceConfiguration` key
  3 (`pvCurtailmentLimitFactor=0`, unconfigured) already surfaced in the LPC diagnostic reads -
  same key, different use case.
- Scenarios 3/4 (Total Grid Feed-In/Consumed Energy, [MGCP-031]/[MGCP-041]) - same shape as
  Scenario 2 (single `Measurement` scope match: `gridFeedIn`/`gridConsumption`), just not part of
  this MVP's cut.
- Scenarios 5/6 (phase-specific current/voltage, [MGCP-051]/[MGCP-061]) - **architecturally
  harder than anything this binding has resolved before**: unlike MPC's phase-specific data points
  (which each get their own distinct `ScopeType`, e.g. `acCurrentA`/`acCurrentB`/`acCurrentC`),
  MGCP's phase-specific Measurements all share ONE generic `scopeType` (`acCurrent`/`acVoltage`)
  and must be disambiguated by joining `measurementDescriptionListData` against
  `electricalConnectionParameterDescriptionListData`'s `acMeasuredPhases`/`acMeasuredInReferenceTo`
  fields via `measurementId`/`parameterId`. Real new resolution logic, not a copy-paste of the
  `mpc-additional-datapoints` pattern - deliberately deferred to its own change.
- Scenario 7 (Frequency, [MGCP-071]) - same shape as Scenario 2, just not part of this MVP's cut.
- A static `oh-mgcp-entity` convenience Thing type - out of scope until (if) the full data-point
  set exists, same reasoning as MPC's phased rollout.
- Any change to `jeebus.spine`/`jeebus.ship` (both remain protected, read-only for this
  investigation) or to `pom.xml`.

## Open Questions

- `EEBusOhEntityHandler#applyMpcMeasurement(String channelId, ChannelTypeUID, String, String,
  double, Unit<?>)` (docs/ADR/037-mpc-additional-datapoints.md) already implements exactly the
  `ensureChannel`/`updateCachedState` pattern this MVP's single Channel needs, but hardcodes
  `EEBusBindingConstants.CHANNEL_GROUP_MPC` internally. Two options for `$Architect`:
  1. Generalize it into a channel-group-parameterized method (mirrors
     `EEBusOhEntityHandler#applyLimitStatus`, which already takes `channelGroup` and is shared
     between LPC/LPP, docs/ADR/015) and reuse it for both MPC and MGCP.
  1. Add a new sibling method `applyMgcpMeasurement(...)` with the same body, hardcoded to
     `CHANNEL_GROUP_MGCP` - a few duplicated lines, zero risk to `applyMpcMeasurement`'s existing
     call sites/tests.

  **Resolved by `$Architect` (ADR-040 Decision 4):** option 2 - a new fixed-signature
  `applyMgcpMeasurement(double watts)`. This MVP has exactly one MGCP data point, so a generic
  multi-parameter method would add indirection without a second caller to justify it; option 1
  remains available for a future change that adds more MGCP Scenarios.
- Per Table 22 of the MGCP TS, the peer's Scenario 2 support also requires
  `ElectricalConnectionDescriptionListData`/`ElectricalConnectionParameterDescriptionListData` to
  be Mandatory-present - should `EEBusMgcpClientUseCase#setup()`'s
  `communicationPartnerFeatureRequirements` declare `ElectricalConnection` alongside `Measurement`
  (spec-faithful, matches what a compliant peer must offer) even though this MVP's value
  resolution only ever reads `Measurement` (Scenario 2's total isn't phase-disambiguated, so no
  `ElectricalConnection` join is actually needed to get a usable value)? Proposed: yes, declare
  both - flagged here for `$Architect` to confirm as deliberate rather than overreaching.

  **Resolved by `$Architect` (ADR-040 Decision 3):** confirmed as proposed - both features are
  declared Mandatory, matching Table 22 exactly, even though only `Measurement` is actually read
  by this MVP's resolution logic.
