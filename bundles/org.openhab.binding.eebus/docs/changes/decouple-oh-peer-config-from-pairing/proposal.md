# Proposal: Decouple eebus:oh-peer Configuration from Pairing

## Intent

Today, initializing an `eebus:oh-peer` Thing immediately adds its `ski` to the parent
`eebus:service` Bridge's trusted-SKI set (`separate-real-and-oh-peer-things`, "Creating an
eebus:oh-peer Thing performs pairing"). Saving a Thing's configuration therefore silently
performs a trust decision, with no chance to review the SKI first. This is most visible when
pairing two local `eebus:service` Bridges with each other (CONCEPT.md §4.4): both sides would
need to create their `eebus:oh-peer` Thing in the same step that grants trust, with no way to
verify both SKIs are correct before committing.

This change does two things:

1. Makes the `ski` configuration parameter of `eebus:oh-peer` a selectable option list (sourced
   from known `eebus:peer` Things and from other local `eebus:service` Bridges' own SKI), instead
   of requiring the 40-hex-character SKI to be typed or copy-pasted.
1. Separates configuring an `eebus:oh-peer` Thing from actually trusting it: creating the Thing
   no longer grants trust by itself. Trust is granted or revoked explicitly via two new,
   parameterless Thing Actions, `pair()` and `unpair()`, on the Thing itself.

See CONCEPT.md §4.5 (dropdown) and §4.6 (decoupling decision) for the full discussion, including
why this avoids the two open risks (Action-parameter dropdown rendering, simultaneous two-Bridge
writes) that left the earlier `pairWith` idea (§4.4) under test reservation.

## Scope

In scope:

- A selectable option list for `eebus:oh-peer`'s `ski` configuration parameter, sourced from
  known `eebus:peer` Things (real, discovered devices) and other local `eebus:service` Bridges'
  own SKI (for service-to-service pairing). Manual entry of a SKI not in the list remains
  possible.
- Excluding SKIs already paired under the target `eebus:service` Bridge from that option list;
  SKIs already paired under a _different_ Bridge remain offered.
- Creating and initializing an `eebus:oh-peer` Thing no longer adds its `ski` to the parent
  Bridge's trusted-SKI set.
- A parameterless `pair()` Thing Action on `eebus:oh-peer` that adds its `ski` to the parent
  Bridge's trusted-SKI set.
- A parameterless `unpair()` Thing Action on `eebus:oh-peer` that removes its `ski` from the
  trusted-SKI set without removing the Thing.
- Persisting pairing state as a Thing property so it survives an openHAB restart without
  re-invoking `pair()`.

Out of scope:

- The cross-Bridge `pairWith` Thing Action from CONCEPT.md §4.4 remains tentative and
  unimplemented; not part of this change.
- PIN-based pairing (CONCEPT.md §5.2/§6 — decided not in v1).
- Client-role dynamic channels / server-role metadata data flow (tracked separately, CONCEPT.md
  §5.4/§5.5/§7).
- Migration of any `eebus:oh-peer` Things created before this change (binding is pre-release, no
  migration path provided).

## Open Questions

- None outstanding — see CONCEPT.md §4.6.

---

_Change ID: `decouple-oh-peer-config-from-pairing`._
