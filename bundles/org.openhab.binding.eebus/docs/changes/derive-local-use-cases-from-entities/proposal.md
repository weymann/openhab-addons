# Proposal: Derive Local Use Cases From Entity Children

## Intent

`eebus:oh-device`'s `supportedUseCasesClient`/`supportedUseCasesServer` checkbox config
duplicates intent that `eebus:oh-cs-entity`/`eebus:oh-eg-entity` already express structurally
(which Use Cases a pairing involves). Move that configuration onto the Entity children
(`eebus:oh-entity` gains the checkboxes; `eebus:oh-cs-entity`/`eebus:oh-eg-entity` keep implying
their fixed Use Cases by Thing-type choice alone), derive the Bridge's local SPINE `Device` from
whichever children currently exist, and - since `eebus:oh-cs-device` (ADR-023) only ever existed
to avoid that same checkbox - remove it. Unify how any configuration change (Bridge-level or
child-level, trust included) takes effect: always a full teardown-and-rebuild of the local
`Device`, never a live in-place update. See docs/ADR/027-derive-local-use-cases-from-entities.md
for the full rationale and the `jeebus.spine` source reading that established this is safe
without any change to that protected project.

## Scope

In scope:

- Remove `eebus:oh-cs-device` (thing-types.xml, `EEBusBindingConstants`, `EEBusHandlerFactory`,
  `EEBusMdnsDiscoveryParticipant#isOwnService`, `EEBusSkiOptionProvider`).
- Add `supportedUseCasesClient`/`supportedUseCasesServer` to `eebus:oh-entity`
  (thing-types.xml, `EEBusOhEntityConfiguration`).
- Add `failsafeConsumptionLimitSeedWatts`/`failsafeProductionLimitSeedWatts`/
  `failsafeDurationMinimumSeedSeconds` to `eebus:oh-cs-entity` (thing-types.xml,
  `EEBusOhEntityConfiguration`); remove them from `EEBusConfiguration`.
- Change `eebus:oh-cs-entity`'s `supported-bridge-type-refs` from exclusive-under-`oh-cs-device`
  to additive-under-`oh-device`.
- `EEBusHandler#initialize()` derives the local Use-Case list by scanning current children
  instead of reading Bridge config.
- New `EEBusEntityChangeListener` interface; `EEBusHandler` implements it; `EEBusOhEntityHandler`
  calls it via `getBridge()` in `initialize()`/`dispose()`.
- Remove `EEBusDeviceActions` (`trust()`/`untrust()` Thing Actions) and
  `EEBusHandler#trust(String)`/`untrust(String)`/`persistTrustedSkis(List)`/
  `recomputeTrustedSkis()`.
- README.md: document that every configuration change now uniformly triggers an automatic full
  rebuild, briefly dropping and reconnecting all peers, with disabling the Bridge first as an
  optional way to batch several changes into one rebuild.
- CONCEPT.md: record the decision, superseding/amending the relevant sections.

Out of scope:

- Any change to `jeebus.ship`/`jeebus.spine` (confirmed unnecessary - see ADR-027 Context).
- Per-Entity routing via `entityAddress` (still out of scope, unchanged from ADR-024).
- A dedicated `eebus:oh-eg-device` convenience Bridge (still explicitly declined, ADR-026
  unaffected).
- Any change to MPC handling, Client-role Use-Case detection wiring, or Channel Group
  definitions themselves.

## Open Questions

- None outstanding - all scoping questions were resolved in the `$Concept` discussion this
  proposal follows from.

---
