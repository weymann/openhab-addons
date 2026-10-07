# Proposal: HEMS Convenience Thing (`oh-hems-entity`)

## Intent

The user wants one convenience Thing that makes the binding behave like a real EEBus HEMS: it
receives limits from a CLS gateway as Controllable System, and acts as Energy Guard towards a
wallbox and a heat pump, each with its own limit. Wallbox and heat pump pair with the HEMS like
any EEBus device. See ADR-053.

## Scope

In scope (Phase 1):

- New Thing type `eebus:oh-hems-entity` under `eebus:oh-device`.
- Several local SPINE Entities per Bridge: Monitoring (MPC/MGCP Client), Controllable System
  (LPC/LPP Server), Energy Guard wallbox, Energy Guard heat pump.
- Per-partner scoped Energy Guard instances with their own writable Channel Groups.
- Limit distribution through an openHAB rule over Channels.

Out of scope:

- Any change to `jeebus.ship` / `jeebus.spine`.
- A built-in algorithm distributing the gateway limit.
- EV use cases (EVSECC, EVCC, EVCEM, OPEV, OSCEV, CEVC, EVSOC, EVCS) - Phases 2/3.
- Heat pump use case OHPCF - Phase 4.
- White goods Entity (`flexibleStartForWhiteGoods`) - no limit role, later.

## Open Questions

- Does a real wallbox / heat pump accept an Energy Guard Entity of type `GridGuard` or `CEM`?
  Only testable against the real devices.
- Number of Energy Guard Entities is fixed at two for now (user decision 2026-10-07).
