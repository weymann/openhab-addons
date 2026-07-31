# ADR-021: Controllable System also mirrors confirmed limit status onto the paired oh-entity's dynamic Channel

## Status

Accepted (2026-08-23)

## Context

CONCEPT.md §4.2 split data binding by SPINE actor role: Client role (openHAB consumes from a
paired peer) gets dynamically created Channels on `eebus:oh-entity` (ADR-014/015); Server role
(openHAB offers something) gets `eebus` Item metadata instead, on the reasoning that a Server
value "comes from an already-existing, arbitrary local Item" and Discovery has nothing to
resolve for it.

That reasoning holds for MPC's Server role (`EEBusMpcServerUseCase` reads an existing local Item
and answers SPINE requests from it) and for the LPC/LPP Client-role write path
(`AbstractEEBusLimitEnergyGuardUseCase`, added 2026-08-21): the Energy Guard's outbound limit
command also originates from an Item the user already owns and tags. It does **not** hold for
`AbstractEEBusLimitControllableSystemUseCase` (LPC/LPP Server role, "Controllable System" actor):
this class does not offer a pre-existing local value — it **receives** a limit write from a
paired Energy Guard peer. Before this ADR, the only way to observe that write locally was to tag
an Item with `eebus="LPC.state"` (bridge-wide, derived `EEBusLimitControlStateMachine` state, not
the raw `isLimitActive`/value pair) or `eebus="LPC.consumptionLimit"` (bridge-wide, receives the
raw watt value as a Command onto whatever real device Item is tagged) — nothing is created
automatically, and nothing is scoped to the specific peer that sent the write.

This was found while pairing a real Energy Guard device (a physical SMGW, fixed in that role) against
an `eebus:oh-entity`-paired openHAB Controllable System: unlike the Client-role read path (where a
`lpc#limit-active`/`lpc#limit-value` Channel appears automatically on the peer's oh-entity Thing
once LPC is detected, no manual configuration needed), the Server-role receive path gave no
comparable, discoverable signal at all.

**Reframing (this ADR's core insight):** the correct split is not "Client role vs. Server role"
but "already backed by an existing Item vs. not". Data a use case **originates** from — whether
by offering it (MPC Server) or by writing it out (LPC/LPP Client's Energy Guard write path) —
naturally binds to an Item the user already has. Data a use case **receives** from a peer — MPC
Client's read, and now LPC/LPP Server's incoming limit write — has no such natural Item and
should get a discoverable, auto-created Channel instead, exactly like Client-role consumption
already does.

## Decision

**Reuse `EEBusOhPeerHandler#applyLimitStatus(String, boolean, double)` unchanged**, the same
method `AbstractEEBusLimitEnergyGuardUseCase` already uses to populate `lpc#limit-active`/
`lpc#limit-value` from its own subscription. `AbstractEEBusLimitControllableSystemUseCase` now
calls it too, from `onStateChanged` — the single place that already computes `active`
(`newState == LIMITED`) and the last written watt value for the SPINE-feature echo-back and for
`LPC.state`. Both existing outputs (SPINE echo, `LPC.state` metadata) are kept unchanged; the
Channel update is additive.

**Resolve the writing peer's `EEBusOhPeerHandler` in `onEnergyGuardFound`, not in `onLimitWritten`.**
`AbstractEEBusLimitControllableSystemUseCase` gains the same
`Function<String, Optional<EEBusOhPeerHandler>> ohPeerHandlerResolver` constructor parameter
`AbstractEEBusLimitEnergyGuardUseCase` already takes, wired from `EEBusHandler` with the same
`this::ohPeerHandlerForCommunicationAddress` reference already used for the Client-role use
cases. `onEnergyGuardFound` resolves `partner.getCommunicationAddress()` to an
`EEBusOhPeerHandler` and stores it in a new `energyGuardOhPeerHandler` field, **independently of,
and before, the existing `DeviceDiagnosis`/Heartbeat address check** — a peer without a
`DeviceDiagnosis` feature (seen on a real Hager Energy S10, see `TEST_PAIRING.md`) must still get
its Channel updated; that peer's write-handling has nothing to do with whether it also exposes a
Heartbeat feature.

**Inherits the existing "first Energy Guard partner only" simplification.** `onEnergyGuardFound`
already only tracks `partners.get(0)` (CONCEPT.md, "v1's motivating scenario has exactly one").
The new Channel is therefore scoped to that one tracked partner, same as the pre-existing
Heartbeat subscription — not a regression, and actually an improvement over the bridge-wide
`LPC.state`/`LPC.consumptionLimit` metadata Items, which were never peer-scoped at all.

**No change to the write-path Item-tag mechanism** (`EEBusMetadataService#findByTag`,
`AbstractEEBusLimitEnergyGuardUseCase#registerWriteListeners`) — explicitly out of scope for this
change, confirmed with the user.

## Consequences

### Positive

- A user pairing openHAB (Controllable System) against a real Energy Guard now gets an automatic,
  discoverable `lpc#limit-active`/`lpc#limit-value` Channel on that peer's `eebus:oh-entity` Thing —
  no Item tagging required, mirroring the zero-configuration Client-role experience.
- Reuses `EEBusOhPeerHandler#applyLimitStatus`/`ensureChannel` verbatim — no new Channel types,
  no new `thing-types.xml` declarations, no duplicated idempotency logic.
- Correctly peer-scoped, unlike the pre-existing `LPC.state`/`LPC.consumptionLimit` bridge-wide
  metadata Items.
- Resolves independently of `DeviceDiagnosis` availability, so it also works against peers
  missing that feature (a known real-hardware case).

### Negative

- `AbstractEEBusLimitControllableSystemUseCase`'s constructor signature changes (new required
  parameter) — `EEBusLpcServerUseCase`/`EEBusLppServerUseCase` and their `EEBusHandler`
  instantiation sites all needed updating in lockstep. No external/API compatibility concern
  (all `internal` package).
- Still limited to a single tracked Energy Guard partner per Controllable System bridge, same as
  before this ADR — multiple concurrent Energy Guards remains unimplemented and out of scope.
- Not yet compiled (no Maven in the editing sandbox, same limitation as ADR-016 through
  ADR-020) or live-retested — reviewed manually only (brace/paren balance checked
  programmatically, CRLF preserved, imports and constructor call sites cross-checked by hand).

## Verification status

Not yet verified: `mvn clean install`, and a live retest pairing openHAB's Controllable System
role against a real Energy Guard, toggling its limit write and confirming the paired oh-entity's
`lpc#limit-active`/`lpc#limit-value` Channel updates accordingly — both user-owned, same pattern
as every prior ADR in this series.
