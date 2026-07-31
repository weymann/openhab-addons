# ADR-047: Fan out EnergyGuard limit writes to every bound partner instead of one Thing per SKI

## Status

Accepted

## Context

`oh-eg-entity` (EnergyGuard actor, LPC+LPP Client role) has so far been 1:1 with a single SKI.
Its `ski` config parameter's own description: left blank, it is auto-selected once the parent
Bridge trusts exactly one device; with zero or more than one trusted device, one must be picked
manually. That forces one `oh-eg-entity` Thing per trusted partner, each with its own tagged
write-path Items (ADR-031: values flow only via tagged Items, no Channels).

Real scenario raised via `$Concept` (2026-09-17): a single physical GridGuard must feed three
different partners at once - a Wechselrichter (LPP Server partner, feed-in/production limit), evcc
(LPC Server partner, consumption limit), and the binding's own `oh-cs-entity` (LPC+LPP Server).
Under the pre-ADR-047 model that needs three `oh-eg-entity` Things, each with its own independent
Item set. Nothing prevented the LPC Items on the evcc-scoped Thing and the CS-scoped Thing from
carrying different values, even though both are supposed to reflect the exact same consumption
limit (equally for the LPP Items shared between the Wechselrichter-scoped and CS-scoped Things).
That is the actual defect: the binding modeled one value store per partner where physically there
must be exactly one per direction (LPC, LPP).

On the SPINE side this restriction does not exist. `onUseCasePartnersFound(List<UseCasePartner>
partners)` in `AbstractEEBusLimitEnergyGuardUseCase` already receives the full, evolving partner
list (the 2026-08-21 idempotency fix's own javadoc establishes that re-invocations carry the
current full list, not just deltas) - `jeebus.spine` already models "one local Feature, N bound
partners" natively. This is exactly the shape a real Hager Controllable-System entity is built to
accept from multiple GridGuards at once, which is what prompted the comparison. The bottleneck was
entirely in this binding's own `resolveLimitIdAndRegisterWriteListeners`, which resolved every
partner to its own `eebus:oh-entity` Thing via `ohEntityHandlerForSki` (exact-SKI match, first
match wins) and bound the write-path Item listeners to that one partner's `featureAddress` - a
design choice in `org.openhab.binding.eebus`, not a `jeebus.spine`/`jeebus.ship` limitation, so no
prior approval is needed for this change.

Considered and rejected: keeping today's one-Thing-per-SKI model and only documenting the
discipline needed to keep values in sync (an openHAB rule mirroring one canonical Item onto the
per-Thing Items) - this reduces the risk but does not remove it structurally; a missing or broken
rule silently reintroduces the exact defect this ADR closes. Fixing it in the binding itself,
below, removes the possibility rather than relying on convention.

## Decision

One `oh-eg-entity` Thing per Bridge is now sufficient, independent of how many partners (real
GridGuard-facing peers) are trusted. Its tagged LPC/LPP Items are fanned out, on every change, to
every partner currently found for that use case - not to a single SKI-matched partner.

Implemented in `org.openhab.binding.eebus` only, no `jeebus.ship`/`jeebus.spine` change:

- `EEBusHandler`: new `ohEgEntityWriteSourceHandler()`, resolving this Bridge's `oh-eg-entity`
  child Thing by **Thing type**, not by SKI (contrast the existing `ohEntityHandlerForSki`, kept
  unchanged for the per-partner `recordDetectedUseCase` marking below). Wired into
  `EEBusLpcClientUseCase`/`EEBusLppClientUseCase`'s constructors as a new
  `Supplier<Optional<EEBusOhEntityHandler>> writeSourceHandlerResolver` parameter, alongside the
  existing per-SKI `ohEntityHandlerResolver`.
- `AbstractEEBusLimitEnergyGuardUseCase`:
  - `onUseCasePartnersFound` no longer requires a per-SKI Thing match to proceed - it requires
    `writeSourceHandlerResolver.get()` to resolve (this Bridge's one `oh-eg-entity` Thing). The
    per-SKI `ohEntityHandlerResolver` lookup is still attempted, best-effort, purely to keep
    calling `EEBusOhEntityHandler#recordDetectedUseCase` on a legacy per-SKI Thing if one is still
    configured (backward compatibility, see Consequences).
  - `resolveLimitIdAndRegisterWriteListeners` renamed to `resolveLimitIdAndRegisterPartnerBinding`:
    still binds and resolves `limitId` per partner exactly as before (a partner's `limitId` is not
    guaranteed to match another's - unchanged, ADR-018), but on success stores the partner's
    `(featureAddress, limitId)` as a new `PartnerBinding` record in a new
    `Map<String, PartnerBinding> partnerBindings` (keyed by `communicationAddress`), then calls the
    new `registerWriteListenersOnce`.
  - `registerWriteListeners` renamed to `registerWriteListenersOnce`: looks up the write-path Items
    on the write-source Thing exactly as before, but now registers the Item listeners **once**
    (guarded by a new `AtomicBoolean writeListenersRegistered`, safe under concurrent partner
    resolution) rather than once per partner. Each listener calls the new
    `sendLimitWriteToAllPartners(active, watts, durationSeconds)`, which iterates
    `partnerBindings` and calls the existing, unchanged `sendLimitWrite` once per entry - so every
    currently bound partner receives the identical `isLimitActive`/`value`/`timePeriod` in the same
    Item-change event, each with its own correct `limitId`.
  - `close()` additionally clears `partnerBindings` and resets `writeListenersRegistered`.

`ohEntityHandlerForSki`/`ohEntityHandlerForCommunicationAddress` themselves are unchanged - they
still resolve a partner to a per-SKI Thing when one is configured, purely for the
`recordDetectedUseCase` status marking. `sendLimitWrite`, `resolveLimitId`, the bind/read logic,
and every diagnostic method are unchanged.

## Consequences

- A new trusted GridGuard-facing partner needs zero new Thing or Item configuration - it is
  automatically fed by the one existing `oh-eg-entity` Thing's current values as soon as SPINE
  discovers it.
- Divergent LPC (or LPP) values between partners of the same direction are now structurally
  impossible, not just discouraged by convention.
- Pre-ADR-047 installations with multiple `oh-eg-entity` Things (one per SKI) keep working
  unchanged - each such Thing's own tagged Items are still found and marked via the untouched
  per-SKI path (`recordDetectedUseCase`), but only **one** of them (the first one
  `ohEgEntityWriteSourceHandler` encounters) actually drives the write path from now on, since the
  write path no longer cares which Thing a given partner would have matched. Multiple
  `oh-eg-entity` Things is not a supported configuration going forward; consolidating to a single
  Thing (clearing `ski` on it) is the recommended migration, not yet enforced or validated against
  in config.
- A partner that disappears (its Bridge goes offline, mDNS stops seeing it) is not actively removed
  from `partnerBindings` - out of scope for this ADR, tracked as a follow-up; it only means a stale
  entry gets a write attempt that fails and is logged, not a functional regression versus before.
- `thing-types.xml`'s `oh-eg-entity` `ski` parameter description and README's "Write path"
  section were both updated as part of this change to describe the fan-out behavior and the
  now-supported single-Thing-per-Bridge configuration.

**Not yet compiled or live-tested.** Self-QA done this session: brace/paren balance confirmed equal
on all four touched files; byte-level CRLF-preservation confirmed (no bare LF/CR introduced) on all
four; a full `grep` sweep confirmed zero remaining references to the old
`resolveLimitIdAndRegisterWriteListeners`/`registerWriteListeners(` names anywhere under `src/`
(the two hits in `EEBusMetadataService.java` are an unrelated, historical 2026-08-21 bug-fix
comment narrating what the method was called _at that time_ - left as accurate history, not a
stale reference to current code). Still needed: `mvn clean install`, and a live retest with a
Bridge that has ≥2 simultaneously bound CS partners (e.g. `evcc-sim` + a second `energy-guard/CS`
simulator) confirming both receive the identical LPC value, that partners with different
`limitId`s are each written correctly, and that a newly discovered partner is fed without adding a
new Thing.
