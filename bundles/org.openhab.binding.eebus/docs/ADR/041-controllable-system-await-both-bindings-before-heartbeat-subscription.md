# ADR-041: Controllable System role gates the Heartbeat subscription on both bindings (IG section 3.8)

## Status

Accepted

## Context

Following the real Hager Energy S10 `COMMAND_REJECTED` investigation (ADR-038, ADR-039 - see
`eebus-lpc-command-rejected-2026-09-02.md` in project memory), the user asked whether this
binding's own Controllable System role (`AbstractEEBusLimitControllableSystemUseCase`, shared by
`EEBusLpcServerUseCase`/`EEBusLppServerUseCase`, used by the local simulated `oh-cs-device` Thing)
behaves exactly per spec, ahead of running a further regression test against it.

Review of `onEnergyGuardFound(List<UseCasePartner> partners)` found that it called
`nodeManagement.requestSubscription(heartbeatAddress, FeatureTypeEnumType.DEVICE_DIAGNOSIS, ...)`
**unconditionally and immediately** upon `UseCasePartner` discovery - with no check anywhere in
the class for whether the Energy Guard partner had sent binding requests for `LoadControl` and
`DeviceConfiguration` first. This directly contradicts EEBUS LPC Implementation Guideline section
3.8 ("Behaviour of Controllable System in case of multiple Energy Guard instances on a connected
device"), which the ADR-039 investigation established as the (necessary, if not sufficient) real
Hager S10 precondition for its own Heartbeat subscription:

> 2\. Await binding requests: The Controllable System SHALL wait until it receives binding requests
> from the Energy Guard. The Energy Guard must request bindings for the Features LoadControl (for
> power limits) and DeviceConfiguration (for failsafe values) to obtain write privileges.
>
> 4\. Execution of subscription: Only after the bindings for LoadControl and DeviceConfiguration
> have been successfully established with the same Entity, the Controllable System SHALL send the
> subscription request for the DeviceDiagnosis Feature (Heartbeat) to exactly that Entity.

Practical consequence: our own CS role never actually exercised this gate. This explains, in
hindsight, why the local simulated CS successfully subscribed to the EnergyGuard's Heartbeat on
the very first attempt in earlier testing (2026-09-02 20:45:55, see the command-rejected project
memory entry) while the real S10 never has - the local CS was never testing the same precondition
the S10 apparently enforces. A regression test against `oh-cs-device` without this gate would
"pass" identically whether or not the EG-side ADR-039 fix (`bindDeviceConfiguration`) is present,
since the local CS has no gate to satisfy in the first place.

## Decision

Implement the IG section 3.8 "await both bindings" gate in
`AbstractEEBusLimitControllableSystemUseCase`, using `jeebus.spine`'s existing public
`Feature#addBindingListener(BindingListener)` SPI (no `jeebus.spine`/`jeebus.ship` change):

- `setup()` registers a `BindingListener` on this Entity's local `LoadControl` and
  `DeviceConfiguration` server features (`registerHeartbeatSubscriptionGate`), and resets three new
  tracking fields to `null` at the top of `setup()` so a reconnect (a fresh SPINE Device/
  `FeatureImpl` generation) starts the gate fresh rather than carrying over a previous connection's
  now-stale binding state.
- `onFeatureBound(BindingRequest, boolean isLoadControl)` fires synchronously whenever a peer's
  binding request against one of those two features is accepted (per `FeatureImpl#bind`, confirmed
  via read-only `jeebus.spine` source review). It records the bound peer Entity's `device+entity`
  identity (`entityKey`, ignoring the feature number) into `loadControlBoundEntityKey`/
  `deviceConfigurationBoundEntityKey`, then calls `maybeSubscribeToEnergyGuardHeartbeat()`.
- `onEnergyGuardFound` no longer subscribes directly. It captures the discovered partner's
  `DeviceDiagnosis` feature address into `pendingHeartbeatSubscriptionAddress` and also calls
  `maybeSubscribeToEnergyGuardHeartbeat()` - covering both possible orderings (bindings arriving
  before or after `UseCasePartner` discovery).
- `maybeSubscribeToEnergyGuardHeartbeat()` sends the subscription only once both bound-entity keys
  are non-null, equal to each other, and equal to the pending partner's own entity key (i.e. "the
  same Entity" bound both features and is the one we're about to subscribe to) - then clears
  `pendingHeartbeatSubscriptionAddress` as a subscribe-once guard.

This makes `oh-cs-device` a faithful local stand-in for the (apparent) real-S10 gating behaviour,
so the still-open root cause behind the S10's `COMMAND_REJECTED` symptom (ADR-039's retest showed
IG section 3.8 is necessary but not sufficient for that device) can potentially be explored
locally - e.g. bind ordering, timing, or entity-matching variants - without needing the real
hardware for every iteration. It does not resolve or claim to resolve the still-open S10 issue
itself.

## Consequences

### Positive

- `AbstractEEBusLimitControllableSystemUseCase` now implements the same documented spec
  precondition (IG section 3.8) this investigation determined the real S10 very likely enforces,
  closing the compliance gap found while answering the user's "does our CS behave exactly per
  spec?" question.
- Built entirely on existing public `jeebus.spine` API (`Feature#addBindingListener`,
  `NodeManagement#requestSubscription`) - no `jeebus.spine`/`jeebus.ship` change, consistent with
  the standing project constraint.
- A local `oh-cs-device` regression test now exercises the actual gate, not a no-op stand-in -
  useful both as a sanity check that the gate itself works (bindings arrive, keys match, Heartbeat
  subscription fires and Heartbeat notifications keep flowing) and, longer term, as a possible
  local reproduction environment for the open S10 root cause.
- The gate resets on every `setup()` (reconnect), so a stale binding from a previous connection
  generation can never satisfy it for a new one.

### Negative / follow-up

- **Not yet compiled or live-tested** - no local Maven/JDK in this environment; verified only by
  manual review (brace/paren balance, single-occurrence checks, side-by-side comparison against
  the pre-existing, working `onEnergyGuardFound`/binding code this mirrors). User needs to compile,
  deploy, and retest against `oh-cs-device` (as originally requested) before relying on this.
- Does **not** implement the literal precondition of IG section 3.8 ("Controllable System detects
  more than one CEM Entity offering the LPC/LPP Use Case") - like the real S10 apparently does, it
  applies the "await both bindings" rule unconditionally, regardless of how many Energy Guard
  Entities are actually present. Consistent with this class's own documented "only the first
  detected Energy Guard partner is tracked" simplification (class javadoc) - revisit together if
  genuine multi-Energy-Guard support is ever added.
- Does not change or fix the still-open real-S10 `COMMAND_REJECTED` root cause (see the
  command-rejected project memory entry's "Not yet done" section for that investigation's current
  candidates) - this ADR is scoped to our own CS role's spec-compliance only.
- If a real Energy Guard peer (or a future EG-role test double) never sends a
  `DeviceConfiguration` binding request at all (e.g. it doesn't implement Scenario 2), this CS role
  will now never subscribe to its Heartbeat either - previously it always did. This is the intended,
  spec-faithful behaviour per IG section 3.8, but it is a behavioural change worth being aware of
  for any EG-role peer that is itself not yet ADR-039-complete.
