# Proposal: Dedicated `oh-mpc-entity` Thing type with statically declared MPC Channel

## Intent

Follow-up to `docs/ADR/025-oh-cs-entity-static-channels.md` and `docs/ADR/026-oh-eg-entity-static-channels.md`, applying the same static-Channel convenience pattern to the MPC Client role (CONCEPT.md 5.4.1: "Monitoring Appliance" - openHAB reading a paired peer's total power).

Today, MPC Client data (`mpc#power`) is only ever available on the fully generic `eebus:oh-entity` Thing (`docs/ADR/014-dynamic-client-role-channels.md`): a user must tick `MPC` in that Thing's `supportedUseCasesClient` config, and the Channel itself is created dynamically, only after the first successful measurement resolution - it does not exist immediately after the Thing is created. This mirrors the exact situation ADR-025 addressed for the Controllable System (LPC/LPP Server) side: no "maybe" to gate on once a user has deliberately chosen a Thing type whose entire purpose is MPC.

Since `docs/ADR/027-derive-local-use-cases-from-entities.md`, a dedicated Thing type's mere presence under a Bridge is already how `eebus:oh-cs-entity` (implies LPC+LPP Server) and `eebus:oh-eg-entity` (implies LPC+LPP Client) each guarantee their Use Case is active, with no Bridge-level checkbox and no dedicated convenience Bridge type. This proposal applies the identical, now-established pattern to MPC Client: a new `eebus:oh-mpc-entity` Thing type, additive under the generic `eebus:oh-device` Bridge, statically declaring the `mpc` Channel Group and unconditionally contributing `MPC` Client to the Bridge's derived local Use-Case set.

## Scope

In scope:

- New Thing type `eebus:oh-mpc-entity`, additive (non-exclusive) child of `eebus:oh-device` - `eebus:oh-entity` remains equally valid alongside it, exactly as `eebus:oh-eg-entity` coexists with `eebus:oh-entity` today. No dedicated convenience Bridge is introduced (superseded by ADR-027 - a dedicated Bridge was only ever needed to avoid a checkbox, and Entity-implies-Use-Case already does that).
- `eebus:oh-mpc-entity` declares the existing `mpc` Channel Group (`mpc#power`, `Number:Power`, read-only - the same `channel-group-type`/`channel-type` ADR-014 already defined) statically via `<channel-groups>`, present on every instance from Thing creation.
- `eebus:oh-mpc-entity` reuses `EEBusOhEntityConfiguration` (`ski`/`entityAddress`/`shipId`) and `EEBusOhEntityHandler` unmodified - no new Java classes, same reuse pattern as `oh-cs-entity`/`oh-eg-entity`.
- `EEBusHandler#deriveLocalUseCases` gains one new branch: an `eebus:oh-mpc-entity` child unconditionally contributes `MPC` Client to the Bridge's derived local Use-Case set - the same mechanism that already handles `oh-cs-entity`/`oh-eg-entity`, no checkbox involved.
- `EEBusSkiOptionProvider` extended so `eebus:oh-mpc-entity`'s `ski` parameter gets the same selectable-trusted-SKIs behavior `oh-entity`/`oh-cs-entity`/`oh-eg-entity` already have.
- Documentation: this proposal, a new ADR (next available number), README.md (Supported Things / Channels sections), CONCEPT.md (new decided-subsection + 7 checklist entry).

Out of scope:

- Any change to `EEBusMpcClientUseCase`'s detection/subscription/resolution logic - it already applies its result through whatever `EEBusOhEntityHandler` the resolver returns, unaffected by that handler's Thing type (identical precedent: `AbstractEEBusLimitControllableSystemUseCase` needed no change for `oh-cs-entity`).
- Any change to the generic `eebus:oh-entity`/dynamic-Channel MPC path (ADR-014) - it remains available for a mixed/any-use-case Entity; this proposal only adds an alternative, not a replacement.
- Additional MPC Scenarios/data points beyond Scenario 1 (Total Active Power) - phase-specific power, energy, current, voltage, frequency remain out of scope per CONCEPT.md 5.4.3, unchanged by this proposal.
- A dedicated `oh-mpc-device` convenience Bridge - not needed under the ADR-027 model; would only reintroduce the dedicated-Bridge pattern ADR-027 deliberately removed.
- Wiring `entityAddress` into transport-layer per-Entity routing - same deferred scope as every other Entity Thing type (ADR-024 "Out of scope"), carried forward unchanged.

## Open Questions

- ~~Naming: `oh-mpc-entity`...~~ **Resolved via `AskUserQuestion`:** `oh-mpc-entity` confirmed by the user, locked into `docs/ADR/036-oh-mpc-entity-static-channels.md`.
- ~~Exact Thing-type label/description wording...~~ **Resolved by `$Architect`:** written per `rules/thing-types-content-rules.md` (see ADR-036 "Negative" for a noted pre-existing inconsistency with the three sibling Thing types' older, more verbose descriptions).

---
