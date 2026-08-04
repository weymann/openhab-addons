# Manual Test 1: SHIP Pairing With a Real Peer (Hager Energy S10)

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

## Known Limitation: Trust Must Already Be Set When the Peer Is (Re-)Discovered

`ShipCommunication`'s outbound connection attempt is only triggered from inside its mDNS
`serviceAdded(ip, ski)` callback (`ShipCommunication.java` in jeebus.spine, around lines
103-121): `device.getConnectionHandler().newConnection(ip)` fires there, gated on
`connectClientsTo == TRUSTED && skisToTrust.contains(ski)`. `withTrustedSkis(...)` (driven by
`EEBusHandler#recomputeTrustedSkis()` whenever an `eebus:peer` Thing is added or removed) only
updates the trust set for **future** `serviceAdded` events - it does not retroactively trigger a
connection attempt for a peer that mDNS already discovered earlier, while its SKI was still
untrusted.

Following the steps below in order - Bridge online, read `localSki`, _then_ add the peer
Thing - means the peer's SHIP service is very likely discovered, and its trust check
therefore fails, before the peer Thing exists at all. **After** adding the peer Thing,
restart the `eebus:service` Bridge (disable/enable, or edit-and-save) so `ShipCommunication`
is rebuilt with the trust set already populated (`currentPeerSkis()` is read directly into
the constructor chain in `EEBusHandler#startShipSpine()`) before its fresh mDNS browse runs.
This is the single most likely explanation if nothing happens after following the steps once —
it is not yet confirmed whether a later mDNS re-announcement would eventually trigger the
connection on its own without a restart.

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

1. Restart the `eebus:service` Bridge now (disable/enable, or edit-and-save) - see
   "Known Limitation" above for why this is required and not optional.

1. `ShipCommunication` (jeebus.ship) is expected to establish the connection on its own
   from here - per the SHIP spec, a SHIP node searches for and connects to SHIP nodes
   with trusted SKI values; no manual "connect" action exists in this binding.

1. Enable debug logging in the Karaf console to observe the handshake. `EEBusHandler`
   itself does not log per-peer connect/disconnect events - the actual SHIP handshake
   trace lives in the embedded `org.openmuc.jeebus.ship` classes (not relocated, see
   ADR-001), most usefully `StateMachine.java`'s `"{} --> {}"` state-transition line at
   DEBUG. `org.openhab.binding.eebus` alone will not show it:

   ```shell
   log:set DEBUG org.openhab.binding.eebus
   log:set DEBUG org.openmuc.jeebus
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

# Manual Test 2: SHIP Pairing Between Two Self-Built EEBus Services (LPC Server + LPC Client)

Status: Draft - not yet verified against a real Karaf runtime.
Scope: SHIP-level pairing only. No LPC scenario exchange happens in this test - see
"Known Limitation: LPC Client Role Is Not Implemented" below.

## Why This Runbook Exists

Test 1 above pairs against a real, external device. This test instead pairs two
self-built `eebus:service` Bridges against each other - useful when no real EEBUS
device is available for testing, or to isolate binding-side pairing behavior from a
third-party device's own quirks.

## Known Limitation: LPC Client Role Is Not Implemented

The LPC Server role (Controllable System actor) is fully implemented
(`EEBusLpcServerUseCase`, all 3 mandatory scenarios). The LPC Client role (Energy
Guard actor) is not: `EEBusHandler#startShipSpine()` only wires a client-role UseCase
for `MPC` (`EEBusMpcClientUseCase`); any other entry in `supportedUseCasesClient`,
including `LPC`, is only logged, not acted on:

```text
Configured to detect use cases [LPC] - not yet implemented, see CONCEPT.md §7.3
```

Consequently this test exercises **SHIP-level pairing only**. No `LPC.consumptionLimit`
write, no `EEBusLimitControlStateMachine` transition, and no SPINE scenario exchange
will occur, regardless of how carefully the steps below are followed. To actually
exercise the LPC scenario, either pair the LPC Server instance against an external
Energy Guard implementation (e.g. the `eebus-go` reference code), or implement the LPC
Client role first (`$Spec`/`$Architect`/`$Dev`).

## Prerequisites

- Two separate Karaf runtimes, each with this binding installed, ideally on two
  separate hosts/VMs/containers on the same network segment - mDNS multicast
  discovery requires the same broadcast domain. Running both on one host has not been
  verified against a real Karaf runtime; if attempted, the two `eebus:service` Bridges
  must at least use different `port` values (default is `4711` on both).
- Karaf console access on both instances for log inspection.

## Steps

1. On instance A, add an `eebus:service` Bridge. Fill in `vendorCode`, `deviceBrand`,
   `deviceModel`, `serialNumber`, `mdnsServiceInstance` with any identifying values for
   local testing (e.g. `mdnsServiceInstance = "Test CS"`). Under
   `supportedUseCasesServer`, select `LPC`. Leave `autoAcceptEnabled` at its default
   (`false`), to test the real SKI-based trust relationship rather than auto-accept.

1. On instance B, add an `eebus:service` Bridge the same way, with its own identifying
   values (e.g. `mdnsServiceInstance = "Test EG"`). Under `supportedUseCasesClient`,
   select `LPC` - this produces only the log line quoted above, see "Known Limitation".

1. Wait for both Bridges to go `ONLINE`, then read each one's `localSki` property
   (Bridge Properties tab): call it `SKI_A` on instance A and `SKI_B` on instance B.

1. On instance A, add an `eebus:peer` Thing under the A Bridge with `ski = SKI_B`.

1. On instance B, add an `eebus:peer` Thing under the B Bridge with `ski = SKI_A`.
   Unlike Test 1, both sides of the trust relationship are configured from within
   openHAB here - there is no external device UI to configure.

1. Restart both `eebus:service` Bridges now (disable/enable, or edit-and-save on each) -
   see Test 1's "Known Limitation: Trust Must Already Be Set When the Peer Is
   (Re-)Discovered" above. It applies here too, and is even more likely to bite: both
   Bridges typically go `ONLINE` (and start mDNS browsing) well before either peer Thing
   exists, so both sides' mutual discovery has almost certainly already happened with an
   empty trust set on both ends.

1. `ShipCommunication` (jeebus.ship) is expected to establish the connection on its own
   from here, same as in Test 1 - no manual "connect" action exists in this binding.

1. Enable debug logging in the Karaf console on both instances to observe the
   handshake. `org.openhab.binding.eebus` alone is not enough - see "What to Observe"
   below for why:

   ```shell
   log:set DEBUG org.openhab.binding.eebus
   log:set DEBUG org.openmuc.jeebus
   log:tail
   ```

## What to Observe

- **The binding's own logger scope is not sufficient.** `EEBusHandler` never logs
  per-peer connect/disconnect events - checked directly against the source, no such
  log line exists. The actual SHIP handshake trace lives entirely in the embedded
  `org.openmuc.jeebus.ship` classes (private/embedded per ADR-001, not relocated, so
  they keep their own logger namespace). The most useful single line is
  `StateMachine.java:139`, `LOGGER.debug("{} --> {}", state, newState)` - this only
  appears once `org.openmuc.jeebus` (or narrower, `org.openmuc.jeebus.ship`) is also
  set to `DEBUG`. With only `org.openhab.binding.eebus` enabled, a completely silent
  trace is expected and proves nothing either way about whether pairing is being
  attempted.
- The `eebus:peer` Thing status is **not** a reliable signal either: `EEBusPeerHandler`
  sets it to `ONLINE` as soon as its Bridge is `ONLINE` (`EEBusPeerHandler.java`, lines
  100-103), regardless of whether a SHIP connection to that specific peer actually
  exists.
- With `org.openmuc.jeebus` at DEBUG, follow the SHIP handshake state sequence via the
  `"{} --> {}"` lines (jeebus.ship packages `cmi`, `smehello`, `smeproth` - Connection
  Mode Initialization, then SME_HELLO, then Protocol Handshake). If the connection
  drops before reaching `HelloOk`/`ProtHOk`, the SKI trust relationship is not set up
  correctly yet - a mistyped SKI is the most common cause. If no state transition
  appears at all even with this scope enabled, the two instances are likely not
  discovering each other via mDNS in the first place (see "Known Open Risk" below).
- On instance A (server), expect a log line for `EEBusLpcServerUseCase` registration
  during `startShipSpine()`. Separately, `EEBusLimitControlStateMachine` logs an
  `INIT -> UNLIMITED_AUTONOMOUS` transition on Bridge startup - this is the LPC state
  machine's own local default-state self-transition and happens regardless of any peer
  connection. Do not read it as evidence that pairing succeeded.
- On instance B ("client"), expect the "not yet implemented" log line quoted above -
  this confirms the known gap, not a setup mistake.
- SPINE-level `NodeManagement`/use-case discovery should still exchange generically
  (instance A advertises LPC as a use case) even though instance B's Java side does not
  act on it - this is protocol-level bootstrapping, independent of the binding's own
  client-role wiring.

## Known Bug Encountered: NPE in jeebus.ship's Double-Connection Handling Blocks Pairing

Confirmed against a real run on a single Raspberry Pi (openHABian), both instances as
two `eebus:service` Bridges: after fixing the trust-ordering issue above (restarting
both Bridges once both peer Things existed), mDNS discovery, the outbound connection
attempt, and the TLS handshake all worked - `ShipCommunication` logged
`"Ship service with address ... detected"`, then `"Opening new connection to device"`,
then a real WebSocket/TLS connection came up on both sides. The SHIP state machine then
reaches `CMI_INIT_START` -> `CMI_STATE_SERVER_WAIT` and gets no further - the connection
is torn down repeatedly, with this exception on the server side:

```text
java.lang.NullPointerException: Cannot invoke "org.bouncycastle.asn1.x509.SubjectKeyIdentifier.getKeyIdentifier()" because "ski" is null
    at org.openmuc.jeebus.ship.node.KeyManagement.encodeSkiAsString(KeyManagement.java:181)
    at org.openmuc.jeebus.ship.node.websocket.WebSocketHandler.getPeerSki(WebSocketHandler.java:182)
    at org.openmuc.jeebus.ship.node.ShipNodeImpl.isDoubleConnection(ShipNodeImpl.java:433)
    at org.openmuc.jeebus.ship.node.websocket.server.ShipServerHandler.handleHttpRequest(ShipServerHandler.java:162)
```

**Root cause** (read directly from `WebSocketHandler.java:162-186`, jeebus.ship):
`getPeerSki()` calls `sslHandler.engine().getSession().getPeerCertificates()`, catches
`SSLPeerUnverifiedException` (thrown when the TLS handshake has not yet produced the
peer's certificate) but leaves `certificate`/`ski` as `null` in that case - and then
unconditionally calls `KeyManagement.encodeSkiAsString(ski)`, which does
`ski.getKeyIdentifier()` without a null check. Since both instances here are symmetric
SHIP peers (each is client _and_ server at once), each side ends up briefly holding two
parallel connections to the other and has to resolve the resulting "double connection"
via `ShipNodeImpl.isDoubleConnection()` - which calls `getPeerSki()` to identify which
connection is redundant. Whether this crashes is a timing race: in the same log capture,
one of the two simultaneous "double connection" resolutions succeeded (logged a normal
`"Trust level of device with SKI ... is 32"` line), the other hit the NPE because its
TLS handshake had not yet produced a verifiable peer certificate at that point.

This is a genuine bug in `jeebus.ship`, not a configuration mistake in this test. It
most plausibly went unnoticed until now because Test 1's real external device (Hager
S10) likely does not trigger the same simultaneous-mutual-connection race that two
symmetric self-built peers reliably do.

**Status: not fixed in jeebus.ship** (may not be changed without prior human approval,
project instruction) - see `WebSocketHandler.java:162-186`, `KeyManagement.java:181`, and
`ShipNodeImpl.java:433` (jeebus.ship) for the exact locations if a fix is approved later.

**Binding-side workaround added:** the crash is triggered specifically by both sides
actively dialing each other at the same moment - a legitimate SHIP scenario
(`ShipCommunication`'s `serviceAdded()` callback calls `newConnection(ip)` on either side
once it discovers a trusted peer), but one where jeebus.ship's simultaneous-connection
resolution has the bug above. `ShipCommunication` already exposes a public
`withConnectClientsTo(ConnectClientsTo)` method with a `NONE` value (purely passive, never
dials out) - no jeebus.ship/jeebus.spine change needed to use it. The binding now exposes
this as a new, advanced `eebus:service` config parameter, `connectToPeers` (default
`true` - unchanged behavior). Setting it to `false` on **one** of the two instances in this
test makes that side passive; only the other side then dials out, so the two sides never
connect simultaneously and the race - and the crash - do not occur. Retest with this set
on instance B ("client") and `true` (default) left on instance A: instance A should then
be the one to `serviceAdded()`-detect and dial instance B, with no reverse dial to race
against. This is a test-only workaround, not a general fix - do not disable
`connectToPeers` against a real third-party device, which may rely on openHAB dialing out
(e.g. Test 1's Hager S10, if it never initiates).

## Known Open Risk

Resolved by a real single-host run: mDNS announcement/discovery for two `eebus:service`
Bridges on one machine is **not** a blocker. `avahi-browse -r _ship._tcp` from that same
host showed both instances correctly, each with its own hostname (JmDNS auto-suffixes
`-1` etc. when two local services generate the same default hostname - harmless) and the
exact SKI configured in openHAB's own UI in its TXT record. jeebus.ship's own embedded
JmDNS-based browser (used internally by `ShipCommunication`, separate from both
`avahi-daemon` and this binding's own `EEBusMdnsBrowser`) was also confirmed working
directly, via its own `"Ship service with address ... detected"` log line firing
correctly once the trust-ordering issue above was fixed (Bridge restart after both peer
Things existed). The actual current blocker for this test is the NPE bug documented
above, encountered only after mDNS/TLS both succeeded.
