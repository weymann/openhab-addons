# Proposal: EEBus Network Discovery

## Intent

Today, background discovery of real EEBUS/SHIP devices only starts once an `eebus:service`
Bridge (a full local SHIP identity: certificate, port, vendor/model/serial) has been
configured and successfully gone `ONLINE`. Installing the binding alone produces no visible
effect, which is confusing: a user's first action after installation is to add a Thing, but
there is nothing to add yet, and nothing on the network is visibly detected until a heavyweight
local identity is fully configured first.

Discovering the presence of real EEBUS devices is, protocol-wise, independent of having a local
SHIP identity — it is passive `_ship._tcp.local.` mDNS listening, which needs no certificate,
no trust relationship, and no active SHIP session. This change decouples "seeing what's on the
network" from "having our own identity to pair with it", and introduces a lightweight
`eebus:network` Bridge as the anchor that discovered real devices attach to.

## Scope

In scope:

- Background discovery of real EEBUS/SHIP devices starts immediately when the binding is
  installed, independent of any configured Thing.
- A new `eebus:network` Bridge Thing type with no mandatory configuration, acting as the parent
  for discovered `eebus:peer` Inbox entries.
- Discovered devices are only added to the Inbox once at least one `eebus:network` Bridge
  exists.
- Already-paired SKIs (existing `eebus:peer` Things, regardless of which `eebus:service` they
  belong to) continue to be excluded from new Inbox entries.

Out of scope:

- The relationship between `eebus:network` and the existing `eebus:service` Bridge (e.g.
  whether `eebus:service` becomes a child of `eebus:network`) — deferred to a follow-up change.
- Simulated/virtual local EEBUS devices driven by Items (previously discussed as "case B") —
  deferred to a follow-up change.
- Any change to pairing, trust, or certificate handling.
- Supporting more than one simultaneous `eebus:network` Bridge Thing.

## Open Questions

- None outstanding — the two options considered (auto-suggesting `eebus:network` as an Inbox
  entry vs. requiring it to be added manually) were resolved in favor of manual addition, since
  `eebus:network` has no real-world counterpart on the wire to discover in the first place.

---

_Change ID: `eebus-network-discovery`._
