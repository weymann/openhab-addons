# ADR-013: ConfigOptionProvider for eebus:oh-peer's ski Parameter

## Status

> Accepted

## Context

CONCEPT.md §4.5 (point 2) and the "eebus:oh-peer ski parameter offers known SKIs as selectable
options" requirement in the `thing-model` delta spec of the
`decouple-oh-peer-config-from-pairing` change call for replacing free-text SKI entry with a
selectable option list, sourced from two places: known `eebus:peer` Things (real, mDNS-discovered
devices) and other local
`eebus:service` Bridges' own SKI (for pairing two local services with each other, CONCEPT.md
§4.4 — these are excluded from normal mDNS-based Inbox discovery entirely by ADR-008's
`isOwnService` filter, so no other source offers them). This is the first use of
`org.openhab.core.config.core.ConfigOptionProvider` in this binding.

## Decision

Add `EEBusOhPeerSkiOptionProvider`, an `@Component`-registered `ConfigOptionProvider`, in
`internal.handler` (alongside `EEBusOhPeerHandler`/`EEBusOhPeerConfiguration` — this is
Thing/config-layer UI behavior, not SPINE/SHIP transport, per the `internal.transport`/
`internal.handler` split in ADR-002). It receives `ThingRegistry` via constructor injection (per
the dependency-injection rule in `rules/java-coding-rules.md`) and implements
`getParameterOptions(URI, String, String, Locale)`:

- Returns `null` (defer to static XML options / free text) unless the URI matches
  `thing-type:eebus:oh-peer` and the parameter name is `ski`.
- Otherwise builds `ParameterOption`s from:
  - every `eebus:peer` Thing's `ski` plus its discovered name, and
  - every other `eebus:service` Bridge's `localSki` property plus its Thing label (excluding the
    target Thing's own parent Bridge — pairing a service with itself is not a meaningful
    option).
- Excludes any SKI that already belongs to a sibling `eebus:oh-peer` Thing under the same target
  parent Bridge (avoids offering a duplicate pairing), but keeps SKIs already paired under a
  _different_ Bridge (a device or service may legitimately be paired with more than one local
  service).
- Does not set `limitToOptions` — the parameter remains free-text-capable, so a SKI not yet seen
  via discovery (e.g. read from a device's label) can still be entered manually.

## Consequences

### Positive

- No `thing-types.xml` change needed beyond a description update — `ConfigOptionProvider` is
  additive to the existing `ski` parameter; openHAB's config framework merges dynamic and static
  options.
- Establishes the pattern for any future dynamic config option in this binding (currently
  `supportedUseCasesClient`/`supportedUseCasesServer` are static XML `<option>` lists — an
  obvious future candidate once client/server use-case detection, CONCEPT.md §5.4/§5.5, is
  implemented, but out of scope here).
- Read-only, in-memory `ThingRegistry` query — no I/O, no new failure mode to handle at runtime
  (unlike, say, a network call).

### Negative

- A second, small OSGi service to register in addition to `EEBusHandlerFactory`/
  `EEBusDiscoveryService` — more moving parts than a purely static XML option list.
- The option list is only as fresh as `ThingRegistry`'s current state at the moment Main UI
  renders the config form — a `eebus:peer` Thing discovered a second after the form opens will
  not appear until the form is reopened. Existing openHAB behavior for all
  `ConfigOptionProvider`s, not specific to this binding; not solved here.

---

_Implements CONCEPT.md §4.5 (point 2) and the "eebus:oh-peer ski parameter offers known SKIs as
selectable options" requirement in
`docs/changes/decouple-oh-peer-config-from-pairing/specs/thing-model/spec.md`._
