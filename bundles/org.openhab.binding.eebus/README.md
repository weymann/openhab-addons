# EEBus Binding

This binding connects openHAB to devices that speak **EEBUS** (SHIP for transport, SPINE for the data model), for example a Hager Energy S10, an evcc HEMS or another openHAB instance.
openHAB can take both sides of the supported use cases:

- **Energy Guard** (value provider): openHAB commands a power limit to a controllable system (LPC / LPP, Client role).
- **Controllable System** (value consumer): openHAB receives a power limit from an Energy Guard and exposes the resulting state (LPC / LPP, Server role).
- **Monitoring** (MPC, MGCP): openHAB reads power, energy, current, voltage and frequency from a monitoring device.

The binding embeds jeebus.ship 3.2.1 and jeebus.spine 4.2.0 (pinned in `pom.xml`).

Further documentation lives next to the code:

- `docs/ADR/` - one architecture decision record per design decision (referenced below as ADR-nnn).
- `docs/CONCEPT.md` - overall concept.
- `docs/TEST_PAIRING.md` - step-by-step pairing tests against a real device and between two openHAB Bridges.
- `docs/SKI.md` - reference values from the first real-device pairing.

## Supported Things

Names follow the EEBUS/SHIP/SPINE vocabulary (ADR-024).

| Thing Type        | Kind    | Description |
|-------------------|---------|-------------|
| `oh-device`       | Bridge  | One local EEBUS SHIP/SPINE service instance: own certificate, own mDNS presence, own SHIP server port. |
| `hw-device`       | Thing   | A real EEBUS device seen on the network, identified by its SKI. Top level (no Bridge), carries no channels and implies no trust. |
| `oh-eg-entity`    | Thing   | Energy Guard (LPC/LPP Client). Writable `lpc`/`lpp` channels. |
| `oh-cs-entity`    | Thing   | Controllable System (LPC/LPP Server). Read-only `lpc`/`lpp` channels, state, heartbeat. |
| `oh-mpc-entity`   | Thing   | Monitoring Appliance (MPC Client). `mpc` channels. |
| `oh-entity`       | Thing   | Generic entity. Use cases are selected by hand, channels are created dynamically once a use case is detected. |

All `oh-*-entity` Things are children of an `oh-device` Bridge and are additive: they can be mixed freely under the same Bridge.

### `oh-device` (Bridge)

- Its local SPINE use case set is **derived** from the attached entity Things on every `initialize()` (`oh-eg-entity` contributes LPC+LPP Client, `oh-cs-entity` LPC+LPP Server, `oh-mpc-entity` MPC Client; ADR-027).
- Every config change of the Bridge and every lifecycle event of a child entity triggers a full rebuild of the local SPINE device. To avoid several rebuilds while you change many Things, disable the Bridge first and enable it afterwards.
- **The Thing ID matters.** The SHIP certificate (and therefore the SKI that real devices trust) is persisted per Thing ID. If you recreate the Bridge, reuse the exact same ID to keep the certificate and the trust relationship.
- **The SHIP identity is frozen after the first successful start** (ADR-050).
  `vendorCode`, `deviceModel` and `serialNumber` form the SHIP ID (`<vendorCode>-<deviceModel>-<serialNumber>`) and the SPINE device address. They are stored as Thing properties (`identityVendorCode`, `identityDeviceModel`, `identitySerialNumber`) and later config changes are ignored with a warning.
  Reason: a real device such as the Hager Energy S10 closes the connection as soon as the identity of an already paired peer changes.
  To really change the identity, remove and re-create the Thing (same Thing ID keeps the SKI) and pair again.

### Entity Things

All entity Things share:

| Name            | Type | Description | Default | Required | Advanced |
|-----------------|------|-------------|---------|----------|----------|
| `ski`           | text | SKI (40 hex characters) of the remote device this entity lives on. Left empty it is auto-selected once the Bridge trusts exactly one device. | - | no | no |
| `entityAddress` | text | SPINE address of the entity on the device. For reference only. | - | no | yes |
| `shipId`        | text | Learned during the first successful handshake, normally empty. | - | no | yes |
| `entityType`    | text | SPINE entity type the shared local entity presents (ADR-042). Allowed values depend on the Thing type. | `CEM` | no | yes |

Additional parameters:

- `oh-entity`: `supportedUseCasesClient`, `supportedUseCasesServer` (list of use cases to detect or to offer).
- `oh-cs-entity`: `failsafeConsumptionLimitSeedWatts` (default `0` W), `failsafeProductionLimitSeedWatts` (default `0` W), `failsafeDurationMinimumSeedSeconds` (default `120` s). They seed the failsafe values at every start; afterwards only a paired Energy Guard can change them over EEBUS.

### `oh-device` Bridge parameters

| Name                  | Type    | Description | Default | Required | Advanced |
|-----------------------|---------|-------------|---------|----------|----------|
| `deviceType`          | text    | SPINE device type presented in detailed discovery (ADR-044). Independent of `entityType`. | `EnergyManagementSystem` | no | no |
| `vendorCode`          | text    | 5-character vendor code. Part of the SHIP ID, pinned at first start. | - | yes | no |
| `deviceBrand`         | text    | Brand, announced via mDNS. | - | yes | no |
| `deviceModel`         | text    | Model, announced via mDNS. Part of the SHIP ID, pinned at first start. | - | yes | no |
| `serialNumber`        | text    | Serial number. Part of the SHIP ID, pinned at first start. | - | yes | no |
| `mdnsServiceInstance` | text    | Friendly service name other devices see. | - | yes | no |
| `port`                | integer | WebSocket port of the local SHIP server. Empty: a free port from 4711 to 4810 is assigned. | - | no | yes |
| `trustedSkis`         | text[]  | SKIs this Bridge trusts (see "Trust"). | - | no | yes |
| `connectToPeers`      | boolean | Actively dial trusted peers found via mDNS in addition to accepting incoming connections. | `true` | no | yes |
| `preferIpv4`          | boolean | Bind and announce the SHIP server on IPv4. | `true` | no | yes |

The SHIP registration (mDNS TXT record) announces `brand`, `type`, `id` and `model` of the Bridge, like a real device does (ADR-050).
SHIP auto-accept is not offered any more (ADR-051); trust is always established explicitly.

## Discovery

Real devices are discovered via mDNS (`_ship._tcp.local.`, SHIP 7.3.2) as soon as the binding is installed.
They appear in the Inbox without any prerequisite Thing, unless their SKI already belongs to an existing `hw-device` Thing.

Inside a running Bridge the binding resolves a SPINE partner address to the SKI of the partner.
Since jeebus.ship 3.2.1 the partner address is the **SHIP ID** of the partner (for example `OPHAB-Energy Guard-0001` or `S10-492011009322`), not `ip:port` any more; the binding therefore also indexes the mDNS TXT field `id`.

## Trust

Trust is SKI based and a **Bridge level** decision (ADR-024, ADR-027).

1. Add the SKI of a device to the `trustedSkis` of the Bridge. The list offers known `hw-device` Things and the SKIs of other local Bridges; free text is possible.
1. Create an entity Thing (`oh-eg-entity`, `oh-cs-entity`, `oh-mpc-entity` or `oh-entity`) under the Bridge. An entity whose SKI is not trusted stays `OFFLINE` / `CONFIGURATION PENDING`.
1. **The remote device has to trust openHAB too.** Enter the SKI of your Bridge (shown as Thing property `localSki`) on the other device, for a real device usually in its own app or web interface. As long as one side does not trust the other, SHIP stays in `connectionHello` pending/ready and no data flows.
1. To revoke trust remove the SKI from `trustedSkis`. Entity Things with that SKI go back to `OFFLINE`.

Two Bridges in the same openHAB (a typical test setup) need each other's SKI in their `trustedSkis`, because mDNS does not show a service to itself.
If you let both Bridges talk to a real device, remember that all of them announce from the same host address.

## Channels

### Energy Guard: `oh-eg-entity` (value provider, writable)

| Channel Group | Channel          | Type           | Description |
|---------------|------------------|----------------|-------------|
| `lpc`, `lpp`  | `limit-active`   | `Switch`       | Activates or deactivates the limit. |
| `lpc`, `lpp`  | `limit-value`    | `Number:Power` | Power limit. Offers 1234 W, 2345 W, 4200 W and 9876 W as selectable states, any other number is accepted as Watt. |
| `lpc`, `lpp`  | `limit-duration` | `Number:Time`  | Duration of validity. Never commanded: unbounded limit. |

A command on one of these channels remembers the value, echoes it as channel state and sends **one combined** LoadControl write (`isLimitActive`, `value`, `timePeriod`, using the last commanded values of the other two) to **every** bound partner (ADR-047, ADR-052).
Each partner gets its own resolved `limitId`.
A failing partner is logged and does not stop the write to the other partners.

The write can alternatively be driven by tagged Items instead of channels: tag `<ThingUID>:LPC:limitActive`, `<ThingUID>:LPC:limitValue` or `<ThingUID>:LPC:limitDuration` (`LPP` for production), with the `oh-eg-entity` Thing UID.
Exactly one `oh-eg-entity` per Bridge is the supported configuration.

The Energy Guard also sends its own **Heartbeat** (every 60 s, timeout 60 s) to every partner (ADR-035).
The binding binds the partner's LoadControl and DeviceConfiguration features from its own entity before writing (ADR-034, ADR-039, ADR-044).

### Controllable System: `oh-cs-entity` (value consumer, read-only)

| Channel Group | Channel                     | Type           | Description |
|---------------|-----------------------------|----------------|-------------|
| `lpc`, `lpp`  | `state`                     | `Number`       | State machine: 0 Init, 1 Unlimited (Controlled), 2 Limited, 3 Failsafe, 4 Unlimited (Autonomous) (ADR-046). |
| `lpc`, `lpp`  | `limit-active`              | `Switch`       | A limit of the paired Energy Guard is in effect. |
| `lpc`, `lpp`  | `limit-value`               | `Number:Power` | Limit value of the most recent write. |
| `lpc`, `lpp`  | `limit-duration`            | `Number:Time`  | Remaining duration of validity, `UNDEF` if unbounded (ADR-033). |
| `lpc`, `lpp`  | `failsafe-limit-value`      | `Number:Power` | Failsafe limit configured by the Energy Guard. |
| `lpc`, `lpp`  | `failsafe-duration-minimum` | `Number:Time`  | Minimum duration of the failsafe limit. |
| `lpc`, `lpp`  | `heartbeat`                 | trigger        | Fires for every Heartbeat of the Energy Guard; the event carries the heartbeat counter (ADR-045). |

**Which Thing carries the values:** the channels are filled on the `oh-cs-entity` Thing **under the Bridge that plays the Controllable System** and whose `ski` is the SKI of the Energy Guard.
The Energy Guard side (`oh-eg-entity`) never shows the status of its partners.

Value processing in short: a received write changes the state machine (limit active: `LIMITED`; limit deactivated: `UNLIMITED_CONTROLLED`; no Heartbeat within the timeout: `FAILSAFE`); the Controllable System republishes the effective values to its SPINE feature and mirrors them into the channels (ADR-021, ADR-022, ADR-032).
Items with `eebus="LPC.state"`, `eebus="LPC.consumptionLimit"` metadata additionally receive the state and the raw value.

### Monitoring: `oh-mpc-entity` / `oh-entity` (MPC) and `oh-entity` (MGCP)

`oh-mpc-entity` declares the `mpc` group statically, `oh-entity` creates the same channels dynamically once MPC is detected (ADR-014, ADR-036, ADR-037).
All channels are read-only.

| Channel Group | Channel                                            | Type                     | Description |
|---------------|----------------------------------------------------|--------------------------|-------------|
| `mpc`         | `power`                                            | `Number:Power`           | Total active power (positive: consumption, negative: feed-in). |
| `mpc`         | `power-phase-a`, `power-phase-b`, `power-phase-c`  | `Number:Power`           | Active power per phase. |
| `mpc`         | `energy-consumed`, `energy-produced`               | `Number:Energy`          | Total consumed / produced energy. |
| `mpc`         | `current-phase-a`, `-b`, `-c`                      | `Number:ElectricCurrent` | Current per phase. |
| `mpc`         | `voltage-phase-a`, `-b`, `-c`                      | `Number:ElectricPotential` | Phase-to-neutral voltage. |
| `mpc`         | `voltage-a-b`, `voltage-b-c`, `voltage-c-a`        | `Number:ElectricPotential` | Phase-to-phase voltage. **Stub, never populated** (no measurement identifier available), only on `oh-mpc-entity`. |
| `mpc`         | `frequency`                                        | `Number:Frequency`       | Grid frequency. |
| `mgcp`        | `total-active-power`                               | `Number:Power`           | Total active power at the grid connection point, MGCP Scenario 2 only (`oh-entity`, dynamic, ADR-040). |

`oh-entity` additionally creates dynamic `lpc`/`lpp` channels (`limit-active`, `limit-value`, `limit-duration`, `failsafe-*`) when it receives LPC/LPP as Controllable System.

Item states in the openHAB UI are not live: press REFRESH to see the current value.

## Full Example

Two Bridges in one openHAB, an Energy Guard that commands a Controllable System (and a real device), and a monitoring entity.

### Thing Configuration

```java
Bridge eebus:oh-device:energy-guard-sim "EEBus Energy Guard" [
        vendorCode="OPHAB", deviceBrand="openHAB", deviceModel="Energy Guard", serialNumber="0001",
        mdnsServiceInstance="openHAB Energy Guard", port=4711,
        trustedSkis="4c060b1e54b667d40689203d4c9896a63e599360,dd8ba427c0a449180513fbb51bf6f0bbdbf0c821" ] {
    Thing oh-eg-entity limiter "Energy Guard" [ entityType="GridGuard" ]
}

Bridge eebus:oh-device:ems "EEBus Controllable System" [
        vendorCode="OPHAB", deviceBrand="openHAB", deviceModel="CEM", serialNumber="0001",
        mdnsServiceInstance="EMS", port=4712,
        trustedSkis="ba61c22dc42b8b067bd2da4669a8b89dc3f9cf49" ] {
    Thing oh-cs-entity cs "Controllable System" [
            ski="ba61c22dc42b8b067bd2da4669a8b89dc3f9cf49",
            failsafeConsumptionLimitSeedWatts=4200, failsafeDurationMinimumSeedSeconds=7200 ]
}
```

### Item Configuration

```java
Switch EG_Lpc_Active  { channel="eebus:oh-eg-entity:energy-guard-sim:limiter:lpc#limit-active" }
Number:Power EG_Lpc_Limit { channel="eebus:oh-eg-entity:energy-guard-sim:limiter:lpc#limit-value" }

Number CS_Lpc_State   { channel="eebus:oh-cs-entity:ems:cs:lpc#state" }
Switch CS_Lpc_Active  { channel="eebus:oh-cs-entity:ems:cs:lpc#limit-active" }
Number:Power CS_Lpc_Limit { channel="eebus:oh-cs-entity:ems:cs:lpc#limit-value" }
```

### Rule Example

```java
rule "Heartbeat of the Energy Guard"
when
    Channel "eebus:oh-cs-entity:ems:cs:lpc#heartbeat" triggered
then
    logInfo("eebus", "Heartbeat {}", receivedEvent)
end
```

## Troubleshooting

- **Connection stays pending:** both sides have to trust each other's SKI (see "Trust").
- **A real device disconnects after you changed the Bridge settings:** the SHIP identity of a paired Bridge must not change. The binding keeps the pinned identity; check the warning in the log and the `identity*` Thing properties.
- **Channels of the Controllable System stay `NULL`:** check that an `oh-cs-entity` with the SKI of the Energy Guard exists under the Controllable System Bridge and is `ONLINE`. Look for `Energy Guard partner at … resolved to …` in the DEBUG log of the `EEBusLpcServerUseCase`/`EEBusLppServerUseCase`.
- **A real Energy Guard peer rejects LPC writes with `COMMAND_REJECTED` (Error 7):** the peer decides whether it accepts the change; seen with a Hager Energy S10 for several LPC value/active combinations.
- **Slow stop of a Bridge:** disposing a Bridge can take a few seconds (mDNS shutdown inside jeebus.ship).

## Known Limitations

- `entityAddress` is recorded but not yet used to route data; several entity Things for one device observe the same device-level events.
- Only the first detected Energy Guard of a Controllable System is tracked.
- Scenario 4 (constraints) of LPC/LPP is not exposed.
- `jeebus.ship` and `jeebus.spine` are used as they are; known upstream issues (for example a null `NodeManagement` when a connection closes after the device was closed) are reported there.
