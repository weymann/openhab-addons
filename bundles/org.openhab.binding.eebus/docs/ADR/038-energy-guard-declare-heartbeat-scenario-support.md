# ADR-038: EnergyGuard actor declares Scenario 3 (Heartbeat) support

## Status

Accepted

## Context

Real Hager Energy S10 test, 2026-09-02/09-03: every LPC `LoadControl` limit write from this
binding's EnergyGuard side was rejected by the S10 with SPINE Error 7 (COMMAND_REJECTED),
identically across two test days, multiple retries, and connection ages ranging from a few
minutes to 15+ hours. ADR-034 (NodeManagement Binding) had already fixed the prior
BINDING_NECESSARY (Error 9) blocker on the same write path; this was the next blocker on top of
that fix.

Diagnostics 1-5 (recorded in project memory, not duplicated here) ruled out `isLimitChangeable`,
`LoadControlLimitConstraints`, `DeviceConfigurationKeyValue` lock flags, a bad post-rejection
state, and `ElectricalConnection` permitted-value-set gating. Entity type (CEM vs. GridGuard) was
also ruled out - no spec mechanism ties a Controllable System's write acceptance to the writing
EnergyGuard's own declared local entity type.

Root cause was found by reading the actual raw SPINE traffic (`ShipConnectionImpl` logs every
sent/received message in full at DEBUG/TRACE) across the S10 connection's entire 15+-hour
lifetime and correlating it against the LPC/LPP Technical Specification:

- Table 5 (§2.6.3, Scenario 3 - Heartbeat) marks "Heartbeat of Energy Guard" as **Mandatory**
  whenever the use case is supported at all: "The message SHALL be sent at least every 60
  seconds ([LPC-005])."
- Table 12 (§3.2.1.2.1, Server data - Resources for Actor Energy Guard) lists the _only_ possible
  operation on `deviceDiagnosisHeartbeatData` as `read` - delivery is peer-polled, not
  push/notified. This matches `jeebus.spine`'s own `FeatureImpl.notifySubscribers()`, which is a
  pure push-to-subscribers mechanism that silently no-ops if nobody has subscribed - consistent
  with the observed traffic, where nobody ever did.
- The S10's own `nodeManagementDetailedDiscoveryData` reply confirms it hosts a dedicated
  `"Heartbeat device diagnosis client feature"` (Generic/CLIENT at its own entity 6, feature
  1001) - built specifically to poll a peer's `deviceDiagnosisHeartbeatData`.
- The S10 never once read this binding's `deviceDiagnosisHeartbeatData` (entity 1, feature 1 on
  the local `d:_n:OPHAB-Energy Guard-0001` device) across the full, unbroken 15+-hour connection
  lifetime - confirmed by grepping every raw SPINE message referencing the S10's device ID in the
  current `openhab.log`, despite the S10 having received this binding's `DetailedDiscoveryData`
  (so it knows exactly where the feature lives) at connection start.
- The S10's own `nodeManagementUseCaseData` reply declares `"scenarioSupport":[1,2,3,4]` for both
  `limitationOfPowerConsumption` and `limitationOfPowerProduction` - all four scenarios,
  including Scenario 3. This binding's own `nodeManagementUseCaseData` reply, sent to the S10 at
  connection start, declared `"scenarioSupport":[1]` only - Scenario 3 was never advertised,
  despite `AbstractEEBusLimitEnergyGuardUseCase` hosting a live, correctly-running outgoing
  Heartbeat feature since ADR-035.

A SPINE peer only interacts with scenarios the other side has itself declared supported. Since
this binding's own `getScenarioSupport()` (`AbstractEEBusLimitEnergyGuardUseCase`) hardcoded
`List.of(1L)`, the S10 had every reason to believe this binding does not support Scenario 3, and
correctly never bothered polling its Heartbeat - leaving the S10's own LPC state machine
permanently stuck believing no Heartbeat exists (see LPC-901/902/906 "init" state handling,
recorded in project memory), rejecting every limit write regardless of connection age. This is a
pure declaration gap in the binding; `getFeatureRequirements()` on the same class already
correctly requires and hosts the `DeviceDiagnosis`/`SERVER` feature (ADR-035) - only the
use-case-support advertisement was incomplete. No `jeebus.spine`/`jeebus.ship` change is needed
or was made.

## Decision

`AbstractEEBusLimitEnergyGuardUseCase#getScenarioSupport()` now returns `List.of(1L, 3L)` instead
of `List.of(1L)`, for both `EEBusLpcClientUseCase` and `EEBusLppClientUseCase` (which share this
base class). Scenario 2 (Constraints/failsafe-limit-on-timeout) and Scenario 4 are intentionally
_not_ added - this binding does not implement the corresponding failsafe/constraints data points
on the EnergyGuard side, and declaring scenarios that are not actually backed by data would be a
false advertisement in the other direction.

## Consequences

### Positive

- A real, spec-compliant Controllable System (Hager Energy S10 confirmed) now sees this
  binding's EnergyGuard side correctly advertise Scenario 3, and per its own already-existing
  `"Heartbeat device diagnosis client feature"`, should start polling this binding's
  `deviceDiagnosisHeartbeatData` - the immediate next thing to verify live.
- No `jeebus.spine`/`jeebus.ship` change, no new scheduler/polling code, no behavioral change to
  the Heartbeat feature itself (ADR-035's self-perpetuating `HeartbeatDataFunction` is untouched)
  - purely a one-line correction to an existing, already-correct capability's advertisement.

### Negative / follow-up

- Not yet live-tested against the real S10 (needs a binding restart, since `UseCaseData` is
  exchanged once at connection/discovery time) - the working hypothesis is that this alone
  resolves the persistent COMMAND_REJECTED symptom, but that must be confirmed by retesting a
  limit write after the S10 has had a chance to poll the now-advertised Heartbeat at least once.
- If the S10 still rejects writes after this fix and after confirmed Heartbeat polling, the
  remaining candidates are vendor-specific/undocumented S10 behavior, at which point escalating
  to Hager support becomes appropriate - but not before this is ruled out live.
