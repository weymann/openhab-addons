# Proposal: `oh-eg-entity` Thing type with statically declared LPC/LPP Channels

## Intent

Direct follow-up to docs/ADR/025-oh-cs-entity-static-channels.md: the user asked for the
EnergyGuard-side analog of `eebus:oh-cs-entity` - "Das oh-cs-device müsste jetzt eine oh-cs-entity
werden, mit config daten und vordefinierten channels für die LPC/LPP CS use cases" was the
original CS-side request; this proposal is "analog zu LPC/LPP CS den EnergyGuard LPC/LPP EG als
oh-eg-entity" - a dedicated `eebus:oh-eg-entity` Thing type with pre-defined Channels for the
LPC/LPP EnergyGuard (Client role) use cases.

One scoping question was resolved via `AskUserQuestion` before implementation: since
`eebus:oh-cs-entity`'s static-Channel design leans on `eebus:oh-cs-device` guaranteeing LPC+LPP
is active (no equivalent guarantee exists for the Client role today - LPC/LPP Client-role
construction is checkbox-gated on the fully generic `eebus:oh-device`, see ADR-026 Context),
should a new `eebus:oh-eg-device` convenience Bridge be built too, full mirror of ADR-023+ADR-025
with `eebus:oh-eg-entity` exclusive under it? **The user explicitly declined this** (chose "no new
Bridge - `oh-eg-entity` under the existing `oh-device`" over the recommended full-mirror option).

## Scope

In scope:

- New Thing type `eebus:oh-eg-entity` ("EEBus OH Energy Guard Entity"), an additional
  (non-exclusive) child of `eebus:oh-device` alongside the existing `eebus:oh-entity`.
- `eebus:oh-eg-entity` declares its `lpc`/`lpp` Channel Groups statically
  (`limit-active`/`limit-value`/`failsafe-limit-value`/`failsafe-duration-minimum`, the existing
  `lpc`/`lpp` `channel-group-type` definitions, unchanged) - present immediately at Thing
  creation, not created on first EEBus event. Same mechanism as `eebus:oh-cs-entity`
  (docs/ADR/025), reused verbatim.
- `eebus:oh-eg-entity` reuses `EEBusOhEntityConfiguration` (`ski`, `entityAddress`, `shipId`) and
  `EEBusOhEntityHandler` unmodified - no new Java classes. `EEBusHandlerFactory` gains one new
  branch constructing the same `EEBusOhEntityHandler` for the new Thing-type UID.
- `EEBusSkiOptionProvider` extended so `eebus:oh-eg-entity`'s `ski` parameter gets the same
  selectable-trusted-SKIs behavior `eebus:oh-entity`/`eebus:oh-cs-entity` already have; its
  `targetBridgeUid` context-type recognition extended to include `eebus:oh-eg-entity` (the
  Bridge-type side, `eebus:oh-device`, already worked - it is the same Bridge `eebus:oh-entity`
  already resolves to).
- Documentation: this proposal, `docs/ADR/026-oh-eg-entity-static-channels.md`, README.md
  (Supported Things / Trust / Channels sections), CONCEPT.md (new decided-subsection + §7
  checklist item).

Out of scope:

- A new `eebus:oh-eg-device` convenience Bridge - explicitly declined by the user via
  `AskUserQuestion` (see "Intent" above and ADR-026's Context).
- Any change to `eebus:oh-device`'s `supported-bridge-type-refs`, `eebus:oh-entity`'s Thing type,
  or the dynamic-Channel behavior of the generic `eebus:oh-device`/`eebus:oh-entity` pairing
  (docs/ADR/014, docs/ADR/015) - `eebus:oh-entity` remains equally valid and unaffected under
  `eebus:oh-device`.
- Any change to `eebus:oh-cs-device`/`eebus:oh-cs-entity` (docs/ADR/023, docs/ADR/025) -
  unaffected by this change.
- Enforcing or validating that a `eebus:oh-eg-entity` Thing's parent `eebus:oh-device` actually
  has `LPC`/`LPP` enabled in `supportedUseCasesClient` - not possible to express in
  `thing-types.xml` given `eebus:oh-device`'s generic/checkbox-driven design; documented as a
  known consequence in ADR-026 instead.
- Wiring `entityAddress` into transport-layer per-Entity routing - same deferred scope as
  docs/ADR/024/025, carried forward unchanged.
- Any change to `AbstractEEBusLimitEnergyGuardUseCase`'s or
  `AbstractEEBusLimitControllableSystemUseCase`'s own logic - both already resolve and call into
  whatever `EEBusOhEntityHandler` their respective resolver returns, regardless of which Thing
  type that handler's Thing has; no changes needed there.

## Open Questions

None blocking - the one open fork (dedicated convenience Bridge or not) was resolved via
`AskUserQuestion` above before implementation started.

---
