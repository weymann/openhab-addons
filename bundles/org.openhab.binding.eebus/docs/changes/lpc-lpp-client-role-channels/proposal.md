# Proposal: LPC/LPP Client-Role Monitoring Channels

## Intent

CONCEPT.md §5.4.3 documents a primary-source-verified candidate Channel table for LPC
(Limitation of Power Consumption) and notes LPP (Limitation of Power Production) is
structurally identical with mirrored naming. Both are currently only implemented in the
**Server role** (`EEBusLpcServerUseCase`/`EEBusLppServerUseCase` - openHAB _is_ the
Controllable System, offering data via Item metadata). No **Client role** implementation
exists yet - openHAB cannot act as an Energy Guard reading a remote Controllable System's
current limit state, the mirror of what `dynamic-client-role-channels` just built for MPC.

This change closes that gap for the **read-only monitoring half** of LPC/LPP Client role,
reusing the dynamic-Channel mechanism, gating, and persistence semantics established by
`dynamic-client-role-channels` (`docs/ADR/014-dynamic-client-role-channels.md`) - builds on
that change (not yet archived, see "Dependencies" below).

## Scope

**Note: further narrowed twice during `$Dev` (2026-08-12) - see `tasks.md` for the play-by-play.
The bullets below are the scope as originally drafted; the actually-implemented scope is
`limit-active`/`limit-value` only (no `limit-duration`, no Scenario 4/Constraints, no
Scenario 2/Failsafe). Kept as-is rather than rewritten, so this document still records the
original intent - `tasks.md` is the source of truth for what shipped.**

In scope (as originally scoped; see note above for what actually shipped):

- Two `channel-group-type`s, `lpc` and `lpp`, each with Channels covering Scenario 1 (current
  limit status: `limit-active` `Switch`, `limit-value` `Number:Power`, `limit-duration`
  `Number:Time`) and Scenario 4 (constraints: `nominal-max` `Number:Power`,
  `contractual-nominal-max` `Number:Power`) - see CONCEPT.md §5.4.3 for the primary-source field
  mapping (Tables 22/23/27).
- Two new `UseCase` classes, `EEBusLpcClientUseCase` and `EEBusLppClientUseCase`, sharing a new
  `AbstractEEBusLimitEnergyGuardUseCase` base class - mirrors this codebase's own existing
  pattern for the Server-role counterpart (`AbstractEEBusLimitControllableSystemUseCase` +
  `EEBusLpcServerUseCase`/`EEBusLppServerUseCase`), and `EEBusMpcClientUseCase`'s
  detection/subscription structure (Client actor `"EnergyGuard"`, expected peer actor
  `"ControllableSystem"` - exact wire-format strings taken directly from
  `AbstractEEBusLimitControllableSystemUseCase`'s own javadoc, which cites
  `EEBus_UC_IG_GeneralGuidelines_V1.0.0.pdf`).
- Wiring in `EEBusHandler#startShipSpine`: registered when `LPC`/`LPP` is present in
  `supportedUseCasesClient`, using the same `ohPeerHandlerForCommunicationAddress` resolver
  already built for MPC.
- Use-case visibility properties `lpc`/`lpp` = `"server"`, reusing
  `EEBusOhPeerHandler#recordDetectedUseCase` unchanged.

Out of scope (deferred to a later change):

- **Scenario 2 (Failsafe values)** - `failsafe-limit-value`/`failsafe-duration-minimum`. Descoped
  from this change (revised down from the original scope draft) purely for review-batch size:
  Scenario 1/4 alone already introduce two full Client-role `UseCase` classes plus a shared base
  class; adding a third `DeviceConfiguration`-based read path in the same change would make this
  diff harder to review carefully. Structurally, it is a close analog of the Scenario 4 read
  (a list-data read + parse), so a follow-up change should be small.
- **Scenario 3 (Heartbeat) mechanics.** CONCEPT.md §5.4.3 already established no Channel is
  needed for it, but the _protocol-level_ heartbeat exchange is mandatory for both actors
  (Table 1: 3 – Heartbeat, M/M) and is **mutual** - per
  `AbstractEEBusLimitControllableSystemUseCase`'s own javadoc, the Server-role (`ControllableSystem`)
  side already both serves its own heartbeat _and_ watches the `EnergyGuard`'s. The reverse
  (`EnergyGuard` serving its own heartbeat so a strict `ControllableSystem` peer keeps trusting
  it) is genuinely new work - it needs this local entity to expose a `DeviceDiagnosis` **Server**
  feature, structurally similar to `AbstractEEBusLimitControllableSystemUseCase#setupDeviceDiagnosis`
  but from the opposite actor. Not implemented here - flagged as a real limitation, not a
  formality: a strict real `ControllableSystem` peer may not consider this binding a
  fully-compliant `EnergyGuard` without it, even though the monitoring Channels themselves will
  still populate correctly from the read/subscribe side. See "Open Questions".
- **The control/write path** - Energy Guard actively _setting_ a new limit
  (`isLimitActive`/`value`/`timePeriod` as a command, not just a status readout). This is a
  materially different and higher-risk feature (openHAB issuing a command that changes a real
  device's power behavior, not just reading a value) and deserves its own `$Spec`/`$Architect`
  pass - including failure-mode design (what happens if the write fails or the connection drops
  mid-command) that a read-only monitoring Channel does not need.
- LPP-specific sign/terminology verification against a real peer (CONCEPT.md §5.4.2 states LPP
  is "strukturell identisch" with mirrored naming, based on the LPC TS PDF's own cross-reference,
  not a separate LPP TS PDF reading pass) - flagged as a residual risk, not blocking, since the
  Server-role `EEBusLppServerUseCase` already relies on the same assumption today.
- A general `ChannelTypeProvider` (still deferred per CONCEPT.md §4.2.2 point 1, unchanged from
  `dynamic-client-role-channels`).

## Open Questions

- **Should the control/write path ever be built, and if so, as Commands on these same Channels
  or a separate Thing Action?** Not answered here - out of scope for this change (see above).
  Flag to `$Concept`/the user before starting a follow-up change, since a writable
  `Number:Power` Channel implies openHAB can autonomously alter a real device's power limit,
  which has safety/liability implications a read-only monitoring Channel does not.
- **Is EnergyGuard-side heartbeat serving required before shipping this to a real device?** Not
  answered here. The monitoring Channels will populate correctly against any peer lenient enough
  not to require it (plausible for a first real-device test, mirroring how MPC shipped without
  this binding first needing to be a "complete" SPINE citizen); a strict peer may behave
  differently after some timeout. Recommend treating this as a fast-follow once a real
  LPC/LPP-capable `ControllableSystem` peer is available to test against, not a blocker for
  merging the read path.
- **LPP wire-format naming** (`ActivePowerProductionLimit` etc., CONCEPT.md §5.4.2) is asserted,
  not independently verified against a primary-source LPP TS PDF page-by-page the way LPC's
  Table 22/23/27 was. Low risk (mirrors the already-shipped Server-role assumption) but should
  be confirmed against a real LPP-capable peer during `$QA`, same as MPC's "confirmed 2026-08-05
  against a real Hager Energy S10" precedent (docs/ADR/011-usecasename-lowercamelcase.md).

## Dependencies

Builds on `dynamic-client-role-channels` (`EEBusOhPeerHandler#ensureChannel`,
`#recordDetectedUseCase`, the `ohPeerHandlerForSki`/`ohPeerHandlerForCommunicationAddress`
resolvers in `EEBusHandler`) - not yet archived as of this proposal (its `$Release` Maven build
has not been run/confirmed). This change's delta spec is written against that change's
in-progress `dynamic-channels` domain, same as any two changes touching the same domain before
either is archived.

---

_Change ID: `lpc-lpp-client-role-channels`. Domain: `dynamic-channels` (extends the baseline
`dynamic-client-role-channels` is establishing)._
