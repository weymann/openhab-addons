# ADR-036: Dedicated `oh-mpc-entity` Thing Type with Statically Declared MPC Channel

## Status

> Accepted

## Context

`docs/ADR/025-oh-cs-entity-static-channels.md` and `docs/ADR/026-oh-eg-entity-static-channels.md` established a pattern: a dedicated Entity Thing type, additive under `eebus:oh-device`, whose mere presence unconditionally activates a fixed Use Case set on the parent Bridge (`docs/ADR/027-derive-local-use-cases-from-entities.md`) and, where the role has Channels at all, declares them statically instead of waiting for the first EEBus event.

MPC Client (CONCEPT.md 5.4.1: "Monitoring Appliance" - the receiving side that reads a paired peer's total power measurement) never got this treatment. It remains reachable only through the fully generic `eebus:oh-entity`: a user must tick `MPC` in that Thing's `supportedUseCasesClient` config, and its `mpc#power` Channel (`docs/ADR/014-dynamic-client-role-channels.md`) is created dynamically, only after the first successful measurement resolution - not present immediately after the Thing is created. This is the same situation ADR-025 addressed for the Controllable System (LPC/LPP Server) side.

Unlike the LPC/LPP EnergyGuard case (`eebus:oh-eg-entity`, ADR-026), there is no read-vs-write asymmetry to resolve here: MPC Client's entire purpose is reading a peer's measurement into a Channel (`EEBusMpcClientUseCase#applyMeasurement` -> `EEBusOhEntityHandler#applyMpcPower`), so the ADR-025 static-Channel treatment applies directly, without the write-only carve-out ADR-031 later needed for `eebus:oh-eg-entity`.

A dedicated convenience Bridge (an `oh-mpc-device`, mirroring the now-removed `eebus:oh-cs-device` from ADR-023) was not considered: ADR-027 already established that an Entity Thing type's mere presence is sufficient to guarantee its Use Case is active, making a dedicated Bridge unnecessary for any new role.

Naming was confirmed with the user via `$Concept`/`$Spec`: `oh-mpc-entity`, using the use-case abbreviation `mpc` directly as the role segment (unlike `cs`/`eg`, MPC's client actor - "Monitoring Appliance" - has no separate short role name in the catalogue).

## Decision

### 1. New `eebus:oh-mpc-entity` Thing type, additive child of `eebus:oh-device`

Same non-exclusive relationship `eebus:oh-eg-entity` has: `eebus:oh-device`'s `supported-bridge-type-refs` set is unchanged, and nothing restricts which Entity Thing type is used alongside it. `eebus:oh-entity`, `eebus:oh-cs-entity`, `eebus:oh-eg-entity`, and `eebus:oh-mpc-entity` are all equally valid children of `eebus:oh-device` at the same time.

### 2. `mpc` Channel Group declared statically

`eebus:oh-mpc-entity` declares:

```xml
<channel-groups>
    <channel-group id="mpc" typeId="mpc"/>
</channel-groups>
```

reusing the existing `mpc`/`mpc-power` `channel-group-type`/`channel-type` from ADR-014 unchanged. The `mpc#power` Channel exists from Thing creation, at its default (`NULL`) state, rather than waiting for `EEBusMpcClientUseCase` to resolve a first measurement.

### 3. Same config and handler classes as the other three Entity Thing types - no duplication

`eebus:oh-mpc-entity` reuses `EEBusOhEntityConfiguration` (`ski`, `entityAddress`, `shipId`) and `EEBusOhEntityHandler` (identical trust-status derivation, identical `applyMpcPower` method) rather than introducing parallel classes - the same reuse pattern ADR-025/ADR-026 already established. `EEBusOhEntityHandler#ensureChannel` already no-ops when the target Channel exists, so `applyMpcPower`'s channel-creation call simply finds the statically declared Channel pre-existing and updates it.

### 4. Bridge Use-Case derivation gains one branch

`EEBusHandler#deriveLocalUseCases` (docs/ADR/027) gains `else if (THING_TYPE_OH_MPC_ENTITY.equals(childType)) { clientUseCaseKeys.add("MPC"); }`, alongside the existing `oh-cs-entity`/`oh-eg-entity` branches. An `eebus:oh-mpc-entity` child's mere presence is therefore sufficient by itself to guarantee `MPC` Client is active locally, with no checkbox to forget - closing the same gap for MPC that ADR-027 already closed for LPC/LPP.

### 5. `entityAddress` retained

Same field, same not-yet-wired-into-transport-routing status, same rationale as every other Entity Thing type (ADR-024 "Out of scope", carried forward by ADR-025/026).

## Consequences

### Positive

- `eebus:oh-mpc-entity`'s `mpc#power` Channel is visible immediately after the Thing is created, rather than only after the first successful measurement resolution - matching the "always MPC" guarantee the Thing type itself now makes.
- Zero code duplication: reusing `EEBusOhEntityConfiguration`/`EEBusOhEntityHandler` keeps the actual behavior in exactly one place, consistent with every prior Entity Thing type.
- `eebus:oh-device`/`eebus:oh-entity` stay exactly as generic as before - this is purely additive, not a replacement, matching the non-destructive pattern every prior Thing-type addition in this binding has followed.
- Closes the last asymmetry among the three implemented Use Cases (MPC, LPC, LPP): all three now have a dedicated, additive, statically-Channel-declaring (where applicable) Entity Thing type alongside the generic `eebus:oh-entity`.

### Negative

- A fourth Entity Thing type now exists (`oh-entity`, `oh-cs-entity`, `oh-eg-entity`, `oh-mpc-entity`), each with its own `thing-types.xml` declaration and Main UI "Add Thing" entry - a further small increase in surface area, documented in the shared handler class javadoc to keep it discoverable, same approach as ADR-025/026.
- `eebus:oh-mpc-entity`'s Channel exists even before any MPC measurement has been resolved (e.g. immediately after trust, before a peer's MPC Server actor has been detected) - it simply reads `NULL` until then, same as any Channel before its first update; no functional risk, just something a user should expect rather than be surprised by (same trade-off ADR-025 already accepted for `oh-cs-entity`).
- The new Thing type's `ski`/`entityAddress`/`shipId` `description` text was written to comply with `rules/thing-types-content-rules.md` (short, no ADR references) more strictly than the three existing sibling Thing types' text, which predates that rule being applied this strictly. This is a pre-existing inconsistency across the four Entity Thing types, not introduced or worsened by this change - left as-is, out of scope to retrofit the other three here.

## Migration

None needed. This is purely additive: no existing Thing type's `supported-bridge-type-refs` or behavior changes. An existing `eebus:oh-entity` Thing configured for MPC Client continues to work unchanged; a user who wants the statically-declared Channel instead may create a new `eebus:oh-mpc-entity` Thing alongside or in place of it.

---

_Applies docs/ADR/025-oh-cs-entity-static-channels.md's static-Channel pattern, as already extended to the Client-role side by docs/ADR/026-oh-eg-entity-static-channels.md, to MPC Client - refines docs/ADR/014-dynamic-client-role-channels.md, docs/ADR/025, docs/ADR/026 and docs/ADR/027-derive-local-use-cases-from-entities.md, supersedes none of them._
