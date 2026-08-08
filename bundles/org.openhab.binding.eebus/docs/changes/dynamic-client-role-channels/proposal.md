# Proposal: Dynamic Channels for Client-Role Use Cases (MPC first)

## Intent

CONCEPT.md §4.2 establishes that Client-role use case data (openHAB reading a paired peer's
values, as opposed to the Server-role where openHAB _offers_ data) belongs on dynamically
created Channels on the `eebus:oh-peer` Thing, not on Item metadata. §4.2.2 resolved the four
open design questions this raised (channel-creation mechanism, gating, visibility, structure),
but no code exists yet: `EEBusMpcClientUseCase#applyMeasurement()` already reliably reads a
paired peer's total power value, but only logs it —

```java
logger.debug("MPC.power = {} W for oh-peer '{}' (not yet delivered - dynamic Channels not
implemented, see CONCEPT.md §5.4.1)", watts, ohPeerThingUid);
```

This change closes that gap for MPC, the only currently implemented Client-role use case, and
establishes the general mechanism (channel-type + `editThing()`, gating, persistence, use-case
visibility property) that later Client-role use cases (LPC, LPP, MGCP, ...) will reuse.

## Scope

In scope:

- A static `channel-group-type` for MPC (`mpc`) with one `channel-type` inside it
  (`mpc-power`, `Number:Power`), declared in `thing-types.xml` for `oh-peer`.
- `EEBusOhPeerHandler` gains the ability to create that Channel Group/Channel at runtime via
  `editThing()`, and to update its state — replacing the `ohPeerThingUidResolver` function
  currently passed into `EEBusMpcClientUseCase` with direct access to the owning
  `EEBusOhPeerHandler` (per CONCEPT.md §4.2.2 point 1).
- Gating: the Channel is only created for a peer if MPC is both configured in the parent
  `eebus:service` Bridge's `supportedUseCasesClient` _and_ actually detected for that specific
  peer via SPINE (`onUseCasePartnersFound`) — per CONCEPT.md §4.2.2 point 2.
- The Channel is created once, on first successful resolution of the peer's power measurement
  ID, and is never removed by `unpair()` or by the peer becoming unreachable — per CONCEPT.md
  §4.2.2 "Channels bleiben bestehen".
- A Thing property on `eebus:oh-peer` recording that MPC was detected for this peer (key
  `mpc`, value `server` — see "Open Questions" below for the exact semantics of the value).

Out of scope (deferred to a later change, once a second Client-role use case is implemented):

- LPC/LPP Client-role channels. CONCEPT.md §5.4.3 now holds a primary-source-verified
  candidate channel table (`limit-active`/`limit-value`/`limit-duration`, from
  `EEBus_UC_TS_LimitationOfPowerConsumption_V1.0.0_public.pdf` Tables 3/22/23) for when that
  work starts, but no LPC Client-role `UseCase` class exists yet to attach it to.
  MGCP/other catalog entries: not even scenario-level primary-source research done yet
  (CONCEPT.md §5.4.2).
  - A general-purpose `ChannelTypeProvider` for arbitrary, only-known-at-runtime use cases.
    CONCEPT.md §4.2.2 point 1 explicitly defers this until the number of Client-role use cases
    no longer fits a small, hand-maintained `channel-type` catalog.

## Open Questions

- **Semantics of the use-case-visibility property's value** (CONCEPT.md §4.2.2 point 3,
  flagged there as unresolved and still not answered by the user as of this proposal): does
  `server`/`client` describe the actor role the **peer** plays for that use case (as SPINE
  discovery reports it), or the role **openHAB itself** plays? This proposal proceeds on the
  first reading — value = the peer's actor role — because CONCEPT.md §4.2.2 point 3 frames the
  property's purpose as making a peer's _otherwise invisible, per-peer discovered_ use cases
  visible ("ohne das Pairing sind die Use-Cases nicht bekannt"), which is peer-specific
  information; "which role openHAB itself plays" is a binding-wide constant already implied by
  which `UseCase` class is registered, not something worth recording per peer. For MPC this
  yields `mpc` = `server`, since our own `EEBusMpcClientUseCase#setup()` already requests the
  remote peer's actor as `"CEM"` (comment: "MPC Server Actor") while openHAB itself plays
  `MonitoringAppliance`, the Client actor. **`$Review`/the user should confirm this reading
  before it is relied on for a second use case**, since a single data point (MPC) cannot fully
  disambiguate it (see CONCEPT.md §4.2.2 point 3 for the full argument).

---

_Change ID: `dynamic-client-role-channels`. Builds on `decouple-oh-peer-config-from-pairing`
(pairing state, `EEBusOhPeerHandler.isPaired()`) and on the existing
`EEBusMpcClientUseCase`/`EEBusHandler` Client-role detection wiring. Domain: `dynamic-channels`
(new)._
