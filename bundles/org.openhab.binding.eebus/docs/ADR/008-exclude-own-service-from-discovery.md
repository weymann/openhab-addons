# ADR-008: Exclude a Bridge's Own SKI from Inbox Discovery

## Status

> Proposed

## Context

The Inbox showed four `eebus:peer` suggestions all labeled "Fraunhofer ISE jEEBus" (the default
brand/model `jeebus.ship` announces via mDNS for any locally-run SHIP server, regardless of the
`eebus:service` Bridge's own `deviceBrand`/`deviceModel` configuration - confirmed by contrast
with the one real third-party device in the same Inbox, "Hager Energy S10", which correctly shows
its own brand). One of the four had the exact same SKI the running Bridge logs as its own at
startup (`"Key Management initialized. SKI of this node is a0bf50f4..."`).

Root cause: `EEBusMdnsDiscoveryParticipant` (ADR-003) discovers `_ship._tcp.local.` independent of
any `eebus:service` Bridge's lifecycle. An `eebus:service` Bridge's own SHIP server necessarily
announces itself over mDNS too, so other EEBUS devices can find it - and this same participant
then re-discovers that self-announcement and offers it back as a bogus `eebus:peer` suggestion for
itself. `createResult()` already filtered out already-paired SKIs (`isAlreadyPaired`), but had no
equivalent filter for "is this actually one of my own Bridges".

The other three "Fraunhofer ISE jEEBus" entries (distinct SKIs, none matching the currently
running Bridge) are not fixed by this ADR - see "Negative" below.

## Decision

Add `EEBusMdnsDiscoveryParticipant#isOwnService(String ski)`, checked in `createResult()`
alongside the existing `isAlreadyPaired()` check. It compares the discovered SKI against the
`localSki` property every `eebus:service` Thing publishes once its SHIP server is up
(`EEBusHandler#startShipSpine`, `updateProperty(PROPERTY_LOCAL_SKI, communication.getOwnSki())`).
That property key was previously an inline string literal in `EEBusHandler`; it is now
`EEBusBindingConstants.PROPERTY_LOCAL_SKI`, shared by both classes (java-coding-rules.md: no
magic strings for values used in more than one place).

## Consequences

### Positive

- A Bridge no longer suggests pairing with itself in the Inbox.
- Symmetric with the existing `isAlreadyPaired` filter - same method shape, same place it's
  applied, easy to reason about together.

### Negative

- Does **not** explain or clean up the other three stale "Fraunhofer ISE jEEBus" entries observed
  in the same screenshot. Their SKIs don't match the currently running Bridge, and their
  `mdnsServiceInstance`-style names/ids ("e"/"a-c-d") look like earlier test `eebus:service` Things
  created (and later deleted) during development - i.e. other, no-longer-configured self-
  announcements. Two explanations, not mutually exclusive: (a) the underlying SHIP server process
  is still actually running somewhere (orphaned, e.g. a JVM that was killed uncleanly instead of
  going through `dispose()`, so no mDNS "goodbye" was ever sent) or (b) it stopped cleanly but the
  record is still cached until its TTL expires in JmDNS/the shared `MDNSClient`. This binding has
  no way to attribute a discovered SKI it doesn't currently own to "used to be mine" - only "is
  mine right now" (`isOwnService`) - so no code change here can retroactively filter those out.
  Practical mitigation: ignore/remove them from the Inbox manually; they should stop reappearing
  once the underlying stale records/processes age out.
- `isOwnService` only helps once a Bridge's SHIP server has actually started (`localSki` is set in
  `startShipSpine`, after a successful bind) - a Bridge that is still starting, or failed to start,
  offers no protection yet for its own (not-yet-announced) SKI. Not a practical problem: the SKI
  is derived from the keystore-persisted certificate, which is stable across restarts, so this
  only matters for the brief window between "server bound" and "property published", not before.

---

_Adds `EEBusMdnsDiscoveryParticipant#isOwnService`; introduces
`EEBusBindingConstants.PROPERTY_LOCAL_SKI`, replacing the inline `"localSki"` literal previously
only in `EEBusHandler`._
