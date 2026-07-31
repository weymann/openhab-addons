# Proposal: Remove EnergyGuard (Client-Role) LPC/LPP Monitoring Channels

## Intent

The user corrected the binding's architecture (2026-08-27, `$Concept`): the EnergyGuard
(Client role) side of LPC/LPP should only ever interact via a tagged Item's write path - it does
not need a Channel that echoes a peer's own reported limit status back into openHAB, since the
tagged Item already is the source of truth for the value being written. A Controllable System
(Server role) is the correct place for a Channel, because it _receives_ a value it has no local
Item for. The current implementation does not match this: `AbstractEEBusLimitEnergyGuardUseCase`
also reads and subscribes to the peer's `LoadControlLimitListData` and mirrors it onto a Channel
(`docs/ADR/014-dynamic-client-role-channels.md`'s mechanism, applied by `docs/ADR/015-lpc-lpp-
client-role-channels.md`, later statically declared on `eebus:oh-eg-entity` by `docs/ADR/026-
oh-eg-entity-static-channels.md`/`docs/ADR/027-derive-local-use-cases-from-entities.md`). This
change removes that redundant read/subscribe/Channel path and leaves the write path untouched.

## Scope

In scope:

- Remove `AbstractEEBusLimitEnergyGuardUseCase#subscribeLimitStatus(...)` and
  `#applyLimitStatus(RequestResult, long, EEBusOhEntityHandler)`.
- Rename `resolveLimitIdAndSubscribe(...)` to `resolveLimitIdAndRegisterWriteListeners(...)` and
  drop its now-removed `subscribeLimitStatus(...)` call.
- Remove `eebus:oh-eg-entity`'s static `<channel-groups>` declaration in `thing-types.xml`; keep
  the Thing type itself (its "mere presence implies LPC+LPP Client" property, ADR-027, is
  independent of Channels).
- Update class/thing-type javadoc and description text, `README.md`'s Channels section, and
  `CONCEPT.md` (§4.9, §7 checklist) to describe the EnergyGuard role as write-only.
- Supersede `docs/ADR/015-lpc-lpp-client-role-channels.md` in full; append a revision note to
  `docs/ADR/026-oh-eg-entity-static-channels.md` (Thing type kept, Channel declaration removed).

Out of scope:

- The Controllable-System (Server role) side (`AbstractEEBusLimitControllableSystemUseCase`,
  `eebus:oh-cs-entity`, ADR-021/022/025) - explicitly confirmed correct and unaffected by the
  user's own statement.
- `docs/ADR/014-dynamic-client-role-channels.md`'s general mechanism - still valid and in active
  use for MPC (`EEBusMpcClientUseCase`), not touched.
- Any change to `jeebus.ship`/`jeebus.spine` - this is a purely binding-side (`org.openhab.
  binding.eebus`) removal; project instructions require prior human approval for those two repos
  and neither is touched here.
- The EnergyGuard-side Heartbeat TODO (CONCEPT.md §7 item 19) - unrelated, still open.

## Open Questions

None - the user's correction was explicit and unambiguous, and the code-level scope was
confirmed by reading the actual call graph before implementing.

---
