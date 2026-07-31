# Proposal: Dedicated `oh-cs-entity` Thing type with statically declared LPC/LPP Channels

## Intent

Follow-up refinement identified right after ADR-024 shipped: `eebus:oh-cs-device` (docs/ADR/023-
cs-service-convenience-bridge.md), the LPC/LPP-Controllable-System convenience Bridge, currently
takes the same generic `eebus:oh-entity` Thing type as the fully generic `eebus:oh-device`
Bridge does. That sharing was an incidental side effect of ADR-024's "Post-acceptance bug" fix
(widening `oh-entity`'s `supported-bridge-type-refs` to include `oh-cs-device` so a child Thing
could be created under it at all), not a deliberate design choice.

The user asked for a dedicated `oh-cs-entity` Thing type instead, with its own config and
pre-defined Channels for the LPC/LPP Controllable System use cases - since `eebus:oh-cs-device`
always offers exactly LPC+LPP by construction (no checkboxes), there is no reason for its child
Entity's Channels to wait for a first EEBus event the way the generic, use-case-agnostic
`eebus:oh-entity`'s Channels do.

Three scoping questions were resolved via `AskUserQuestion`:

1. **Exclusivity:** `eebus:oh-cs-entity` becomes the _only_ Entity Thing type `eebus:oh-cs-device`
   accepts - `eebus:oh-entity` is narrowed back to `eebus:oh-device` only, mirroring the
   `oh-device`/`oh-cs-device` Bridge-level split.
1. **Channel mechanism:** the `lpc`/`lpp` Channels are declared statically in `thing-types.xml`
   (via `<channel-groups>`), not dynamically created on first EEBus event.
1. **entityAddress:** retained on `eebus:oh-cs-entity`, for the same reasons and with the same
   not-yet-wired-into-transport-routing status as on `eebus:oh-entity`.

## Scope

In scope:

- New Thing type `eebus:oh-cs-entity` ("EEBus OH Controllable System Entity"), exclusive child of
  `eebus:oh-cs-device`.
- `eebus:oh-cs-device`'s `supported-bridge-type-refs` no longer accepted on `eebus:oh-entity`;
  `eebus:oh-entity` is narrowed back to `eebus:oh-device` only.
- `eebus:oh-cs-entity` declares its `lpc`/`lpp` Channel Groups statically
  (`limit-active`/`limit-value`/`failsafe-limit-value`/`failsafe-duration-minimum`, the existing
  `lpc`/`lpp` `channel-group-type` definitions, unchanged) - present immediately at Thing
  creation, not created on first EEBus event.
- `eebus:oh-cs-entity` reuses `EEBusOhEntityConfiguration` (`ski`, `entityAddress`, `shipId`) and
  `EEBusOhEntityHandler` unmodified - no new Java classes. `EEBusHandlerFactory` gains one new
  branch constructing the same `EEBusOhEntityHandler` for the new Thing-type UID.
- `EEBusSkiOptionProvider` extended so `eebus:oh-cs-entity`'s `ski` parameter gets the same
  selectable-trusted-SKIs behavior `eebus:oh-entity`'s already has.
- Documentation: this proposal, `docs/ADR/025-oh-cs-entity-static-channels.md`, README.md
  (Supported Things / Trust / Channels sections), CONCEPT.md (new decided-subsection + §7
  checklist item).

Out of scope:

- Any change to the generic `eebus:oh-device`/`eebus:oh-entity` pairing's dynamic-Channel
  behavior (docs/ADR/014, docs/ADR/015) - this proposal only opts the CS-specific pairing out of
  it, where the "maybe this use case never happens" reasoning behind dynamic Channels does not
  apply.
- Wiring `entityAddress` into transport-layer per-Entity routing - same deferred scope as
  docs/ADR/024-oh-device-oh-entity-rename.md's "Out of scope", carried forward unchanged.
- An automated migration path for an existing `eebus:oh-entity` Thing currently configured under
  an `eebus:oh-cs-device` Bridge - see ADR-025's "Migration" section for the manual step needed.
- Any change to `AbstractEEBusLimitControllableSystemUseCase`'s own logic - it already resolves
  and calls into whatever `EEBusOhEntityHandler` the `ohEntityHandlerResolver` returns, regardless
  of which Thing type that handler's Thing has; no changes needed there.

## Open Questions

None blocking - all three forks were resolved via the `AskUserQuestion` above before
implementation started.

---
