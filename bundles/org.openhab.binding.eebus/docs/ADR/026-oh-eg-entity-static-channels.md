# ADR-026: `oh-eg-entity` Thing Type with Statically Declared LPC/LPP Channels (No Dedicated Convenience Bridge)

## Status

> Accepted

## Context

Follow-up to docs/ADR/025-oh-cs-entity-static-channels.md: the user asked for an analogous Thing
type on the EnergyGuard (LPC/LPP Client role) side - `eebus:oh-eg-entity` - with its own config
and pre-defined Channels for the LPC/LPP EnergyGuard use cases, the mirror image of ADR-025's
`eebus:oh-cs-entity` for the Controllable System (Server role) side.

Unlike the CS side, however, there is today no `eebus:oh-eg-device` convenience Bridge analogous
to `eebus:oh-cs-device` (docs/ADR/023-cs-service-convenience-bridge.md) - the EnergyGuard/Client
role is only reachable via the fully generic `eebus:oh-device` Bridge, with LPC/LPP selected
among many other options in its `supportedUseCasesClient` checkbox list
(`EEBusHandler`'s `cfg.supportedUseCasesClient.contains("LPC"/"LPP")` construction, unlike the
Server-role side's `THING_TYPE_OH_CS_DEVICE`-gated hardcoded LPC+LPP construction). This matters
because ADR-025's static-Channel design leans on `eebus:oh-cs-device` _guaranteeing_ LPC+LPP is
active - there is no equivalent guarantee on the generic `eebus:oh-device` Bridge.

This was resolved via `AskUserQuestion` before implementation: should a new `eebus:oh-eg-device`
convenience Bridge be built too (full mirror of ADR-023+ADR-025, `eebus:oh-eg-entity` exclusive
under it), or should `eebus:oh-eg-entity` instead be added as an additional Entity Thing type
under the existing generic `eebus:oh-device`, without a new Bridge? **The user explicitly chose
the latter** (deliberately against the recommended "build the full analogous pair" option).

Both `AbstractEEBusLimitControllableSystemUseCase` (Server role, resolves the paired EnergyGuard
peer's `EEBusOhEntityHandler` via `energyGuardOhEntityHandler`) and
`AbstractEEBusLimitEnergyGuardUseCase` (Client role, resolves the paired ControllableSystem
peer's `EEBusOhEntityHandler` via `ohEntityHandlerResolver`) already populate the very same
`lpc`/`lpp` Channel Groups on whichever `EEBusOhEntityHandler` they resolve to, via the same
`applyLimitStatus`/`applyFailsafeStatus` methods - the Channel semantics ("does this Entity
currently have an active power limit, from its own perspective") do not depend on which local
role (CS or EG) is observing them. This is what makes `oh-eg-entity` structurally almost
identical to `oh-cs-entity`, differing only in which Bridge type(s) it is offered under and
whether that Bridge guarantees LPC/LPP.

## Decision

### 1. New `eebus:oh-eg-entity` Thing type, additional (not exclusive) child of `eebus:oh-device`

`eebus:oh-device`'s `supported-bridge-type-refs` set is unchanged - both `eebus:oh-entity` and
the new `eebus:oh-eg-entity` remain valid children of it, side by side. Nothing on
`eebus:oh-device` restricts which Entity Thing type is used, and nothing on `eebus:oh-eg-entity`
requires `eebus:oh-device`'s `supportedUseCasesClient` to actually include `LPC`/`LPP` - a user
who creates one without configuring the checkbox simply sees Channels that never populate.
`eebus:oh-cs-device`/`eebus:oh-cs-entity` are unaffected by this change.

### 2. `lpc`/`lpp` Channel Groups declared statically, same as `oh-cs-entity`

`eebus:oh-eg-entity` declares the identical `<channel-groups>` block ADR-025 introduced for
`eebus:oh-cs-entity`:

```xml
<channel-groups>
    <channel-group id="lpc" typeId="lpc"/>
    <channel-group id="lpp" typeId="lpp"/>
</channel-groups>
```

reusing the same `lpc`/`lpp` `channel-group-type` definitions (`limit-active`, `limit-value`,
`failsafe-limit-value`, `failsafe-duration-minimum`) unchanged. The only functional difference
from `oh-cs-entity` is that these Channels are not backed by a Bridge-level guarantee that LPC/LPP
is actually active - see "Consequences" below.

### 3. Same config and handler classes as `eebus:oh-entity`/`eebus:oh-cs-entity` - no duplication

`eebus:oh-eg-entity` reuses `EEBusOhEntityConfiguration` (`ski`, `entityAddress`, `shipId`) and
`EEBusOhEntityHandler`, exactly as `eebus:oh-cs-entity` does (docs/ADR/025's Decision 3) - no new
Java classes, no Thing-type-specific branching in either class. `EEBusHandlerFactory` gains one
new `if (THING_TYPE_OH_EG_ENTITY.equals(thingTypeUID))` branch constructing the same
`EEBusOhEntityHandler`. `EEBusSkiOptionProvider`'s `ski`-option and `targetBridgeUid` logic is
extended to recognize `eebus:oh-eg-entity` alongside `eebus:oh-entity`/`eebus:oh-cs-entity` -
`targetBridgeUid` already handled `eebus:oh-device` as a valid target Bridge type, so only the
Entity-Thing-type recognition needed extending, not the Bridge-type recognition.

### 4. `entityAddress` retained, same as `oh-entity`/`oh-cs-entity`

Same field, same not-yet-wired-into-transport-routing status, same rationale as ADR-025's
Decision 4 and docs/ADR/024's "Out of scope" - kept for consistency across all three Entity Thing
types and readiness for future per-Entity routing work.

## Consequences

### Positive

- `eebus:oh-eg-entity` gives a user working the EnergyGuard/Client-role case the same
  immediately-visible-Channels convenience `eebus:oh-cs-entity` already gives the Controllable
  System/Server-role case, without waiting for a first EEBus event.
- Zero code duplication, same as ADR-025: `EEBusOhEntityConfiguration`/`EEBusOhEntityHandler`
  stay the single source of truth for all three Entity Thing types now in existence.
- `eebus:oh-device`/`eebus:oh-entity` remain exactly as generic as before - `eebus:oh-eg-entity`
  is purely additive, not a replacement, matching the same non-destructive pattern every prior
  Thing-type addition in this binding has followed.

### Negative

- **No Bridge-level guarantee, unlike `oh-cs-entity`.** `eebus:oh-device` is generic/checkbox-
  driven: a user can create an `eebus:oh-eg-entity` Thing without ever enabling `LPC`/`LPP` in
  `supportedUseCasesClient`, in which case its statically declared Channels simply never
  populate (state stays `NULL` indefinitely) - nothing in the Thing-type model itself prevents or
  flags this mismatch, unlike `eebus:oh-cs-device`, whose fixed configuration makes the CS-side
  Channels' emptiness-before-first-event the _only_ possible transient state. This was an
  explicit, informed trade-off of the user's chosen option (declined the "build the full
  `oh-eg-device` convenience Bridge too" alternative that would have closed this gap the same way
  ADR-023 closed it for the CS side).
- Three Entity Thing types now exist (`oh-entity`, `oh-cs-entity`, `oh-eg-entity`) instead of two,
  each with its own `thing-types.xml` declaration and Main UI "Add Thing" entry - a further small
  increase in surface area, documented in the shared handler class javadoc to keep it
  discoverable, same approach as ADR-025.
- `eebus:oh-entity` and `eebus:oh-eg-entity` both remain valid under `eebus:oh-device`
  simultaneously, unlike the CS side's exclusivity - a user must pick the right one for what they
  intend (any/mixed use case vs. specifically LPC/LPP-with-static-Channels); nothing in the UI
  distinguishes this beyond the two Thing types' labels/descriptions.

## Migration

None needed. This is purely additive: no existing Thing type's `supported-bridge-type-refs` or
behavior changes (unlike ADR-025, which narrowed `eebus:oh-entity`'s bridge refs and so required
a manual Thing-recreation step for one narrow prior window). Any existing `eebus:oh-entity` Thing
under `eebus:oh-device` continues to work unchanged; a user who wants the statically-declared
Channels instead may create a new `eebus:oh-eg-entity` Thing alongside or in place of it.

## Revision (docs/ADR/027-derive-local-use-cases-from-entities.md, 2026-08-24)

This ADR's Negative consequence "No Bridge-level guarantee, unlike `oh-cs-entity`" is resolved by
ADR-027: `eebus:oh-device` no longer holds `supportedUseCasesClient`/`supportedUseCasesServer` at
all, and the Bridge now derives its local Use-Case set from its attached children instead. An
`eebus:oh-eg-entity` Thing's mere presence under the Bridge is therefore now sufficient by itself
to guarantee `LPC`/`LPP` Client is active locally - the same Bridge-level guarantee
`eebus:oh-cs-entity` already had, closing the asymmetry this ADR originally accepted as a
deliberate trade-off. `thing-types.xml`'s `eebus:oh-eg-entity` comment/description were updated
accordingly.

This ADR's Decisions 1-4 (statically declared `lpc`/`lpp` Channel Groups, shared config/handler
classes, non-exclusive/additive relationship to `eebus:oh-device`, `entityAddress`) are otherwise
unaffected and remain in force.

---

_Refines docs/ADR/025-oh-cs-entity-static-channels.md's static-Channel pattern by applying it to
the EnergyGuard/Client-role side - neither ADR-023, ADR-024, nor ADR-025 is superseded, all
remain in force. Unlike ADR-025, this ADR does **not** introduce a dedicated convenience Bridge
or an exclusive parent-child relationship - that was a deliberate, explicit user decision made
via `AskUserQuestion` during `$Concept`, against the recommended option._

_Partially revised by [`031-remove-energyguard-monitoring-channels.md`](031-remove-energyguard-
monitoring-channels.md) (2026-08-27): the static `<channel-groups>` declaration this ADR put on
`eebus:oh-eg-entity` is removed - per an explicit user correction, the EnergyGuard only ever
writes a limit via a tagged Item and does not need a Channel echoing the peer's own reported
status back. This ADR's Thing-type decision itself (a dedicated, non-exclusive `oh-eg-entity`
under `oh-device`, no dedicated convenience Bridge) is unaffected and remains in force - kept
`Accepted`, not superseded._
