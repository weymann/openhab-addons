# ADR-044: Configurable `deviceType` on the `oh-device` Bridge

## Status

Accepted

## Context

Following ADR-043's topology phase gate finally letting a previously-never-connected Bridge
(`energy-guard-sim`) attempt a real connection, a live crash surfaced: `IllegalArgumentException:
Use case EEBusLpcClientUseCase does not allow entity type GRID_GUARD!` - the Bridge's shared
local Entity was configured `entityType=GridGuard` (docs/ADR/042-configurable-entitytype.md), a
value the EnergyGuard actor's own Use Case classes did not yet actually allow at runtime (fixed
separately, see the 2026-09-04 update to ADR-042).

While diagnosing that, the user asked whether `deviceType` - `EEBusHandler#startShipSpineLocked()`
has always hardcoded `DeviceTypeEnumType.ENERGY_MANAGEMENT_SYSTEM` for the local `Device` it
builds - needed to change alongside `entityType` to "support" `GridGuard`. Checked, confirmed no:

- `deviceType` (`DeviceTypeEnumType`) and `entityType` (`EntityTypeEnumType`) are independent
  SPINE classification axes on two different objects - `Device` vs. `Entity`.
- `jeebus.spine`'s `EntityImpl.addFeaturesForUseCase` (the method that threw above) reads only
  the Entity's own `getType()`; it never touches the owning `Device`'s `deviceType` at all.
- There is no `AllowedDeviceTypes`-style annotation or equivalent restriction anywhere in
  `jeebus.spine` tying a `Device`'s `deviceType` to which Use Cases/entity types it may host -
  confirmed by reading `jeebus.spine`'s `impl` package; the only other places `DeviceTypeEnumType`
  appears are detailed-discovery reporting and a couple of discovery-selector filter comparisons,
  none of which gate anything at connection/build time.
- The two real discovery captures on hand illustrate the same independence: the real Hager S10
  (`hagers10.json`) reports `deviceType: "Generic"` while hosting several `CEM`-typed
  `EnergyGuard`-actor entities; this binding's own simulated EnergyGuard device
  (`GridGuard.json`, `d:_n:OPHAB-Energy Guard-0001`) already reports `deviceType:
  "EnergyManagementSystem"` - matching the existing hardcoded value - regardless of which
  `entityType` its own Entity ends up using.

Given `deviceType` is confirmed to be purely descriptive/self-reported metadata with no
functional coupling to Use Case legality, the user asked to make it configurable too, following
the same precedent ADR-042 already set for `entityType`.

## Decision

Add a `deviceType` config parameter to `EEBusConfiguration` (the `oh-device` Bridge's own config
class - unlike `entityType`, this is a single Bridge-level property with no multi-child
derivation question, since exactly one `Device` is built per Bridge), default
`"EnergyManagementSystem"` (matching the previously-hardcoded value, so existing behavior is
unchanged unless a user explicitly opts in). `thing-types.xml`'s `oh-device` bridge-type gets a
matching `advanced` dropdown listing all 14 `DeviceTypeEnumType` constants (`EnergyManagementSystem`
listed first, then alphabetical - mirroring `oh-mpc-entity`/`oh-entity`'s existing exhaustive
`entityType` dropdown style from ADR-042), since - as established above - none of them are
actually restricted here.

`EEBusHandler` gets a new `resolveDeviceType(EEBusConfiguration cfg)` helper, called from
`startShipSpineLocked()` in place of the hardcoded constant
(`Device.getBuilder().withDeviceType(resolveDeviceType(cfg))...`). Parses `cfg.deviceType` via
the generated `DeviceTypeEnumType.fromValue(...)`, falling back to `ENERGY_MANAGEMENT_SYSTEM` with
a WARN log for a blank or unrecognized string - the same fallback pattern
`deriveLocalUseCases()` already uses for `entityType`.

## Consequences

### Positive

- Closes the asymmetry the GridGuard investigation surfaced: `entityType` was configurable per
  ADR-042 but `deviceType` was still hardcoded, even though nothing actually requires it to be.
- Zero behavior change for existing installations: default matches the old hardcoded value
  exactly.
- No `jeebus.ship`/`jeebus.spine` change - confined to `EEBusConfiguration.java`,
  `EEBusHandler.java`, and `thing-types.xml`.
- Simpler than ADR-042's `entityType` mechanism: no cross-child "first non-default wins"
  derivation logic needed, since `deviceType` belongs to exactly one place (the Bridge itself),
  not to multiple children that could disagree.

### Negative

- **Not yet compiled or live-tested** - no local Maven/JDK in this environment, as with every
  other change in this project; verified only by manual review (brace/paren balance, targeted
  anchor-based edits, XML well-formedness check via `xml.etree.ElementTree`).
- Like `entityType`, no validation that the configured `deviceType` makes sense for what this
  Bridge's derived Use Cases/actors actually represent - it is purely descriptive/self-reported
  and, per this ADR's own Context section, not functionally enforced anywhere in `jeebus.spine`
  either. A nonsensical choice (e.g. `Dishwasher` for an EnergyGuard Bridge) would not fail, it
  would just misreport itself to peers via detailed discovery.
