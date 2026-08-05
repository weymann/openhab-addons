# Proposal: Separate Real and OH-Managed EEBus Peer Things

## Intent

The current `eebus:peer` Thing type accepts either `eebus:network` or `eebus:service` as its
parent Bridge, with identical configuration but very different runtime meaning. Under
`eebus:service`, adding a `eebus:peer` Thing is the pairing action (its SKI becomes part of the
Bridge's trusted-SKI set, and it is meant to eventually carry dynamically generated channels).
Under `eebus:network`, it is a passive record of a real device seen via mDNS discovery, with no
local identity to pair against — no trust computation and no channels are possible there.
Supporting both parents on one Thing type hides this difference from the user and lets an
unusable configuration (a `eebus:peer` under `eebus:network` that can never do anything
functional) look valid.

This change splits `eebus:peer` into two distinct Thing types so the Main UI "Add Thing" list
itself communicates the difference, and separates the naming ("EEBus Peer" for real, discovered
devices vs. "EEBus OH Peer" for openHAB-managed pairings) so users can tell "real" and "openHAB"
EEBus devices apart at a glance. It also resolves an existing ambiguity in server-role Item
metadata resolution when more than one `eebus:service` Bridge offers the same use case.

## Scope

In scope:

- Restricting `eebus:peer` to `eebus:network` as its only supported parent Bridge.
- Introducing a new `eebus:oh-peer` Thing type, child of `eebus:service` only, carrying the
  pairing/channel/metadata behavior currently described for `eebus:peer` under `eebus:service`.
- Renaming the `ski` configuration parameter's label on each type ("SKI" for `eebus:peer`,
  "Trusted SKI" for `eebus:oh-peer`) — no change to the parameter name itself.
- Relabeling the `eebus:service` Bridge type's UI label to "EEBus OH Service" (label only, no
  structural change).
- Adding an `oh-service-id` prefix to the server-role Item metadata value format, to
  disambiguate between multiple `eebus:service` Bridges offering the same use case/datapoint.

Out of scope:

- Nesting `eebus:service` under `eebus:network` (considered and rejected — see CONCEPT.md §4.5
  point 1).
- Any automatic link between an `eebus:peer` and an `eebus:oh-peer` Thing (e.g. a future
  `pairWith` Thing Action, CONCEPT.md §4.4, remains a separate, still-tentative change).
- Actual implementation of client-role dynamic channels or server-role metadata data flow — both
  remain tracked by their own, not-yet-implemented work (CONCEPT.md §5.4/§5.5/§7).
- Migration of any already-existing `eebus:peer` Things (none exist in production yet; binding
  is pre-release).

## Open Questions

- None outstanding — see CONCEPT.md §4.5 for the discussion that led to this proposal.

---

_Change ID: `separate-real-and-oh-peer-things`._
