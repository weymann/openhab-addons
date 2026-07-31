# Proposal: Merge eebus:network and eebus:peer into a Bridgeless Thing

## Intent

`eebus:network` is a lightweight Bridge Thing (ADR-003) whose only role is to act as the
mandatory parent for `eebus:peer` Inbox entries. It holds no SHIP/SPINE identity, defines no
channels, and its handler (`EEBusNetworkHandler`) goes `ONLINE` unconditionally the moment it is
added - it never gates, mirrors, or influences anything at runtime. CONCEPT.md §4.5 already
documents this as "funktional inert", and §7(15) records a 2026-08-21 proposal to merge it away.

Requiring this Bridge to be added manually, before any discovered device can appear in the
Inbox, is friction with no corresponding benefit - it also directly contradicts the original
intent of ADR-003 ("background discovery starts the moment the binding is installed"), since
discovery results still cannot surface until this extra manual step has happened. ADR-004
exists solely to paper over the resulting gap (a device seen before the Bridge existed staying
invisible) and was never implemented in code (`docs/changes/mdns-discovery-retry/tasks.md` is
0/12) - its entire premise disappears once the Bridge itself is removed.

This change removes `eebus:network` and makes `eebus:peer` a bridgeless (top-level) Thing type,
reusing the existing binding ID's `eebus-peer` identifier rather than the ambiguous `peer`, so a
discovered device requires no prerequisite Thing at all.

## Scope

In scope:

- Remove the `eebus:network` Bridge Thing type and `EEBusNetworkHandler`.
- Replace the `eebus:peer` Thing type (child of `eebus:network` only) with a bridgeless
  `eebus:eebus-peer` Thing type carrying the same `ski` configuration parameter and the same
  "seen, not trusted" semantics.
- `EEBusMdnsDiscoveryParticipant` creates Inbox entries unconditionally (still excluding
  already-known SKIs and this instance's own `eebus:service` identities, per ADR-008) - no
  Bridge lookup, no `null` result while none exists.
- Retire ADR-004 and the `mdns-discovery-retry` change proposal - both addressed a gap that
  only existed because of the Bridge this change removes.
- Finally delete `EEBusDiscoveryService.java`, the ADR-003 stub pending manual deletion since
  this sandbox's filesystem mount previously rejected the delete operation (still true; moved to
  `_to_delete/` again this time).

Out of scope:

- Any change to `eebus:service` or `eebus:oh-entity` (CONCEPT.md §4.5 already settled that
  `eebus:service` stays a top-level, multiply-instantiable Bridge - see §7(15) "Explizit nicht
  übernommen").
- Fixing the three "was ist wann verfügbar" bugs referenced by CONCEPT.md §7(15) (trust-check
  timing, channel-push timing, address- vs. SKI-based resolver) - unrelated to Thing-type count.
- Migrating already-provisioned `eebus:network`/`eebus:peer` Things in an existing installation;
  this binding has no released users yet (single real-device test setup, CONCEPT.md "Real-
  hardware test findings") so no migration path is provided - existing Things of the removed
  types simply stop resolving to a handler and should be deleted and re-added.
- `TEST_PAIRING.md`'s pre-existing, unrelated inconsistency (its Test 1 step 4 already describes
  adding an `eebus:peer` Thing under `eebus:service`, which predates the §4.5 peer/oh-peer split
  and was never updated) - only its `eebus:network`-specific passages are corrected here.

## Open Questions

- None outstanding. Per the user's 2026-08-23 direction this proceeds now, ahead of the pending
  live retest/build for `lpc-lpp-limitid-resolution` (ADR-018/019) - see "Risk accepted" note in
  `tasks.md`.

---

_Change ID: `merge-network-peer-things`. Supersedes ADR-003's decision 1 (introducing
`eebus:network`) and ADR-004 in full; see `docs/ADR/020-merge-network-peer-things.md`._
