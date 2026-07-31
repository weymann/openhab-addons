# ADR-023: eebus:cs-service - a convenience Bridge for the Controllable System role

## Status

> Superseded by ADR-027

## Context

Following ADR-022 (read-only failsafe status Channel, startup/reconnect sync), the user asked
whether a "convenience Thing" for the Controllable System (CS) role should also get its own new
Thing type - previously left undecided ("Convenience Thing idea ... discussed, NOT decided/
scoped yet", project memory). Four scoping questions were asked and answered (`$Concept`,
2026-08-23):

1. **What does it represent?** The user corrected the initially proposed "per-peer Thing" framing:
   it has to be a **Bridge**. `supportedUseCasesServer` (which use cases a local SPINE Entity
   offers) is inherently a property of the one local SHIP/SPINE identity a Bridge owns - only
   `eebus:service` (and now `eebus:cs-service`) hold a local `Device`/`Entity` at all;
   `eebus:oh-peer` is just a pairing record for a remote peer, it never offers use cases itself.
   A "pre-selected LPC/LPP Server use cases" Thing is therefore structurally a Bridge, exactly
   like `eebus:service` - not a new kind of peer.
1. **Relationship to the checkbox `supportedUseCasesServer`?** Structurally exclusive - achieved
   simply by being a separate `ThingTypeUID` with its own `thing-types.xml` config-description
   that does not declare `supportedUseCasesClient`/`supportedUseCasesServer` at all, rather than
   by runtime validation logic on a single shared Bridge type.
1. **Do Thing-config edits push at runtime?** No - seed-only. A Thing-config change only takes
   effect at the next Bridge (re-)initialization, consistent with ADR-022's "only EEBus, never
   openHAB, changes the running failsafe value" decision.
1. **LPC and LPP together, or selectable?** Together - matches
   `AbstractEEBusLimitControllableSystemUseCase`'s existing sibling-subclass design (LPC/LPP
   already share one local `LoadControl` feature, ADR-018/019).

The existing checkbox `eebus:service`/`eebus:oh-peer` model stays exactly as-is, per the
standing decision from the ADR-022 discussion - `eebus:cs-service` is purely additive.

## Decision

**New Bridge Thing type `eebus:cs-service`** ("EEBus Controllable System Service"), sibling to
`eebus:service`: its own local SHIP/SPINE identity (own certificate/SKI, same 1:1-exclusive
pattern), same pairing mechanism (child `eebus:oh-peer` Things, unchanged). Its
`thing-types.xml` config-description carries the same infrastructure parameters as `eebus:service`
(vendor/device/serial/mDNS-name/port/autoAccept/connectToPeers/preferIpv4) but omits
`supportedUseCasesClient`/`supportedUseCasesServer` entirely, and adds three new parameters:
`failsafeConsumptionLimitSeedWatts`, `failsafeProductionLimitSeedWatts`,
`failsafeDurationMinimumSeedSeconds`.

**Reuse, not duplicate.** `EEBusHandler` serves both Bridge types - the only place their behavior
actually differs is server-use-case construction, which now branches on
`thing.getThingTypeUID()`: `eebus:cs-service` unconditionally constructs `EEBusLpcServerUseCase`/
`EEBusLppServerUseCase` with the Bridge's own Thing-config seed values; `eebus:service` keeps its
exact prior checkbox-driven logic, now passing the previously-implicit `0.0`/
`DEFAULT_FAILSAFE_DURATION_MINIMUM_SECONDS` defaults explicitly instead of relying on field
initializers. Client-role use-case construction needed no change at all -
`cfg.supportedUseCasesClient` is naturally empty for `eebus:cs-service` since its
`thing-types.xml` doesn't declare that parameter, so `EEBusConfiguration`'s single POJO can serve
both Bridge types without a second Configuration class.

**Constructor change, propagated through three classes.**
`AbstractEEBusLimitControllableSystemUseCase`'s constructor gains
`initialFailsafeLimitWatts`/`initialFailsafeDurationMinimumSeconds`, used to seed
`lastFailsafeLimitWatts`/`failsafeDurationMinimumSeconds` (previously a field initializer
constant); `setupDeviceConfiguration`'s initial SPINE-visible failsafe value now comes from
`lastFailsafeLimitWatts` instead of a hardcoded `0.0`, so the wire-visible value and the tracking
field are seeded consistently from the same source. `EEBusLpcServerUseCase`/
`EEBusLppServerUseCase` pass the two new parameters through unchanged.
`DEFAULT_FAILSAFE_DURATION_MINIMUM_SECONDS` was widened from `private` to `public` so
`EEBusHandler` (a different package) can reference it explicitly for the checkbox path, keeping
that default defined in exactly one place.

**No changes needed to the ADR-022 failsafe Channels.** `EEBusOhPeerHandler#applyFailsafeStatus`
and the `failsafe-limit-value`/`failsafe-duration-minimum` Channel types are agnostic to which
Bridge type constructed the `AbstractEEBusLimitControllableSystemUseCase` instance feeding them -
they populate identically under `eebus:cs-service` as under a checkbox-configured
`eebus:service`.

## Consequences

### Positive

- A user setting up the single most common real scenario (openHAB as Controllable System,
  receiving limits from an external Energy Guard/SMGW) now has a purpose-built Bridge type: add
  Thing, fill in a handful of fields including two failsafe numbers, done - no checkbox list to
  search through, no Item to create and tag for the failsafe configuration.
- Zero duplication of the actual CS logic - `EEBusHandler`,
  `AbstractEEBusLimitControllableSystemUseCase`, `EEBusLpcServerUseCase`/`EEBusLppServerUseCase`,
  and the ADR-022 Channels are all shared, unchanged in behavior, between both Bridge types.
- Structurally impossible to have the checkbox and convenience configuration paths conflict on
  the same local Entity - they are different Bridge Things with different local SPINE identities
  by construction, not a shared mutable Bridge with two configuration sources.
- Consistent, safety-preserving semantics: Thing-config seed values never race with or override a
  real Energy Guard's live SPINE writes - exactly the same guarantee ADR-022 already established
  for the read-only Channels.

### Negative

- A third Bridge Thing type to explain to users (`eebus:service` vs. `eebus:cs-service` vs. the
  bridgeless `eebus:eebus-peer`/`eebus:oh-peer` pair) - README/CONCEPT.md need to make the choice
  between `eebus:service` and `eebus:cs-service` clear (full flexibility vs. CS-only convenience).
- `AbstractEEBusLimitControllableSystemUseCase`'s constructor signature changed again (third time
  after ADR-021/ADR-022) - `EEBusLpcServerUseCase`/`EEBusLppServerUseCase` and both `EEBusHandler`
  call sites needed updating in lockstep, same pattern as ADR-021. No external/API compatibility
  concern (all `internal` package).
- Inherits the pre-existing "only the first detected Energy Guard partner is tracked"
  simplification unchanged - `eebus:cs-service` does not solve or claim to solve multi-Energy-
  Guard support.
- Not yet compiled (no Maven in the editing sandbox, same limitation as every ADR in this series)
  or live-retested - reviewed manually only (brace/paren balance checked programmatically per
  touched `.java` file, `thing-types.xml` re-parsed for well-formedness, CRLF preserved
  throughout, single-occurrence-verified find/replace for every edit).

## Post-acceptance fix (2026-08-23)

Live testing (user, same day) showed a new `eebus:cs-service` Bridge came online correctly
but exposed **no Channels at all** - expected for the Bridge itself (Channels live on the
paired `eebus:oh-peer` Thing, per ADR-021/ADR-022, not the Bridge), but no `eebus:oh-peer`
could be added under it either. Root cause: `oh-peer`'s `<supported-bridge-type-refs>` in
`thing-types.xml` only listed `<bridge-type-ref id="service"/>`, an oversight from the
original implementation - `cs-service` was never added alongside it, so the framework
refused to let an `oh-peer` be created under a `cs-service` parent. `EEBusOhPeerHandler`
itself was already bridge-type-agnostic (casts its bridge's handler to `EEBusHandler`
without checking the specific Bridge Thing type), so the fix is XML-only: added
`<bridge-type-ref id="cs-service"/>` next to the existing `service` entry. Re-verified
`thing-types.xml` well-formedness after the change.

## Verification status

Not yet verified: `mvn clean install`, and a live retest adding an `eebus:cs-service` Bridge,
pairing a real or self-built Energy Guard against it, confirming the Thing-config failsafe seed
values are used at first startup, confirming a later Thing-config edit does not push immediately
(only takes effect on the next restart), and confirming the ADR-022 Channels populate identically
to the checkbox-configured path - all user-owned, same pattern as every prior ADR.

---

_Superseded in full by docs/ADR/027-derive-local-use-cases-from-entities.md (2026-08-24):_ _`eebus:oh-cs-device` is removed - a plain `eebus:oh-device` Bridge with an `eebus:oh-cs-entity`_ _child now gets the identical LPC/LPP Server behavior for free, once the local Use-Case set is_ _derived from Entity children instead of a Bridge checkbox. The three failsafe seed Thing-config_ _parameters this ADR introduced move onto `eebus:oh-cs-entity` itself - see ADR-027 Decision 3._
