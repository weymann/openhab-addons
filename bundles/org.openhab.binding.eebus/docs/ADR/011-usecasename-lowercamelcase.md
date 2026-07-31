# ADR-011: SPINE `useCaseName` Is lowerCamelCase, Not PascalCase

## Status

> Proposed

## Context

Pairing with a real Hager Energy S10 completed successfully (SHIP handshake, SPINE
NodeManagement, heartbeat), but the log repeatedly showed:

```text
No common use cases with d:_i:52158_S10-1 at 192.168.1.30 were found. Awaiting NodeManagement
updates.
```

`UseCaseDiscoveryWrapper#filterUseCaseSupport` (jeebus.spine) compares the remote device's
advertised `useCaseName` against this binding's locally registered use case name with a plain,
case-sensitive `Objects.equals(...)` - no normalization on either side.

`jeebus.spine`'s comparison itself is spec-correct SPINE behavior and was not suspected as the
root cause. This binding's four locally registered `getUseCaseName()`/`getName()` values were the
suspect: `EEBusLpcServerUseCase`, `EEBusLppServerUseCase`, `EEBusMpcServerUseCase`, and
`EEBusMpcClientUseCase` all returned PascalCase strings (`"LimitationOfPowerConsumption"`,
`"LimitationOfPowerProduction"`, `"MonitoringOfPowerConsumption"`). Their surrounding comments
already flagged this as unverified: _"exact wire-format casing not independently verified against
the primary spec"_.

To check without guessing, `jeebus.spine`'s `DiscoveryLogger` (env vars
`JEEBUS_LOG_DISCOVERY=true` / `JEEBUS_LOG_DISCOVERY_DIR=<path>`, see its class javadoc) was
enabled on the Raspberry Pi running openHAB, and the `eebus:service` Bridge was restarted to force
a fresh `Discovery` run against the S10 (a same-IP SHIP reconnect alone does not re-trigger it -
`NodeManagementImpl.notifyDisconnect()` is currently a no-op stub and never clears the internal
per-address `discoveryMap`, so only discarding the whole `Device`/`NodeManagementImpl` - i.e. a
Bridge-level `dispose()`/`initialize()` cycle - starts a new one). The resulting
`discovery-d_i_52158_S10-1.json` confirmed the real wire values sent by the S10:

```json
"useCaseName" : "monitoringOfPowerConsumption"
"useCaseName" : "limitationOfPowerConsumption"
"useCaseName" : "limitationOfPowerProduction"
```

All lowerCamelCase - the first letter is lowercase, unlike this binding's PascalCase constants.
Actor strings (`"CEM"`, `"MonitoringAppliance"`, `"EnergyGuard"`) are a separate field with its own
casing convention and already matched the S10's wire values exactly - not affected by this bug.

## Decision

Fix the four use-case-name strings in this binding to lowerCamelCase, matching the confirmed wire
format:

| Class | Method | Before | After |
|---|---|---|---|
| `EEBusLpcServerUseCase` | `getUseCaseName()` | `"LimitationOfPowerConsumption"` | `"limitationOfPowerConsumption"` |
| `EEBusLppServerUseCase` | `getUseCaseName()` | `"LimitationOfPowerProduction"` | `"limitationOfPowerProduction"` |
| `EEBusMpcServerUseCase` | `getName()` | `"MonitoringOfPowerConsumption"` | `"monitoringOfPowerConsumption"` |
| `EEBusMpcClientUseCase` | `getName()` | `"MonitoringOfPowerConsumption"` | `"monitoringOfPowerConsumption"` |

No change to `jeebus.spine`'s case-sensitive comparison - it is spec-correct; this was a bug in
this binding's own hardcoded strings, not something requiring the protected-library approval
process.

## Consequences

### Positive

- MPC (Monitoring of Power Consumption) - the binding's primary implemented use case - can now
  actually be discovered against real devices that were previously silently rejected by this
  casing mismatch, despite an otherwise fully successful SHIP/SPINE connection.
- Root-caused with real wire evidence (a device's actual discovery payload), not guesswork - the
  previously-honest "not independently verified" caveat in the surrounding comments is now
  resolved and replaced with a dated, sourced confirmation.
- No `jeebus.ship`/`jeebus.spine` changes needed - stays within the protected-library constraint.

### Negative

- Only confirmed against one real device (Hager Energy S10) and only for MPC/LPC/LPP. If any other
  EEBUS use case is added later, its name string should be verified the same way (real discovery
  JSON, or an authoritative wire-format source) rather than assumed from a written spec's display
  casing.
- No compile/runtime verification was possible in the session this fix was written in (non-
  functional shell sandbox); a build/`$QA` pass should confirm before merging.

---

_Changes the string literal returned by `getUseCaseName()`/`getName()` in
`EEBusLpcServerUseCase`, `EEBusLppServerUseCase`, `EEBusMpcServerUseCase`, and
`EEBusMpcClientUseCase` from PascalCase to lowerCamelCase; updates the now-resolved "not
independently verified" caveats in their comments and in
`AbstractEEBusLimitControllableSystemUseCase#getUseCaseName()`'s javadoc._
