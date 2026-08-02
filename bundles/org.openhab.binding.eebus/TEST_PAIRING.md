# Manual Test: SHIP Pairing With a Real Peer (Hager Energy S10)

Status: Draft - not yet verified against a real Karaf runtime.
Scope: SHIP-level pairing only. Use-case (SPINE) consumption is a separate follow-up,
see `CONCEPT.md` §4.2/§5.4.

## Why This Runbook Exists

mDNS discovery (`_ship._tcp.local.`) and SHIP pairing are two independent layers.
A device showing up in a generic mDNS browser, or under the binding's `eebus:network`
Inbox, only proves the discovery layer works - it proves nothing about pairing.

`eebus:network` is a lightweight, zero-config anchor Bridge with no SHIP/SPINE identity of its
own (see `EEBusNetworkHandler`, ADR-003). A `eebus:peer` Thing added under it never opens a real
SHIP session, so it will show no traces and no channels regardless of anything else being
correct. Real pairing only happens under a fully configured `eebus:service` Bridge.

## Prerequisites

- A real EEBUS device visible via mDNS. Confirmed for this test: Hager Energy S10, SKI
  `dd8ba427c0a449180513fbb51bf6f0bbdbf0c821`, TXT record shows `type=CEM`, `register=false`
  (auto-accept is off on the device - normal and not a blocker for SKI-based trust, see
  SHIP TS Specification v1.1.0, section 5 "Registration").
- Access to the Hager device's own app/web UI, since the device-side half of trust
  establishment happens there, not in openHAB.
- Karaf console access for log inspection.

## Steps

1. Add a new `eebus:service` Bridge (not `eebus:network`). Fill in `vendorCode`,
   `deviceBrand`, `deviceModel`, `serialNumber`, `mdnsServiceInstance` with any
   identifying values for local testing - a vendor code officially assigned by the
   EEBUS Initiative is only required for real certification/interop, not for the
   connection itself. Leave `autoAcceptEnabled` at its default (`false`), matching the
   S10's own `register=false`.

1. Wait for the Bridge to go `ONLINE`, then read its `localSki` property (Bridge Properties
   tab). This is openHAB's own SKI.

1. On the Hager S10 side (its own app/web UI - outside openHAB), register `localSki` from
   step 2 as a trusted partner. The exact mechanism (pairing menu, QR code, app
   approval) is Hager-specific; consult Hager's own documentation. Without this step the
   S10 will not trust openHAB, regardless of the following steps.

1. Add an `eebus:peer` Thing under the new `eebus:service` Bridge (not under
   `eebus:network`) with `ski = dd8ba427c0a449180513fbb51bf6f0bbdbf0c821`. Creating this
   Thing is openHAB's half of the trust relationship - it feeds
   `EEBusHandler#recomputeTrustedSkis()` -> `ShipCommunication#withTrustedSkis(...)`.

1. Once both sides trust each other's SKI, `ShipCommunication` (jeebus.ship) is expected
   to establish the connection on its own - per the SHIP spec, a SHIP node searches for
   and connects to SHIP nodes with trusted SKI values; no manual "connect" action exists
   in this binding.

1. Enable debug logging in the Karaf console to observe the handshake:

   ```shell
   log:set DEBUG org.openhab.binding.eebus
   log:tail
   ```

1. Optional, only relevant once pairing succeeds: to also exercise SPINE use-case
   detection, tick `MPC` under the Bridge's `supportedUseCasesClient` (the only
   implemented client-role use case so far) and create an Item with
   `eebus="MPC.power" [peer="<peer-thing-uid>"]` metadata - channels for peers are not
   yet implemented, so this metadata path is currently the only wired way to consume
   data (see `CONCEPT.md` §4.2, §5.4.1).

## Known Open Risk

Whether the Hager S10 accepts a plain pre-shared-SKI trust relationship at all, or
requires its own commissioning/app-approval flow first, is not known from this
codebase. This binding does not implement PIN-based commissioning (`CONCEPT.md` §5.2,
"decided: not in v1"), so if the S10 requires that flow, pairing will not succeed via
the steps above until PIN-based commissioning is implemented.
