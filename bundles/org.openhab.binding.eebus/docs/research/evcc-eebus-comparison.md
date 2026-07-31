# Reference: How evcc Implements EEBus

## Purpose

This is a background reference note, not an ADR or spec. It summarizes how the
[evcc](https://github.com/evcc-io/evcc) project (Go, EV charging/energy
management) implements EEBus, captured from a live read of
`C:\Projects\evcc` on 2026-08-23. It exists to give future `$Concept` /
`$Architect` discussions a concrete comparison point against this binding's
own approach. Nothing here implies a decision has been made to adopt any of
it — see "Takeaways" at the end for candidate discussion points only.

## Source

Read directly from the user's local evcc checkout, `go.mod` pinned to:

```text
github.com/enbility/eebus-go v0.7.1-0.20260720111250-363db3c5c262
github.com/enbility/ship-go v0.6.1-0.20260720110450-0aa90f64ac76
github.com/enbility/spine-go v0.7.1-0.20260629113257-b3bcc643f323
```

All three are overridden via `replace` directives to forks maintained by
evcc's author (`andig/eebus-go`, `andig/ship-go`, `andig/spine-go`) — i.e.
evcc actively patches upstream rather than pinning an old release the way
this binding currently pins `org.openmuc.jeebus:ship:2.3.0` /
`spine:4.0.1`.

Files read: `server/eebus/{eebus,service,connector,certificate,pairing,
scenarios,types,helper}.go`, `hems/eebus/{eebus,events,types}.go`,
`hems/config.go`, `charger/eebus-{evse,ohpcf}.go`, `meter/eebus{,_events}.go`,
`util/templates/includes/eebus.tpl`, `docs/agents/hardware-integrations.md`,
`AGENTS.md`.

## Stack and Service Structure

evcc runs a single process-wide EEBus service, a Go singleton (`Instance()`
in `server/eebus/eebus.go`), started once via `sync.OnceValue`. All chargers,
meters, and the HEMS adapter register themselves against this one instance
rather than each owning their own SHIP/SPINE stack.

## Local Entity Model — Three Entities, Both Roles at Once

`NewServer()` creates exactly three local SPINE entities on one local
device:

- **Entity 1, type CEM** — hosts the "Customer Energy Management" use cases
  toward EVSEs and meters: `EvseCC`, `EvCC`, `EvCem`, `OpEV`, `OscEV`,
  `EvSoc`, `OHPCF`. Also hosts the "Monitoring Appliance" use cases toward
  meters: `MGCP`, `MPC`, `MDT`.
- **Entity 2, type CEM ("Controllable System")** — hosts `CS-LPC` and
  `CS-LPP`. evcc is the **server** role here: it receives limits from an
  external Energy Guard / SMGW. This is the role this binding's
  `EEBusLpcServerUseCase` / `EEBusLppServerUseCase` play.
- **Entity 3, type GridGuard ("Energy Guard")** — hosts `EG-LPC` and
  `EG-LPP`. evcc is the **client** role here: it sends limits to a
  connected Controllable System, e.g. a heat pump compressor via OHPCF
  (`charger/eebus-ohpcf.go`).

evcc therefore plays both sides of the client/server split this binding
worked out in ADR-021 — but does it via two separate local entities inside
one process, rather than via two separate Things/bridges.

## Scenario Constants — Same Failure Class as ADR-018/019

`server/eebus/scenarios.go` defines named constants for every use case's
spec scenario numbers (MGCP, MPC, MDT, LPC, LPP, OPEV, OSCEV, EVCEM, EVSOC),
with this comment on top:

```text
Spec scenario numbers diverge between use cases (e.g. MPC scenario 1 =
active power, MGCP scenario 1 = power factor; MPC scenario 2 = energy,
MGCP scenario 2 = active power). Passing the wrong number to
IsScenarioAvailableAtEntity gates reads on the wrong feature.
```

This is the same underlying failure class as this binding's limitId
collision between LPC and LPP (ADR-018/019) — a shared underlying SPINE
feature exposing per-use-case numbers that must not be confused. evcc
prevents it with hard, documented constants up front rather than
discovering it via a live cross-talk bug.

## Certificate, SKI, and Pairing

`certificate.go` / `pairing.go`:

- Standard X.509 cert; SKI is extracted from the certificate's
  `SubjectKeyId` field. There's a user-facing error hint for the case of an
  old certificate whose SKI format is no longer accepted by stricter
  validation introduced later — a bug class similar to issues this binding
  has hit via jeebus's older, differently-validated SKI handling.
- evcc supports **two parallel trust models**: the classic "SKI configured
  up front" model, and the **SHIP Pairing Service** (QR-code pairing with a
  hex secret, plus a replay-protection ring buffer persisted to settings).
  Each trusted device is tagged with a `PairingSource` (`paired` vs. `ski`)
  so the UI can show how trust was established. This binding currently only
  has the SKI-configured model.

## Device Registration Pattern

Every charger/meter/HEMS adapter calls `RegisterDevice(ski, ip, device)`
against the singleton, then blocks on `Connector.Wait(ctx)` (90s timeout)
until the SHIP/SPINE connection is up. Conceptually close to this binding's
pairing flow, minus the Thing/handler framework around it.

## Failsafe State Machine — Spec-Paragraph-Level Detail

`hems/eebus/eebus.go` implements LPC/LPP failsafe handling with direct
comments citing spec clause numbers (LPC-911, LPC-916, LPC-918/919/920,
LPC-921, and LPP equivalents): heartbeat loss enters failsafe immediately;
heartbeat return grants a 120-second grace window for a _fresh_ limit write
from the Energy Guard before falling through to unlimited. This is the same
level of state-machine timing precision this binding worked through in
ADR-019.

## Use Case Coverage

Substantially broader than this binding's current MPC/LPC/LPP-only scope:

- EV charging control: `EVSECC`, `EVCC`, `EVCEM`, `OPEV`, `OSCEV`, `EVSOC`.
- Heat pump flexibility: `OHPCF` (with its own reboost loop and an
  enable/disable state machine on top of the compressor's own process
  states).
- Grid connection point monitoring: `MGCP`.
- Domestic hot water temperature: `MDT`.
- Load limitation both ways: `CS-LPC`/`CS-LPP` (server) and
  `EG-LPC`/`EG-LPP` (client).

This is roughly a dozen use cases against evcc's specific domain (wallbox /
PV-surplus charging / §14a-EnWG), not the full ~43-use-case EEBus catalogue.

## Takeaways for Future Discussion (not decisions)

- **Scenario/limitId constants as a named, documented block** (like
  `scenarios.go`) is a pattern this binding could adopt to make the
  LPC/LPP-vs-shared-feature failure class harder to reintroduce elsewhere.
- **Dual local-entity client/server structure** is a different way to model
  "the same binding plays both EEBus roles" than this binding's two-Thing
  approach — worth a pros/cons pass if the binding ever needs to originate
  limits (Energy Guard role) toward a third party, not just receive them.
- **SHIP Pairing Service (QR/secret-based trust)** alongside SKI-configured
  trust is a UX evcc offers that this binding does not currently have.
- **Actively forking the upstream Java equivalent** (were one to exist) is
  not directly transferable — this binding is constrained by the
  jeebus.ship/jeebus.spine "no changes without human approval" project
  rule — but it is worth noting evcc treats "patch upstream" as a normal
  part of maintaining an EEBus integration, not a last resort.
