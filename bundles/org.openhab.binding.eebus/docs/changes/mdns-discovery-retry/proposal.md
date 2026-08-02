# Proposal: Retry mDNS Discovery When an eebus:network Bridge Is Added

## Intent

`eebus-network-discovery` (ADR-003) deliberately drops any real EEBUS/SHIP device seen via
`_ship._tcp.local.` mDNS before an `eebus:network` Bridge Thing exists — no Inbox entry is
created, and nothing re-evaluates that device later. Confirmed against real hardware: a device
that announced itself before the Bridge was added stays invisible until the user manually
triggers an Inbox "Scan", at which point it appears immediately, because a scan forces an active
mDNS re-query and by then the Bridge exists.

This is surprising to a first-time user, who has no reason to know that adding the Bridge should
be followed by a manual scan. This change makes that manual step unnecessary: adding an
`eebus:network` Bridge should, by itself, surface any device that mDNS still has cached, exactly
as if the user had clicked "Scan" right after adding it.

## Scope

In scope:

- When an `eebus:network` Bridge Thing is added and at least one real EEBUS/SHIP device is
  still present in the mDNS cache (i.e. would be found by a manual scan performed at that same
  moment), an Inbox entry is created for it automatically, without requiring a user-triggered
  scan or a fresh mDNS announcement from the device.
- This applies uniformly regardless of whether the device was first seen before or after the
  binding itself was installed.

Out of scope:

- Guaranteeing discovery of a device whose mDNS record has already expired from the cache (or
  was actively withdrawn) before the `eebus:network` Bridge is added — this is the same
  limitation a manual scan already has, not a regression introduced by this change.
- Any new persistent (disk-backed) storage of discovered devices — this only reaches devices
  still resolvable via mDNS at the moment the Bridge is added.
- Changing how discovery behaves once an `eebus:network` Bridge already exists (already
  well-defined and unaffected by this change).
- Behavior for more than one simultaneous `eebus:network` Thing — remains an accepted, unresolved
  scope limit per ADR-003.

## Open Questions

- Dependency: this change modifies a requirement introduced by `eebus-network-discovery`
  (ADR-003). It assumes that change has already been accepted; the delta spec below is written
  against its requirements, not against a merged `docs/specs/discovery/spec.md` (which does not
  exist yet, since `eebus-network-discovery` has not been archived).
- None outstanding on the design itself — see ADR-004 for the mechanism (`$Architect`).

---

_Change ID: `mdns-discovery-retry`._
