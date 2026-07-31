# ADR-039: EnergyGuard actor binds to the peer's DeviceConfiguration feature

## Status

Accepted

## Context

Real Hager Energy S10 retest, 2026-09-03, after ADR-038 (EnergyGuard declares Scenario 3
support) was deployed: the S10 was confirmed live on the wire to actively re-read this binding's
`nodeManagementUseCaseData` on every fresh connection and to receive the corrected
`scenarioSupport:[1,3]`. Despite this, every LPC `LoadControl` limit write was still rejected
with SPINE Error 7 (COMMAND_REJECTED) - ADR-038 was necessary but not sufficient.

Investigation (raw SPINE traffic across six separate S10 connection attempts spanning two test
days, correlated against `jeebus.spine` source and the EEBUS LPC Implementation Guideline)
established, in order:

- `HeartbeatDataFunction` (`jeebus.spine`) is a correctly-implemented, self-perpetuating,
  per-instance push mechanism (`resetHeartbeat()`/`sendHeartbeat()` via a dedicated
  `ScheduledExecutorService`, armed immediately and every 60s thereafter by `startHeartbeat()`).
  It fires reliably on every `setup()`/reconnect (confirmed via the `"Device Diagnosis - starting
  heartbeat"` log line), but `sendHeartbeat()` unconditionally calls
  `FeatureImpl.notifySubscribers()`, which silently no-ops when the feature's `subscribers` list
  is empty - no exception, no log line, nothing to indicate anything is wrong.
- Across all twelve `NodeManagementSubscriptionRequestCall`s ever processed in the entire log
  history (both test days), exactly one ever targeted this binding's DeviceDiagnosis feature
  (`entity=1, feature=1`) - from the local simulated CS, once, at the very start of testing on
  2026-09-02. Every subscription request from the real S10 (six separate connection attempts)
  targeted only the mandatory `NodeManagement` feature (`entity=0, feature=0`). The S10 has never
  once subscribed to this binding's Heartbeat, including immediately after re-reading the
  corrected `scenarioSupport` from ADR-038.
- The S10's own `nodeManagementDetailedDiscoveryData` confirms a dedicated `"Heartbeat device
  diagnosis client feature"` (Generic/CLIENT) at its own functional entity (entity 6, feature
  1001, alongside its `LoadControl`/`ElectricalConnection`/`Measurement`/`DeviceConfiguration`
  features) - built specifically to consume a peer's Heartbeat, but never used.
- `NodeManagementBindingRequestCall` was ruled out as an alternative activation path: every
  outgoing binding call this binding ever sent targets the S10's `LoadControl` feature only
  (`entity=6, feature=10`, per ADR-034); no `nodeManagementBindingRequestCall` was ever received
  from the S10 for anything (the log line that would report a denial, `"Binding from device {}
  denied"` in `BindingRequestFunction`, never appears at all).
- Section 3.8 of `EEBus_UC_IG_LimitationOfPowerConsumption_V1.1.0.pdf` ("Behaviour of
  Controllable System in case of multiple Energy Guard instances on a connected device")
  specifies the missing precondition explicitly:

  > 2\. Await binding requests: The Controllable System SHALL wait until it receives binding
  > requests from the Energy Guard. The Energy Guard must request bindings for the Features
  > LoadControl (for power limits) **and DeviceConfiguration (for failsafe values)** to obtain
  > write privileges.
  >
  > 4\. Execution of subscription: Only after the bindings for LoadControl **and**
  > DeviceConfiguration have been successfully established with the same Entity, the
  > Controllable System SHALL send the subscription request for the DeviceDiagnosis Feature
  > (Heartbeat) to exactly that Entity.

  This section's stated precondition (more than one CEM Entity offering LPC on the connected
  device) does not literally apply to this binding's single-CEM-Entity topology, but a
  Controllable System that implements this "await both bindings" gate unconditionally - a
  simpler and more defensive design than special-casing the single-instance case - would produce
  exactly the observed behaviour: withhold the Heartbeat subscription indefinitely, stay in
  "failsafe state" (LPC §2.2/§2.3/§2.12, also reproduced in the same IG document), and reject
  every limit write with COMMAND_REJECTED, regardless of connection age or of ADR-038's fix.
- `AbstractEEBusLimitEnergyGuardUseCase#resolveLimitIdAndRegisterWriteListeners` confirmed to
  bind only `LoadControl` (`nodeManagement.requestBind(featureAddress,
  FeatureTypeEnumType.LOAD_CONTROL)`, ADR-034). `DeviceConfiguration` was, by a deliberate
  2026-09-02 decision made earlier in this same investigation (see
  `logDeviceConfigurationDiagnostic`'s javadoc), treated as unrelated to `LoadControl` and
  read-only/diagnostic - never bound. That decision is now understood to be the actual remaining
  gap: the _read_ is indeed unrelated to `LoadControl` and correctly optional, but the _bind_ is
  a separate, required precondition for the CS's Heartbeat subscription per IG section 3.8.

Correction to ADR-038: that ADR's Context section characterised Heartbeat delivery as
"peer-polled, not push/notified" based on the LPC/LPP Technical Specification's Table 12 listing
`read` as `deviceDiagnosisHeartbeatData`'s only function-level operation. IG section 3.8 (found
this session) explicitly requires a _subscription_ request for the same feature, confirming that
`read` (Table 12) and `subscribe` (a NodeManagement-level mechanism orthogonal to any single
function's possible operations, per `jeebus.spine`'s `FeatureImpl`) are two independently
available access paths, not exclusive alternatives. `HeartbeatDataFunction`'s existing
push/subscribe implementation (ADR-035) was correct all along; ADR-038's aside was an incomplete
inference and is superseded by this ADR's finding.

## Decision

`AbstractEEBusLimitEnergyGuardUseCase#resolveLimitIdAndRegisterWriteListeners` now also fires a
binding request against the peer's `DeviceConfiguration` feature (new private method
`bindDeviceConfiguration`), whenever the peer exposes one (`deviceConfigFeatureAddress != null`,
from `UseCasePartner#getCompleteFeatureAddress`). This is deliberately:

- **Independent of the `LoadControl` bind/write chain** - fire-and-forget, logged only, does not
  gate `registerWriteListeners` or anything else. This binding does not (yet) write failsafe
  values through this binding (see the open "document Scenario 2/4" project-memory follow-up),
  so there is nothing further to wire up on success; the bind request itself is the only thing a
  spec-compliant CS needs to see.
- **Not conditioned on `LoadControl` binding first** - both binds are fired close together so a
  CS validating "both from the same Entity" (IG section 3.8 step 3) sees them without an
  artificial ordering dependency this binding has no reason to impose.

## Consequences

### Positive

- A Controllable System that implements IG section 3.8's "await both bindings before
  subscribing to Heartbeat" procedure (Hager Energy S10 suspected, not yet confirmed) now has
  everything it is waiting for, and should proceed to subscribe to this binding's Heartbeat -
  the immediate next thing to verify live.
- No `jeebus.spine`/`jeebus.ship` change - `NodeManagement.requestBind` (used identically to the
  existing `LoadControl` bind) is the only API surface touched, entirely within
  `org.openhab.binding.eebus`.
- A peer without a `DeviceConfiguration` feature (e.g. the local simulated CS predating this
  feature) is unaffected - `bindDeviceConfiguration` no-ops at debug level, exactly like the
  existing `logDeviceConfigurationDiagnostic` does for the same `null` case.

### Negative / follow-up

- Not yet live-tested against the real S10 - needs a binding restart (bindings, like
  `UseCaseData`, are established once per connection/discovery cycle) and a subsequent limit
  write to confirm the S10 (a) now sends a `DeviceConfiguration` bind acknowledgement, (b)
  proceeds to subscribe to this binding's Heartbeat, and (c) finally accepts a limit write.
- If the S10 still never subscribes after this fix, the next candidates are: the literal
  multi-Entity precondition of IG section 3.8 genuinely does not apply to the S10's internal
  logic (i.e. it uses some other, undocumented trigger), or a vendor-specific S10 quirk -  at
  that point escalating to Hager support becomes appropriate, but not before this fix is ruled
  out live.
- This binding still does not implement actual failsafe-value writes (Scenario 2) or Constraints
  (Scenario 4) on the EnergyGuard side - the `DeviceConfiguration` bind added here exists solely
  to satisfy the CS's Heartbeat-subscription precondition, not to enable failsafe writes. The
  open project-memory question of whether to document Scenario 2/4 as a separate follow-up task
  remains unanswered and unrelated to this fix.
