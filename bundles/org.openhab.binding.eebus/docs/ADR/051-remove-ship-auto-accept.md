# ADR-051: Remove the SHIP Auto-Accept Option

## Status

Accepted

## Context

`EEBusHandler#startShipSpineLocked()` passed the Bridge configuration parameter
`autoAcceptEnabled` to `org.openmuc.jeebus.ship.api.ConfigBuilder#withAutoAcceptEnabled(boolean)`
(docs/ADR/049-configbuilder-ship-node-construction.md). That method is deprecated since
`ship` 3.0.0, which produced a compiler deprecation warning on every build.

The deprecation note in `ConfigBuilder` states that auto-accept is discouraged by the EEBus
Initiative and most stack implementers, that even one device in auto-accept mode makes EEBUS
networks hard to set up and unreliable, and that it may be removed in future SHIP specification
versions.

`ship` 3.2.1 offers no non-deprecated replacement: the only other entry point,
`Ship#setAutoAcceptMode()`, is deprecated since 2.3.0 and is not reachable from this binding
anyway (`ShipCommunication` owns the `Ship` instance). The parameter defaulted to `false`, and
all documented test procedures (docs/TEST_PAIRING.md) already required it to stay `false`.

## Decision

Remove the auto-accept option from the binding:

- `EEBusConfiguration#autoAcceptEnabled` is removed.
- The `autoAcceptEnabled` parameter is removed from the Bridge's config description in
  `thing-types.xml`.
- `startShipSpineLocked()` no longer calls `withAutoAcceptEnabled(...)`; `ConfigBuilder`'s own
  default (`false`) applies.

Trust is established exclusively via the trusted SKI list.

## Alternatives Considered

- **Keep the call and suppress the warning** - rejected: the user asked for no suppressions,
  and it keeps a mechanism the stack vendor discourages and may drop.
- **Keep the call, accept the warning** - rejected: a permanent build warning for an option
  that is off by default and unused in every documented test.

## Consequences

### Positive

- No deprecation warning; no dependency on an API that may disappear in a future `ship` release.
- Follows the EEBus Initiative recommendation; one less way to misconfigure pairing.

### Negative

- Existing Bridges that set `autoAcceptEnabled=true` lose that behavior. openHAB ignores the now
  unknown configuration key; those peers must be added to the trusted SKI list instead.
- docs/CONCEPT.md still describes auto-accept as a v1 option (§6 decision 4); this ADR
  supersedes that decision.

## Follow-up

- Run `mvn i18n:generate-default-translations` so the removed parameter's label/description keys
  disappear from the generated default translation file.
