# ADR-055: Rebuild paired local Bridges when a Bridge restarts

## Status

Accepted (2026-10-07) - implemented, not compiled or live-tested yet.

## Context

Two `eebus:oh-device` Bridges in the same openHAB (for example HEMS and Energy Guard) pair with each
other over SHIP. Any configuration change of one Bridge (SKI, port, child Entity) rebuilds it
(`dispose()` + `initialize()`).

Observed on 2026-10-07: after only the HEMS Bridge restarted, the Energy Guard Bridge never
reconnected. The jeebus.ship client threw an NPE in `ShipCommunication.onDisconnect`
(`Device.getNodeManagement()` is null), the new HEMS dial to the Energy Guard aborted after 10 s
("peer not authenticated"), and no side dialed again. The Energy Guard limit never reached the HEMS
(`Lpc_Limitactive` stayed NULL). Restarting both Bridges is the manual workaround. This can recur
whenever one Bridge is reconfigured, so it needs a structural answer.

## Decision

After a Bridge has bound its SHIP server, it rebuilds every other running Bridge of this binding in the
same JVM that is paired with it (either side lists the other's SKI as trusted), using the existing
debounced `onEntityChanged()` rebuild.

- A peer that itself (re)started less than 30 s ago is skipped. This prevents ping-pong between two Bridges.
- jeebus.ship and jeebus.spine are not changed.

## Consequences

- A configuration change on one Bridge briefly restarts its paired local Bridges as well (about 10 s of
  reconnect time for their partners).
- Real third-party devices are never restarted, only Bridges of this binding.
- It is a workaround for the library's disconnect handling, not a fix. The NPE in
  `ShipCommunication.onDisconnect` should be reported upstream (needs human approval, project rule).
- Unverified hypothesis: the stale peer state is the cause. If the failure still occurs after this change, the
  cause lies elsewhere (candidates: the mDNS registry ignoring a re-announced service, the
  simultaneous-dial bug with "Actively Connect" on both Bridges).
