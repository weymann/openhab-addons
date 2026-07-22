# Bosch Thermotechnology Binding

This binding integrates Bosch and Buderus heating systems that are controlled through the MyBuderus or Bosch DashApp mobile app.
Those apps talk to the Bosch Thermotechnology **PointT** cloud API, protected by a **SingleKey ID** login.
This binding uses the same cloud API, so it only works with gateways that are already set up in one of those apps — it does not talk to the gateway directly on the local network.
If your gateway is an older KM50/KM100/KM200 model reachable on your local network, use the `km200` binding instead.

Each gateway can bundle more than one physical subsystem — a heat pump, a photovoltaic/solar-thermal installation, a pool heater, an air conditioner, and so on.
The binding represents each of these as its own Thing underneath the gateway, instead of exposing every possible channel on one large Thing.
See [Thing Hierarchy](#thing-hierarchy) below for how these Things relate to each other.

## Thing Hierarchy

Every setup has exactly one `account` Bridge and one `gateway` Bridge per physical gateway.
Everything functional — heat pump, PV, pool, and so on — is a separate Thing underneath the `gateway` Bridge, as siblings of each other, never nested inside one another:

```text
account (Bridge)
└── gateway (Bridge)
    ├── heatpump
    ├── pv
    ├── pool
    ├── ventilation-zone
    ├── zone-thermostat (one per RF room thermostat)
    ├── energy-monitoring
    ├── ac-unit
    └── water-softener
```

Not every gateway has all eight child things — which ones apply depends on the hardware actually installed, and discovery only proposes the ones it can detect (see [Discovery](#discovery)).

## Supported Things

| Thing Type | Thing ID | Description |
|------------|----------|-------------|
| Bridge | `account` | One SingleKey ID (Bosch/Buderus) account login. Required as the parent of one or more gateways. |
| Bridge | `gateway` | A single heating gateway registered to the account, e.g. a Buderus Logamatic control unit. Holds only channels that belong to the physical box itself; everything functional is a child thing underneath it. |
| Thing | `heatpump` | Heating circuits, domestic hot water circuits, and heat source(s) of one gateway. |
| Thing | `pv` | Photovoltaic inverter and solar-thermal collector readings of one gateway. |
| Thing | `pool` | Pool heating status of one gateway. |
| Thing | `ventilation-zone` | One HRV ventilation zone of a gateway. |
| Thing | `zone-thermostat` | One multi-zone RF room thermostat. A gateway can have more than one. |
| Thing | `energy-monitoring` | Historical energy-monitoring recordings of one gateway's heat source(s). |
| Thing | `ac-unit` | One room air-conditioning (RAC) unit. |
| Thing | `water-softener` | One water softener. Not yet functional — the PointT resource path for this device class has not been confirmed against a live gateway, see the note under [`water-softener` Thing Configuration](#water-softener-thing-configuration). |

## Discovery

Once an `account` Bridge is authorized (see [Account Authorization](#account-authorization) below), it automatically discovers every gateway registered to that account through the PointT API and lists it in the Inbox.
Once a `gateway` Bridge in turn comes online, it probes the PointT API for the child things that gateway actually has (heat pump circuits, PV/solar-thermal, pool, ventilation, RF zone thermostats, AC units) and lists whichever ones it finds in the Inbox.
`water-softener` is never proposed by discovery, since its resource path is unconfirmed — see [Supported Things](#supported-things).

Adding any Thing manually is also possible, but its `gatewayId` (and, for `zone-thermostat`, `zoneId`) configuration parameter must then be filled in by hand.

## Account Authorization

SingleKey ID uses an OAuth2 login flow that ends with a redirect back into the (non-existent) MyBuderus mobile app, so openHAB cannot capture the login result automatically.
A short manual step is required once per account:

1. Add an `account` Bridge.
1. Open the Bridge's configuration and copy the URL shown in the `authUrl` parameter — it is filled in automatically a few seconds after the Bridge is created.
1. Open that URL in any browser and log in with your Bosch/Buderus account.
1. The browser will try to redirect to an address it cannot open. This is expected — copy the full address from the browser's address bar anyway.
1. Paste that address into the Bridge's `pasteAuthorizationRedirectUrl` configuration parameter and save.

The Bridge goes `ONLINE` once the login succeeds, and stays authorized afterward — the login step is not required again unless the Bridge is removed and re-added.

## Thing Configuration

### `account` Bridge Configuration

| Name | Type | Description | Default | Required | Advanced |
|------|------|--------------|---------|----------|----------|
| `authUrl` | text | Automatically filled in with the SingleKey ID login URL. Read-only, empty once authorized. | N/A | no | no |
| `pasteAuthorizationRedirectUrl` | text | One-time: paste the redirect URL from your browser here to complete login. Never stored. | N/A | no | no |

### `gateway` Thing Configuration

| Name | Type | Description | Default | Required | Advanced |
|------|------|--------------|---------|----------|----------|
| `gatewayId` | text | The gateway id assigned by the PointT API. Filled in automatically by discovery. | N/A | yes | yes |
| `refreshInterval` | integer | Interval the gateway's own system resources are polled, in seconds. | 60 | no | yes |

### `heatpump`, `pv`, `pool`, `ventilation-zone`, `energy-monitoring`, `ac-unit`, `water-softener` Thing Configuration

These seven child thing types share the same two configuration parameters:

| Name | Type | Description | Default | Required | Advanced |
|------|------|--------------|---------|----------|----------|
| `gatewayId` | text | The gateway id this thing belongs to. Filled in automatically by discovery. | N/A | yes | yes |
| `refreshInterval` | integer | Interval this thing's resources are polled, in seconds. | 60 | no | yes |

#### `water-softener` Thing Configuration

The PointT resource path prefix for water softeners has not been confirmed against a live gateway — only the device-type string `watersoftener` is known from reverse-engineering the mobile app.
A `water-softener` Thing therefore always goes `OFFLINE`/`CONFIGURATION_PENDING` once its `gatewayId` is set, and exposes no channels yet.

### `zone-thermostat` Thing Configuration

| Name | Type | Description | Default | Required | Advanced |
|------|------|--------------|---------|----------|----------|
| `gatewayId` | text | The gateway id this zone thermostat belongs to. Filled in automatically by discovery. | N/A | yes | yes |
| `zoneId` | text | The zone number (e.g. `1` for `zone1`) as returned by the gateway. Filled in automatically by discovery. | N/A | yes | no |
| `refreshInterval` | integer | Interval this thing's resources are polled, in seconds. | 60 | no | yes |

## Channels

### `gateway` Channels

The `gateway` Thing groups its channels by function.
Use the group id together with the channel id (`<group>#<channel>`) when linking Items.

| Channel Group | Channel ID | Type | Read/Write | Description |
|---------------|-----------|------|------------|--------------|
| `system` | `outdoor-temperature` | Number:Temperature | R | Outdoor temperature reported by the gateway. |
| `system` | `away-mode-enabled` | Switch | RW | Enables or disables away mode for the whole gateway. |
| `system` | `silent-mode-enabled` | Switch | RW | Enables or disables silent (noise-reduced) mode for the whole gateway. |
| `system` | `season-optimizer-mode` | String | RW | Raw season optimizer mode. Exact value enumeration not yet confirmed. |
| `system` | `holiday-mode-active` | String | R | Raw summary of the currently active holiday mode. |
| `notifications` | `active` | String | R | Raw active notifications/faults as a JSON string. Exact response shape not yet confirmed. |

In addition to the channels above, the `gateway` Thing exposes `serialId`, `firmwareVersion`, and `hardwareVersion` as read-only Thing properties (visible in the Properties panel).
These are refreshed once a day on their own schedule, independent of `refreshInterval` and the regular channel poll, since this metadata rarely changes.

### `heatpump` Channels

Unlike every other child thing, `heatpump` has no fixed channel list — its channel groups are built dynamically, one `heating-circuit-<circuitId>` group per heating circuit and one `dhw-circuit-<circuitId>` group per domestic hot water circuit the gateway actually reports (e.g. `heating-circuit-hc1`, `dhw-circuit-dhw1`), plus one fixed `heat-source-1` group.
The circuit id in the group name comes from the gateway itself, not from list position, so it stays stable even if the gateway ever reports its circuits in a different order.

| Channel Group | Channel ID | Type | Read/Write | Description |
|---------------|-----------|------|------------|--------------|
| `heating-circuit-<circuitId>` | `manual-room-setpoint` | Number:Temperature | RW | Manual room setpoint of this heating circuit (5–30 °C). |
| `dhw-circuit-<circuitId>` | `charge-duration` | Number:Time | RW | Domestic hot water single-charge duration of this DHW circuit (15–2880 min). |
| `dhw-circuit-<circuitId>` | `single-charge-setpoint` | Number:Temperature | RW | Domestic hot water single-charge temperature setpoint of this DHW circuit (50–70 °C). |
| `dhw-circuit-<circuitId>` | `operation-mode` | Number | RW | Domestic hot water operation mode of this DHW circuit: `0`=Off, `1`=Eco+, `2`=Eco, `3`=Comfort, `4`=Auto. |
| `dhw-circuit-<circuitId>` | `charge` | Switch | RW | Starts or stops a domestic hot water instant charge of this DHW circuit. |
| `dhw-circuit-<circuitId>` | `reduce-temp-on-alarm` | Switch | RW | Reduces the domestic hot water temperature while an alarm is active. |
| `heat-source-1` | `ch-status` | String | R | Raw central heating status of the heat source. Exact value shape not yet confirmed. |
| `heat-source-1` | `actual-supply-temperature` | Number:Temperature | R | Actual supply (flow) temperature of the heat source. |
| `heat-source-1` | `return-temperature` | Number:Temperature | R | Return temperature of the heat source. |
| `heat-source-1` | `number-of-starts` | Number | R | Number of times the heat source has started. |
| `heat-source-1` | `working-time-total-system` | Number:Time | R | Total working time of the heat source. Unit assumed to be hours, not yet confirmed. |

Current domestic hot water temperature and heating circuit room temperature are still not available in this binding — their resource paths were not confirmed against a real gateway response yet.
Cascade installations with more than one heat source are not yet supported — `heat-source-1` is always the only heat-source group, regardless of how many heat sources the gateway actually has.

### `pv` Channels

| Channel Group | Channel ID | Type | Read/Write | Description |
|---------------|-----------|------|------------|--------------|
| `photovoltaic` | `enabled` | Switch | RW | Enables or disables photovoltaic integration. |
| `photovoltaic` | `inverter-info` | String | R | Raw photovoltaic inverter information, as a JSON string. Exact response shape not yet confirmed. |
| `solar-thermal` | `collector-temperature` | Number:Temperature | R | Solar-thermal collector temperature. |
| `solar-thermal` | `yield` | Number:Energy | R | Solar-thermal yield. |

### `pool` Channels

`pool` channels are not grouped — link them directly by channel id.

| Channel ID | Type | Read/Write | Description |
|-----------|------|------------|--------------|
| `enabled` | Switch | RW | Enables or disables pool heating. |
| `setpoint-temperature` | Number:Temperature | RW | Pool water temperature setpoint. |
| `current-temperature` | Number:Temperature | R | Current pool water temperature. |

### `ventilation-zone` Channels

| Channel ID | Type | Read/Write | Description |
|-----------|------|------------|--------------|
| `operation-mode` | String | RW | Raw ventilation operation mode. Exact value enumeration not yet confirmed. |
| `filter-remaining-time` | Number:Time | R | Remaining filter run time before replacement is due. |

### `zone-thermostat` Channels

| Channel ID | Type | Read/Write | Description |
|-----------|------|------------|--------------|
| `manual-room-setpoint` | Number:Temperature | RW | Manual room setpoint of this zone thermostat (5–30 °C). Assumes heating mode; cooling mode is not yet supported. |
| `average-current-temperature` | Number:Temperature | R | Average current room temperature of this zone thermostat. |
| `child-lock` | Switch | RW | Enables or disables the child lock of this zone thermostat. |

### `energy-monitoring` Channels

| Channel ID | Type | Read/Write | Description |
|-----------|------|------------|--------------|
| `actual-ch-power` | Number:Power | R | Actual central heating power. May need rework once it is confirmed whether this resource returns a single value or a time series. |
| `actual-dhw-power` | Number:Power | R | Actual domestic hot water power. Same caveat as `actual-ch-power`. |
| `total-consumed-energy` | Number:Energy | R | Total consumed energy of the heat source. Same caveat as `actual-ch-power`. |

### `ac-unit` Channels

| Channel ID | Type | Read/Write | Description |
|-----------|------|------------|--------------|
| `operation-mode` | String | RW | Raw RAC operation mode. Exact value enumeration not yet confirmed. |
| `fan-speed` | String | RW | Raw RAC fan speed. Exact value enumeration not yet confirmed. |
| `temperature-setpoint` | Number:Temperature | RW | RAC temperature setpoint. |

### `water-softener` Channels

None yet — see [`water-softener` Thing Configuration](#water-softener-thing-configuration).

## Full Example

### Thing Configuration

```java
Bridge boschthermotechnology:account:myaccount "Bosch Thermotechnology Account" [ ] {
    Bridge gateway mygateway "Buderus Gateway" [ gatewayId="123456789", refreshInterval=60 ] {
        Thing heatpump myheatpump "Heat Pump" [ gatewayId="123456789", refreshInterval=60 ]
        Thing pv mypv "Photovoltaic" [ gatewayId="123456789", refreshInterval=60 ]
        Thing pool mypool "Pool" [ gatewayId="123456789", refreshInterval=60 ]
        Thing zone-thermostat myzone1 "Living Room Thermostat" [ gatewayId="123456789", zoneId="1", refreshInterval=60 ]
        Thing energy-monitoring myenergy "Energy Monitoring" [ gatewayId="123456789", refreshInterval=60 ]
    }
}
```

### Item Configuration

```java
Number:Temperature   OutdoorTemperature      "Outdoor Temperature [%.1f %unit%]"       { channel="boschthermotechnology:gateway:myaccount:mygateway:system#outdoor-temperature" }
Switch                AwayModeEnabled         "Away Mode"                                { channel="boschthermotechnology:gateway:myaccount:mygateway:system#away-mode-enabled" }

Number:Temperature   ManualRoomSetpoint      "Manual Room Setpoint [%.1f %unit%]"      { channel="boschthermotechnology:heatpump:myaccount:mygateway:myheatpump:heating-circuit-hc1#manual-room-setpoint" }
Number:Time          DhwChargeDuration       "DHW Charge Duration [%d %unit%]"         { channel="boschthermotechnology:heatpump:myaccount:mygateway:myheatpump:dhw-circuit-dhw1#charge-duration" }
Number:Temperature   DhwSingleChargeSetpoint "DHW Single Charge Setpoint [%.1f %unit%]" { channel="boschthermotechnology:heatpump:myaccount:mygateway:myheatpump:dhw-circuit-dhw1#single-charge-setpoint" }
Number                DhwOperationMode        "DHW Operation Mode [%d]"                 { channel="boschthermotechnology:heatpump:myaccount:mygateway:myheatpump:dhw-circuit-dhw1#operation-mode" }
Switch                DhwCharge               "DHW Instant Charge"                      { channel="boschthermotechnology:heatpump:myaccount:mygateway:myheatpump:dhw-circuit-dhw1#charge" }
String                 HeatSourceChStatus      "Heat Source Status [%s]"                 { channel="boschthermotechnology:heatpump:myaccount:mygateway:myheatpump:heat-source-1#ch-status" }
Number:Temperature    HeatSourceSupplyTemp    "Heat Source Supply Temp [%.1f %unit%]"    { channel="boschthermotechnology:heatpump:myaccount:mygateway:myheatpump:heat-source-1#actual-supply-temperature" }

Switch                PvEnabled               "PV Enabled"                              { channel="boschthermotechnology:pv:myaccount:mygateway:mypv:photovoltaic#enabled" }
Number:Temperature    SolarCollectorTemp      "Solar Collector Temp [%.1f %unit%]"      { channel="boschthermotechnology:pv:myaccount:mygateway:mypv:solar-thermal#collector-temperature" }

Switch                PoolEnabled             "Pool Enabled"                            { channel="boschthermotechnology:pool:myaccount:mygateway:mypool:enabled" }
Number:Temperature    PoolSetpointTemp        "Pool Setpoint Temp [%.1f %unit%]"        { channel="boschthermotechnology:pool:myaccount:mygateway:mypool:setpoint-temperature" }
Number:Temperature    PoolCurrentTemp         "Pool Current Temp [%.1f %unit%]"         { channel="boschthermotechnology:pool:myaccount:mygateway:mypool:current-temperature" }

Number:Temperature    LivingRoomSetpoint      "Living Room Setpoint [%.1f %unit%]"      { channel="boschthermotechnology:zone-thermostat:myaccount:mygateway:myzone1:manual-room-setpoint" }
Number:Temperature    LivingRoomCurrentTemp   "Living Room Current Temp [%.1f %unit%]"  { channel="boschthermotechnology:zone-thermostat:myaccount:mygateway:myzone1:average-current-temperature" }

Number:Power           ActualChPower           "Actual CH Power [%.1f %unit%]"           { channel="boschthermotechnology:energy-monitoring:myaccount:mygateway:myenergy:actual-ch-power" }
Number:Energy          TotalConsumedEnergy     "Total Consumed Energy [%.1f %unit%]"     { channel="boschthermotechnology:energy-monitoring:myaccount:mygateway:myenergy:total-consumed-energy" }
```

### Sitemap Configuration

```perl
sitemap boschthermotechnology label="Bosch Thermotechnology" {
    Frame label="Gateway" {
        Text item=OutdoorTemperature
        Switch item=AwayModeEnabled
    }
    Frame label="Heat Pump" {
        Setpoint item=ManualRoomSetpoint minValue=5 maxValue=30 step=0.5
        Setpoint item=DhwSingleChargeSetpoint minValue=50 maxValue=70 step=0.5
        Setpoint item=DhwChargeDuration minValue=15 maxValue=2880 step=15
        Selection item=DhwOperationMode mappings=[0="Off", 1="Eco+", 2="Eco", 3="Comfort", 4="Auto"]
        Switch item=DhwCharge
        Text item=HeatSourceChStatus
        Text item=HeatSourceSupplyTemp
    }
    Frame label="Photovoltaic" {
        Switch item=PvEnabled
        Text item=SolarCollectorTemp
    }
    Frame label="Pool" {
        Switch item=PoolEnabled
        Setpoint item=PoolSetpointTemp minValue=20 maxValue=35 step=0.5
        Text item=PoolCurrentTemp
    }
    Frame label="Living Room" {
        Setpoint item=LivingRoomSetpoint minValue=5 maxValue=30 step=0.5
        Text item=LivingRoomCurrentTemp
    }
    Frame label="Energy Monitoring" {
        Text item=ActualChPower
        Text item=TotalConsumedEnergy
    }
}
```
