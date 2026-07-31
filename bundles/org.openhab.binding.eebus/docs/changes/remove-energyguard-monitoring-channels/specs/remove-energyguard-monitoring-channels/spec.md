# Delta for EnergyGuard Monitoring Channels

## REMOVED Requirements

### Requirement: EnergyGuard (Client-role) LPC/LPP monitoring Channels

(The EnergyGuard/Client role only ever writes a limit via a tagged Item - see the write-path
Requirement in `docs/changes/lpc-lpp-client-role-channels/`, unaffected by this change. Reading
a peer's own reported `LoadControlLimitListData` back into a dynamic/static `limit-active`/
`limit-value` Channel on `eebus:oh-eg-entity`/`eebus:oh-entity` duplicated data already owned by
that tagged Item and is removed by `docs/ADR/031-remove-energyguard-monitoring-channels.md`. The
mirror-image Requirement on the Controllable-System side - a Channel showing a value _received_
from a peer with no local Item - is explicitly retained; see `docs/changes/cs-failsafe-status-
channel-and-startup-sync/` and `docs/changes/oh-cs-entity-static-channels/`, both unaffected.)

## MODIFIED Requirements

### Requirement: `eebus:oh-eg-entity` Thing type

The binding MUST offer `eebus:oh-eg-entity` as an additional (non-exclusive) Entity Thing type
under `eebus:oh-device`, whose mere presence unconditionally contributes LPC+LPP Client to the
Bridge's derived local Use Case set, with no Channels of its own.
(Previously: the same Thing type additionally declared a static `lpc`/`lpp` `<channel-groups>`
block, per `docs/ADR/026-oh-eg-entity-static-channels.md`.)

#### Scenario: oh-eg-entity has no Channels

- GIVEN an `eebus:oh-eg-entity` Thing configured under an `eebus:oh-device` Bridge
- WHEN the Thing is created
- THEN it has no Channel Groups and no Channels, at creation or at any later point

#### Scenario: oh-eg-entity still guarantees LPC+LPP Client

- GIVEN an `eebus:oh-eg-entity` Thing configured under an `eebus:oh-device` Bridge
- WHEN the Bridge derives its local Use Case set at `initialize()`
- THEN LPC and LPP Client are both included, unconditionally, with no checkbox configured

---
