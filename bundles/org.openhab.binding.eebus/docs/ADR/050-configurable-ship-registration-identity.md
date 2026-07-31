# ADR-050: Configurable SHIP Registration Identity (`brand`/`type`/`id`/`model`)

## Status

Accepted

## Context

`EEBusHandler#startShipSpineLocked()` builds the local SHIP node's `ShipConfig` via
`org.openmuc.jeebus.ship.api.ConfigBuilder` (docs/ADR/049-configbuilder-ship-node-construction.md).
Only `withId(shipId)` was ever called on it; `withBrand(String)`, `withType(String)`, and
`withModel(String)` - all already present on the vendored `org.openmuc.jeebus:ship:3.0.1`
`ConfigBuilder` - were never called, so this Bridge's own SHIP mDNS TXT record (SHIP:7.3.2) has
always announced `ConfigBuilder`'s own defaults: `brand="jEEBus"`, `type="default"`,
`model="default"`.

The user compared this against a real device's mDNS TXT record (a Hager Energy S10, captured via
a third-party mDNS browser app) which correctly reports its own `brand`, `type`, `id`, and `model`,
and asked why this Bridge's own announcement does not do the same. Checked, confirmed: no
`jeebus.ship`/`jeebus.spine` change is needed at all - `ConfigBuilder` already exposes exactly
these four fields; this Bridge's own `startShipSpineLocked()` simply never populated three of
them, and used a value unrelated to this Bridge's own Thing identity for the fourth (`id`).

## Decision

`startShipSpineLocked()` now sets all four SHIP registration identity fields explicitly:

- `brand` -> `SHIP_BRAND`, a new fixed constant `"openHAB"`. This Bridge is the openHAB
  integration embedding `jeebus.ship`, not `jeebus.ship`/`jEEBus` itself, and not the physical
  device it fronts (that remains `EEBusConfiguration#deviceBrand`, used only for the SPINE Device
  address/certificate DN below - unrelated to this Bridge's own announced SHIP identity).
- `type` -> `SHIP_TYPE`, a new fixed constant `"CEM"` (Customer Energy Manager) - what this
  Bridge actually is on the EEBUS network.
- `id` -> `ohServiceId` (`thing.getUID().getId()`), this Bridge's own Thing UID id segment. Its
  computation is unchanged (`deriveLocalUseCases()` further down already needed exactly this
  value); it is now computed once, above the `ConfigBuilder` call, and used for both.
- `model` -> `OpenHAB.getVersion()` (`org.openhab.core.OpenHAB`, already imported in this file for
  `getUserDataFolder()`), the running openHAB core version.

`brand`/`type` are fixed constants, not new `EEBusConfiguration` fields - unlike
docs/ADR/044-configurable-devicetype.md's `deviceType`, there is no meaningful per-installation
choice here: this Bridge is always the openHAB integration, always a CEM. `id` and `model` are
derived from existing, already-available values (the Thing UID, the running core version), not
new config either.

`shipId` (`cfg.vendorCode + "-" + cfg.deviceModel + "-" + cfg.serialNumber`) is **not** removed
and **not** used for the SHIP registration `id` field anymore - it remains in use, unchanged, for
the certificate distinguished name (`"CN=" + cfg.deviceModel + "-" + cfg.serialNumber"`) and the
SPINE Device address (`Device.getBuilder()...withId("d:_n:" + shipId)` further down in the same
method) - see Consequences for why this now-visible split is accepted rather than unified.

## Consequences

### Positive

- This Bridge's own SHIP mDNS TXT record now correctly self-identifies as an openHAB CEM instead
  of announcing the embedded library's own generic defaults - matches how every real third-party
  SHIP device on the network already identifies itself (confirmed against a real Hager Energy S10
  capture).
- No `jeebus.ship`/`jeebus.spine` change - `ConfigBuilder` already supported this; confined to
  `EEBusHandler.java`.
- No new Thing configuration parameter, no `thing-types.xml` change - all four values are either
  fixed constants or already-available derived values.
- `ohServiceId` is now computed once and reused for both the SHIP registration `id` and
  `deriveLocalUseCases()`, instead of (as before) only existing for the latter.

### Negative

- The SHIP registration `id` (now `thing.getUID().getId()`) and the SPINE Device address /
  certificate DN (still built from `cfg.vendorCode`/`deviceModel`/`serialNumber` via `shipId`) are
  now two different identifiers for the same running node, where before both used `shipId`. This
  is intentional (the user asked specifically for the _registration_ `id` to be the Thing ID) but
  is a visible split worth knowing about when reading `startShipSpineLocked()` later.
- `EEBusConfiguration#vendorCode`/`deviceBrand`/`deviceModel`/`serialNumber` remain required
  Bridge config (validated in `initialize()`) purely for the certificate DN and SPINE Device
  address - `deviceBrand` in particular is not read anywhere else at all. Left as-is: out of scope
  for this change, and removing/relaxing that validation is a separate decision.
- Not yet compiled or live-tested - no local Maven/JDK in this environment, as with every other
  change in this project; verified only by manual review (targeted anchor-based edits, brace/paren
  balance check) and cross-referenced against the actually-vendored `ship:3.0.1` `ConfigBuilder`/
  `ShipConfig`/`TxtRecord` source (`git show v3.0.1:...` against the local `jeebus.ship` checkout,
  since the checked-out working tree itself sits at the older `v2.3.0` commit).

## Update 2026-10-05: `id` reverted to `shipId`

A real Hager Energy S10 closed the SHIP connection about 5 to 10 ms after the `accessMethods`
exchange on every attempt once the announced `id` was the Thing UID id segment
(`energy-guard-sim`) instead of `shipId` (`OPHAB-0001-0002`), which it had been paired with.
Decision: `id` is `shipId` again (`vendorCode-deviceModel-serialNumber` from the Bridge config).
`brand`, `type` and `model` stay as decided above. `ohServiceId` remains in use for
`deriveLocalUseCases()` only. This is a test of the S10 hypothesis, not yet confirmed live.

## Update 2026-10-05 (2): identity is frozen after the first successful start

Confirmed live: with `vendorCode`/`deviceModel`/`serialNumber` set back to `OPHAB`/`Energy Guard`/`0001`
(SHIP id and SPINE address `d:_n:OPHAB-Energy Guard-0001`) the Hager Energy S10 stays connected;
with `OPHAB-0001-0002` it closed the connection right after the `accessMethods` exchange. Which
part exactly the S10 pins (SHIP id, SPINE address or mDNS entry) is not known, as neither the S10
nor jeebus.ship log the close reason.

Decision: the three parts are pinned as Thing properties (`identityVendorCode`,
`identityDeviceModel`, `identitySerialNumber`) after the first successful SHIP/SPINE start. From
then on `shipId`, the SPINE device address and the certificate CN are built from the pinned values.
A differing Bridge config only produces a WARN log line. To change the identity deliberately, remove
and re-create the Thing (the same Thing UID keeps the keystore and so the SKI) and re-pair the peers.
The parameter descriptions in `thing-types.xml` say so.

Consequence for existing Things: the first start with this change pins whatever the config contains
at that moment, so the config must already hold the identity the peers were paired with.
