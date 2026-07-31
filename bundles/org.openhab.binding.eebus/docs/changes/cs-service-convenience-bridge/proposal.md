# Proposal: eebus:cs-service convenience Bridge

## Intent

Follow-up to a `$Concept` discussion about a possible additive "convenience Thing" for the
Controllable System (CS) role (2026-08-23, evcc-inspired). The user confirmed via four scoping
questions: this is a **Bridge** (it must be - it owns pre-selected LPC/LPP Server use cases,
which are attached to a local SPINE Entity/Device, and only a Bridge owns one of those today,
exactly like `eebus:service` does), structurally exclusive of the checkbox-driven
`supportedUseCasesServer` mechanism (a different Bridge Thing type entirely, not a mode switch),
with Thing-config failsafe values that only ever seed the next startup - never pushed to SPINE at
arbitrary times - and covering both LPC and LPP together, matching the existing
`AbstractEEBusLimitControllableSystemUseCase` sibling-subclass design.

The existing checkbox `eebus:service`/`eebus:oh-peer` model is explicitly untouched and remains
the full-flexibility path - this Bridge type is a pre-configured shortcut for the single most
common real scenario (openHAB as Controllable System, receiving limits from an external Energy
Guard/SMGW), not a replacement.

## Scope

In scope:

- New Bridge Thing type `eebus:cs-service` ("EEBus Controllable System Service"), sibling to
  `eebus:service` - its own local SHIP/SPINE identity (own certificate/SKI), same pairing
  mechanism (child `eebus:oh-peer` Things) as `eebus:service`.
- Always offers exactly LPC + LPP Server role, unconditionally - no `supportedUseCasesClient`/
  `supportedUseCasesServer` checkboxes in its `thing-types.xml` config-description, no MPC, no
  client-role use cases.
- Three new Thing-config parameters - `failsafeConsumptionLimitSeedWatts`,
  `failsafeProductionLimitSeedWatts`, `failsafeDurationMinimumSeedSeconds` - seed the failsafe
  values `AbstractEEBusLimitControllableSystemUseCase` already exposes (ADR-013/019/022) at every
  startup. Seed-only: a config edit takes effect on the next Thing re-initialization, never
  pushed to the running SPINE value directly - only a real Energy Guard writing over EEBus
  changes the running value, consistent with the 2026-08-23 decision behind ADR-022.
- Reuses the existing `EEBusHandler` class (same handler serves both Bridge types, branching
  internally only where behavior actually differs) and the existing
  `AbstractEEBusLimitControllableSystemUseCase`/`EEBusLpcServerUseCase`/`EEBusLppServerUseCase`
  classes (constructor gains two seed parameters, defaulted to the pre-existing behavior -
  `0.0`/`DEFAULT_FAILSAFE_DURATION_MINIMUM_SECONDS` - for the checkbox path).
- The two ADR-022 read-only Channels (`failsafe-limit-value`/`failsafe-duration-minimum`) work
  unchanged under `eebus:cs-service` - no new Channel-side work needed, they already populate
  from whatever `AbstractEEBusLimitControllableSystemUseCase` instance is running, regardless of
  which Bridge type constructed it.

Out of scope:

- Any UI/wizard beyond the standard openHAB "Add Thing" flow with the new Bridge type's
  config-description form - no custom onboarding.
- Multi-Energy-Guard support - `eebus:cs-service` inherits the pre-existing "only the first
  detected Energy Guard partner is tracked" simplification (CONCEPT.md), unchanged.
- A parallel Energy-Guard-side convenience Bridge (e.g. `eebus:eg-service`) - not asked for, not
  scoped.
- Persisting the Energy-Guard-written running value across restarts - explicitly rejected
  already (ADR-022's scope), still applies here: every restart re-seeds from Thing config.

## Open Questions

- None blocking - the four scoping questions the user answered (Bridge, not peer; structurally
  exclusive of the checkbox path via being a separate Thing type; seed-only Thing-config edits;
  LPC+LPP together) resolve the architecture. `EEBusOhPeerConfiguration`/pairing itself is
  unchanged - `eebus:cs-service` pairs child `eebus:oh-peer` Things exactly like `eebus:service`.

---
