# ADR-053: `oh-hems-entity` - one convenience Thing for a complete HEMS (multiple local SPINE Entities)

## Status

Proposed (Phase 1 implemented, not yet compiled or live-tested)

## Context

`$Concept` discussion 2026-10-07. The user wants a single convenience Thing "HEMS" that makes the
binding act like a real home energy management device (comparable to evcc or the Hager/E3DC S10):

- a **Controllable System** (LPC+LPP Server) that receives limits from the CLS gateway,
- one **Energy Guard** (LPC Client; there is no LPP for wallbox or heat pump) each for a wallbox and a heat pump, so that an EEBus
  wallbox and an EEBus heat pump can pair with the HEMS and receive limits,
- the Monitoring Appliance roles (MPC, MGCP Client) next to them.

The limit from the CLS gateway (for example 4200 W) must be **distributed** over the devices, so
the wallbox and the heat pump have to receive **different** values.

Findings that shape the design:

- The binding builds exactly **one** shared local SPINE Entity per Bridge (`startShipSpine()`,
  ADR-042). Every use case of every child Thing lands on it. Different values per Energy Guard
  partner are therefore impossible today, and ADR-047 deliberately fans one value set out to
  **all** partners.
- The discovery files of the evcc HEMS and the Hager S10 both show the target structure: separate
  Entities for ControllableSystem and EnergyGuard roles inside one Device (evcc: CEM / CS /
  GridGuard; S10: CS on Entity 6, EnergyGuard actors on Entities 1-4, white goods on Entity 5).
- `jeebus.spine` already supports several top-level Entities per Device
  (`DeviceBuilder#addEntity`, `EntityBuilder#applyToDevice`), see ADR-027. Building the complete
  `Device` before `connect()` never touches the still-open NodeManagement notification gap
  (`openmuc/jeebus.spine#11`). **No change to `jeebus.ship` / `jeebus.spine` is needed.**
- `NodeManagement#addUseCaseListener` stores listeners in a list, so several Energy Guard
  instances with the same use-case name can coexist; each receives all partners and has to filter.

## Decision

### 1. New Thing type `eebus:oh-hems-entity` (child of `eebus:oh-device`)

One Thing, served by the existing `EEBusOhEntityHandler` / `EEBusOhEntityConfiguration` (same
pattern as `oh-cs-entity`, `oh-eg-entity`, `oh-mpc-entity`). At most one per Bridge; if several
exist, the first one found is used and a WARN is logged.

Config parameters:

| Parameter | Meaning |
|---|---|
| `gatewaySki` | SKI of the CLS gateway (Energy Guard partner of the Controllable System). |
| `wallboxSki` | SKI of the wallbox. Blank = the wallbox Energy Guard Entity still exists (so the discovery shows it before pairing) but is bound to no device. |
| `heatPumpSki` | SKI of the heat pump. Blank = the heat pump Energy Guard Entity still exists but is bound to no device. |
| `entityType` | SPINE entityType of the Controllable System Entity (default `CEM`, ADR-042 list). |
| `egEntityType` | SPINE entityType of the Energy Guard Entities (default `GridGuard`, allowed `CEM`/`GridGuard`, ADR-042). |
| `failsafe*Seed*` | Failsafe seeds of the Controllable System, as on `oh-cs-entity`. |

The three SKI parameters are offered as a selectable list of the Bridge's **trusted** SKIs
(`EEBusSkiOptionProvider`; free text stays possible). `ski` auto-selection does not apply (there are
up to three SKIs, all optional).

Status: the Thing is `ONLINE` when the Bridge is online and every **configured** SKI is trusted by
the Bridge - also when no SKI is configured at all. Unlinked roles are shown as the status
description of the `ONLINE` state:

| Situation | Status description |
|---|---|
| `gatewaySki` blank | `Energy Guard not configured` |
| `wallboxSki` and `heatPumpSki` both blank | `Neither wallbox nor heatpump configured` |
| both of the above | both texts, separated by a semicolon and a space |
| a configured SKI is not trusted | `OFFLINE` / `CONFIGURATION_PENDING` |

### 2. Several local Entities, built before `connect()`

`EEBusHandler#deriveLocalUseCases` now returns a list of local Entity specifications
(`entityType` + use cases) instead of one shared Entity:

| Local Entity | Created when | Use cases |
|---|---|---|
| Legacy shared Entity | children `oh-entity`, `oh-cs-entity`, `oh-eg-entity`, `oh-mpc-entity` contribute use cases | unchanged (ADR-027/042) |
| HEMS Monitoring (type `CEM`) | `oh-hems-entity` present | MPC Client, MGCP Client |
| HEMS Controllable System (type `entityType`) | `oh-hems-entity` present | LPC Server, LPP Server |
| HEMS Energy Guard Wallbox (type `egEntityType`) | `oh-hems-entity` present (bound to `wallboxSki`; blank = bound to no device) | LPC Client |
| HEMS Energy Guard Heat Pump (type `egEntityType`) | `oh-hems-entity` present (bound to `heatPumpSki`; blank = bound to no device) | LPC Client |

All Entities are added in one builder chain (`...applyToDevice().addEntity()...`) before the
Device is connected. The legacy shared Entity is only built when some legacy child contributes a
use case, so a HEMS-only Bridge has no empty extra Entity.

### 3. Energy Guard instance per target device

`AbstractEEBusLimitEnergyGuardUseCase` (and `EEBusLpcClientUseCase` / `EEBusLppClientUseCase`)
get an optional **target scope**:

- `partnerSki`: `onUseCasePartnersFound` ignores every partner whose SKI (resolved from the
  communication address through the mDNS browser) differs. Without it the behaviour is exactly
  the ADR-047 fan-out.
- `channelGroupPrefix` (`wallbox` / `heatpump`): the Channel Group this instance's write hook is
  registered under (`wallbox-lpc`, `heatpump-lpc`). Without it: `lpc` / `lpp` as before.
- With a target scope the tagged-Item write path is **not** used (ADR-052 made Channels the
  primary path; Item tags carry no device discriminator). Commands on the scoped Channels are
  written only to that one partner.

`EEBusOhEntityHandler#handleLimitChannelCommand` accepts the group ids `lpc`, `lpp` and
`<prefix>-lpc` / `<prefix>-lpp`.

### 4. Channels of the HEMS Thing

| Channel Group | Type | Direction |
|---|---|---|
| `lpc`, `lpp` | `lpc`, `lpp` (Controllable System) | read-only: limit received from the CLS gateway, failsafe, state, heartbeat |
| `wallbox-lpc` | `lpc-eg` | writable: limit sent to the wallbox |
| `heatpump-lpc` | `lpc-eg` | writable: limit sent to the heat pump |
| `mpc`, `mgcp` | `mpc`, `mgcp` | read-only measurements |

The channel groups carry explicit labels in the Thing type ("CLS Gateway - LPC", "Wallbox - LPC (consumption limit sent to wallbox)", "Heat Pump - LPC (...)"), so the UI shows which LPC group belongs to which device. Wallbox and heat pump have no LPP Energy Guard.

### 5. Distribution of the limit: openHAB rule, no built-in algorithm

The Controllable System publishes the limit received from the gateway on `lpc#limit-value`
(and `lpp`). An openHAB rule splits it, for example 4200 W into 3000 W (wallbox) and 1200 W
(heat pump), and commands `wallbox-lpc#limit-value` / `heatpump-lpc#limit-value`. The binding does
not check that the partial limits add up to the gateway limit.

### 6. Resolver changes in `EEBusHandler`

- `ohEntityHandlerForSki(ski)` also matches a `oh-hems-entity` whose `gatewaySki` equals `ski`,
  so the Controllable System publishes the gateway's limit on the HEMS Thing.
- `ohEgEntityWriteSourceHandler()` additionally resolves the HEMS Thing for scoped Energy Guard
  instances.

## Consequences

- One Thing gives the full HEMS structure; wallbox and heat pump pair like any EEBus device and
  receive their own limits.
- ADR-042's "one shared Entity" restriction is lifted **only** for the HEMS Thing; legacy children
  behave as before.
- ADR-047 is unchanged for `oh-eg-entity` (fan-out). The HEMS Energy Guards are per-partner by
  design, which is what the distribution requires.
- Per-Entity legality of use cases (ADR-042's deferred consistency check) is only covered through
  the fixed allowed lists of `entityType` / `egEntityType`.
- Each additional Energy Guard Entity adds its own outgoing heartbeat (ADR-035) and bindings
  (ADR-034/039); startup load grows accordingly.
- Not covered (later phases, in this order): EVSECC, EVCC, EVCEM, OPEV (Phase 2); OSCEV, CEVC,
  EVSOC, EVCS (Phase 3); OHPCF (Phase 4); white goods as a further Entity without limits.
- `jeebus.ship` / `jeebus.spine` are not changed.
- Known limitations of Phase 1: MPC/MGCP data on the HEMS Thing comes from the gateway device only
  (the Thing is resolved by `gatewaySki`); a second Controllable System Entity (for example a
  separate `oh-cs-entity` under the same Bridge) is not supported; the Energy Guard Entities do not
  feed back the partner's reported status onto their Channels (same as ADR-052).

## Not yet compiled or live-tested

Implemented without a Maven build (Java 11 sandbox only). Needed: `mvn clean install` with
Spotless, then a live test with a CLS gateway simulator (Controllable System receives 4200 W) and
two Energy Guard partners with different limits, checking `limitId` resolution, heartbeat and
`BINDING_NECESSARY` handling per Entity.
