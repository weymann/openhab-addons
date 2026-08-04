# ADR-007: Use the Feature's Canonical Wrapper, Not a Throwaway One

## Status

> Proposed

## Context

Live testing surfaced a crash while starting an `eebus:service` Bridge with LPC/LPP enabled:

```text
java.lang.IllegalArgumentException: Cannot add description, because descriptionFunction is not set in feature.
        at org.openmuc.jeebus.spine.utils.features.deviceconfiguration.KeyValueInitialData.addToFeature(KeyValueInitialData.java:64)
        at org.openhab.binding.eebus.internal.transport.AbstractEEBusLimitControllableSystemUseCase.setupDeviceConfiguration(AbstractEEBusLimitControllableSystemUseCase.java:323)
```

Traced (reading the actual `jeebus.spine` source, not guessing) to a wrapper-identity bug in how
this binding obtained `FeatureWrapper` instances (`DeviceConfigurationFeature`,
`LoadControlFeature`, `DeviceDiagnosisFeature`, `MeasurementFeature`):
`AbstractEEBusLimitControllableSystemUseCase#setupLoadControl/setupDeviceConfiguration/
setupDeviceDiagnosis` and `EEBusMpcServerUseCase#setup()` all called

```java
FeatureInformationService.getInstance().createFeatureWrapper(rawFeature)
```

to get a wrapper for an already-existing `Feature`. That call always constructs a **brand-new**
wrapper instance (`DeviceConfigurationFeature.MetaInformation#create()` etc. simply `return new
DeviceConfigurationFeature(feature)`), disconnected from the one `FeatureImpl` created for itself
in `setType()` (`featureWrapper = FeatureInformationService.getInstance().createFeatureWrapper(this)`,
`FeatureImpl.java:747`) and stores as `feature.featureWrapper`. `FeatureImpl#addFunction()` only
calls `updateFunction(...)` on that one canonical, stored wrapper (`FeatureImpl.java:425`) - never
on any other wrapper instance a caller happens to have constructed. A throwaway wrapper's function
fields (e.g. `DeviceConfigurationFeature.keyValueDescriptionListDataFunction`) therefore stay
`null` forever, even after `addKeyValueDescriptionListDataFunction()` was called successfully on
that very instance - which is exactly what `KeyValueInitialData.addToFeature()` then trips over
when it calls `feature.getKeyValueDescriptionListDataFunction()`.

`setupLoadControl` happened not to crash only because `LoadControlFeature`'s `addXxxFunction()`
methods return the function reference directly and additionally call `function.setLoadControlFeature(this)`
themselves - so code that captures the return value directly (as `setupLoadControl` does) never
needed the wrapper's own field to be populated. `setupDeviceDiagnosis` has the identical latent
bug as `setupDeviceConfiguration` (`DeviceDiagnosisFeature#addHeartBeatDataFunction()` returns its
own `heartbeatDataFunction` field, not the function directly) - it simply never ran, since
`setupDeviceConfiguration` (called first) always threw before reaching it.

## Decision

Stop constructing throwaway wrappers. Use the feature's own canonical wrapper instead, via
`Feature#getFeatureWrapper(Class<T> wrapperClass)` - the API `jeebus.spine` already exposes for
exactly this purpose.

`AbstractEEBusLimitControllableSystemUseCase` gained a private generic helper,
`findFeatureWrapper(Entity, FeatureTypeEnumType, Class<T>)`, replacing the
`findFeature(...)` + `FeatureInformationService.getInstance().createFeatureWrapper(...)` pair at
all three of its call sites (`setupLoadControl`, `setupDeviceConfiguration`,
`setupDeviceDiagnosis`). `EEBusMpcServerUseCase#setup()` was fixed the same way inline (it has no
other wrapper lookup to share the helper with).

`jeebus.spine` itself was not touched - out of scope per this project's global rule that
`jeebus.ship`/`jeebus.spine` require prior human approval for any change. This is fixed entirely
from the binding side, which is sufficient: `Feature#getFeatureWrapper(Class)` is public API
designed for this.

## Consequences

### Positive

- Fixes the reproduced startup crash for LPC/LPP-enabled `eebus:service` Bridges.
- Also fixes the identical, previously-latent bug in `setupDeviceDiagnosis` (heartbeat would have
  NPE'd on `startHeartbeat()` the moment `setupDeviceConfiguration` stopped throwing first) and in
  `EEBusMpcServerUseCase` (MPC server measurement wrapper was equally throwaway, just not yet
  exercised by a getter call that would have surfaced it).
- One shared helper (`findFeatureWrapper`) instead of three near-identical
  `findFeature`+cast+`createFeatureWrapper` blocks - less to get wrong the same way again in a
  future use case.

### Negative

- `findFeatureWrapper`'s `IllegalStateException` for "no wrapper of the expected type attached" is
  not expected to be reachable in practice (every `FeatureTypeEnumType` this binding uses is in
  `FeatureInformationService`'s known-type cache, and `Entity#addUseCase()` is what adds these
  features via `getFeatureRequirements()` in the first place) - it exists purely as a defensive
  invariant check, per this project's null-handling rules, not a scenario this ADR claims to have
  reproduced.

---

_Fixes `AbstractEEBusLimitControllableSystemUseCase` (`setupLoadControl`,
`setupDeviceConfiguration`, `setupDeviceDiagnosis`) and `EEBusMpcServerUseCase#setup()`; no
`jeebus.spine` changes._
