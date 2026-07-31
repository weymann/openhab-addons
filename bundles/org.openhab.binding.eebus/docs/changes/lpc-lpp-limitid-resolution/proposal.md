# Proposal: LPC/LPP LoadControl limitId Resolution

## Intent

The binding currently hardcodes a single shared `limitId = 0` for both LPC (Limitation of Power
Consumption) and LPP (Limitation of Power Production) on the `LoadControl` SPINE feature, on both
the Server role (`AbstractEEBusLimitControllableSystemUseCase`) and the Client role
(`AbstractEEBusLimitEnergyGuardUseCase`). Confirmed live (2026-08-21): toggling the LPC
Client-role write-path Item also turned the LPP read-back status Channel active on the same
paired peer, because both use cases read and write the exact same `limitId=0` entry.

Confirmed against a real Hager Energy S10 discovery capture
(`discovery-d__i_52158_S10-1.json`) that LPC and LPP genuinely share one SPINE Entity and one
`LoadControl` Feature on certified hardware - that part of the current design is correct and not
being changed. The gap is that `EEBus_SPINE_TS_LoadControl.xsd`'s
`LoadControlLimitDescriptionListDataSelectorsType` explicitly supports filtering by
`limitDirection`, which only makes sense if a single shared `LoadControl` feature is meant to
hold _multiple_ limit entries (one per direction), each independently addressable by `limitId`
and disambiguated by `limitDirection` - not one shared, hardcoded ID. This mirrors the pattern
`EEBusMpcClientUseCase#resolvePowerMeasurementId` already uses for `measurementId`, which
`AbstractEEBusLimitEnergyGuardUseCase` never adopted for `limitId`.

**Amendment (2026-08-21, ~22:30), found while attempting task 3.1's live retest:** the Client-role
fix above (tasks 2.1-2.4, ADR-018) is confirmed correct and working. However, the live retest
(toggling the LPC write-path Item only) still showed the LPP read-back status changing. Direct
source review found the actual remaining defect lives entirely on the **Server** (Controllable
System) role side, in code this change already touches but did not fully fix: `onLimitWritten`
reacts to a write to _either_ direction's `limitId` regardless of which use case it belongs to
(no filtering by `data.getLimitId()`), and `onStateChanged` publishes back to the peer via
`LimitListDataFunction#updateData(0, ...)` - a **hardcoded literal list index** - so whichever of
LPC/LPP registered its list entry second (append order is setup-order-dependent) overwrites the
_other_ direction's peer-visible entry on every state change instead of ever updating its own.
This is a second, distinct defect in the same problem area, not a partial fix of the same one -
see `docs/ADR/019-isolate-lpc-lpp-server-write-handling.md`.

## Scope

In scope:

- Server role: LPC and LPP Server use cases get distinct, stable `limitId` values instead of
  both writing to a shared `LIMIT_ID = 0`, so their `LoadControlLimitDescriptionData`/
  `LoadControlLimitData` entries coexist instead of overwriting each other.
- Client role: LPC and LPP Client use cases resolve which `limitId` is theirs by reading the
  peer's `loadControlLimitDescriptionListData` and matching on `limitDirection`, instead of
  assuming a fixed `limitId`.
- Regression coverage for the observed symptom: toggling one direction's write-path Item must
  not change the other direction's read-back status on the same paired peer.
- **(Amendment)** Server role: `onLimitWritten` only reacts to a write whose `limitId` matches
  this instance's own `getLimitId()`; `onStateChanged` publishes state changes to this
  instance's own list index (captured from `addData()`), never a hardcoded index.

Out of scope:

- Scenario 2 (Failsafe values) and Scenario 4 (Constraints) for LPC/LPP Client role - already
  out of scope per `docs/ADR/015-lpc-lpp-client-role-channels.md` and the
  `lpc-lpp-client-role-channels` change.
- Any change to `jeebus.ship`/`jeebus.spine` - protected, and not required: the fix lives
  entirely in this binding's own use case classes.
- ADR-016 (pom.xml dependency removal) and ADR-017 (idempotent partner subscription) - already
  resolved, unrelated to this change.
- Adding unit test coverage for this class family - a pre-existing gap flagged by `$QA` during
  the original ADR-018 review, and flagged again here since it is exactly what let this second
  defect ship unnoticed; tracked as a deferred follow-up in `tasks.md`, not blocking this fix.

## Open Questions

- Exact `limitId` values to assign server-side (e.g. `0`/`1`, or some other stable pair) - no
  convention is mandated by the SPINE spec; `$Architect` to decide and record as an ADR
  referencing this proposal's requirements.
- How the Client role should behave when the peer's `loadControlLimitDescriptionListData` read
  fails or contains no entry matching the expected `limitDirection` - the spec below requires
  "no status reported, not a guess," matching this binding's existing convention for other
  resolution failures (e.g. `ohPeerHandlerResolver` misses in `AbstractEEBusLimitEnergyGuardUseCase`),
  but `$Architect` should confirm this is the right failure mode before `$Dev` implements it.
- **(Amendment)** None - the Server-side root cause is confirmed by direct source read of
  `AbstractEEBusLimitControllableSystemUseCase.java` and jeebus.spine's `LimitListDataFunction`/
  `DataListHolder` (`updateData` is confirmed literal-index-based via
  `dataList.set(entry.getKey(), ...)`), and corroborated line-by-line against the live retest log.

---

_Stored at `org.openhab.binding.eebus/docs/changes/lpc-lpp-limitid-resolution/proposal.md`._
