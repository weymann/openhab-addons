# Proposal: EnergyGuard Outgoing Heartbeat

## Intent

CONCEPT.md §7 item (19): `AbstractEEBusLimitEnergyGuardUseCase` (the Client-/"EnergyGuard"-role
LPC/LPP implementation) never sent its own Heartbeat - no local `DeviceDiagnosis` Server feature,
no `startHeartbeat()` call - even though Scenario 3 (Heartbeat) is documented as mutual by the
primary source (`EEBus_UC_IG_GeneralGuidelines_V1.0.0.pdf`): "in the LPC Use Case, the
'EnergyGuard' (Client Actor) hosts a Server Feature to provide its own heartbeat to the
'ControllableSystem' (Server Actor)". A strict real ControllableSystem peer (e.g. a Hager Energy
S10) may not consider this binding a fully spec-compliant EnergyGuard without it. The
Controllable-System (Server) side already implements this correctly
(`AbstractEEBusLimitControllableSystemUseCase#setupDeviceDiagnosis`).

User decision (2026-08-23, recorded in CONCEPT.md item 19): reject a ThingAction+Rule design (a
forgotten/misconfigured Rule would be a silent failure in a safety-relevant mechanism) in favor
of mirroring the Controllable-System side's proven, self-perpetuating two-liner
(`addHeartBeatDataFunction(60)` + `startHeartbeat()`), with no scheduler/polling code needed in
this binding.

## Scope

In scope:

- `AbstractEEBusLimitEnergyGuardUseCase` declares a local `DeviceDiagnosis` Server feature
  requirement (`FeatureRequirement`, mirroring the Controllable-System side's identical entry)
  and calls a new private `setupDeviceDiagnosis()` from `setup()`, starting a self-perpetuating
  Heartbeat (60 second interval, per LPC-005/006).
- Class-level Javadoc and `getFeatureRequirements()`'s inline comment updated to describe the new
  behavior instead of documenting it as "Not implemented here".
- CONCEPT.md §7 item (19) marked implemented.

Out of scope:

- Any change to the existing limit-write path (Scenario 1) or failsafe-value path (Scenario 2) -
  both are unaffected and unchanged.
- The EnergyGuard side watching/consuming the ControllableSystem peer's own incoming Heartbeat -
  unlike the Controllable-System side (which runs `EEBusLimitControlStateMachine`, a failsafe
  state machine that must react to Heartbeat loss), the EnergyGuard's write path is not gated by
  peer Heartbeat status and has no equivalent watchdog. Only the _outgoing_ Heartbeat (this
  binding's own) is in scope, matching the exact fix the user approved.
- Any change to `jeebus.ship`/`jeebus.spine` - not needed; both required framework mechanics
  (`Entity#satisfyFeatureRequirement`'s "get-or-add" idempotency for a `FeatureRequirement`
  shared by two Use Cases on the same Entity, and `HeartbeatDataFunction`/`DeviceDiagnosisFeature`
  themselves) are already implemented and already exercised by the Controllable-System side - see
  docs/ADR/035-energy-guard-outgoing-heartbeat.md "Context" for the verification read of that
  source.
- Any new Channel, config parameter, or user-visible behavior - this closes a spec-compliance gap
  invisible to the openHAB user; `README.md` needs no update.

## Open Questions

None - this mirrors an already-implemented, already-tested pattern in the same codebase, and the
design choice (mirror `setupDeviceDiagnosis()`, reject ThingAction+Rule) was already made by the
user on 2026-08-23 (CONCEPT.md item 19).
