# Bosch Thermotechnology Binding

This binding integrates Bosch and Buderus heating systems that are controlled through the MyBuderus or Bosch DashApp mobile app.
Those apps talk to the Bosch Thermotechnology **PointT** cloud API, protected by a **SingleKey ID** login.
This binding uses the same cloud API, so it only works with gateways that are already set up in one of those apps — it does not talk to the gateway directly on the local network.
If your gateway is an older KM50/KM100/KM200 model reachable on your local network, use the `km200` binding instead.

## Supported Things

| Thing Type | Thing ID | Description |
|------------|----------|-------------|
| Bridge | `account` | One SingleKey ID (Bosch/Buderus) account login. Required as the parent of one or more gateways. |
| Thing | `gateway` | A single heating gateway registered to the account, e.g. a Buderus Logamatic control unit. |

## Discovery

Once an `account` bridge is authorized (see [Account Authorization](#account-authorization) below), it automatically discovers every gateway registered to that account through the PointT API and lists it in the Inbox.
Adding a `gateway` Thing manually is also possible, but the `gatewayId` configuration parameter must then be filled in by hand.

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
| `refreshInterval` | integer | Interval the gateway's resources are polled, in seconds. | 60 | no | yes |

## Channels

The `gateway` Thing groups its channels by function.
Use the group id together with the channel id (`<group>#<channel>`) when linking Items.

| Channel Group | Channel ID | Type | Read/Write | Description |
|---------------|-----------|------|------------|--------------|
| `system` | `outdoor-temperature` | Number:Temperature | R | Outdoor temperature reported by the gateway. |
| `heating-circuit` | `manual-room-setpoint` | Number:Temperature | RW | Manual room setpoint of heating circuit `hc1` (5–30 °C). |
| `dhw` | `charge-duration` | Number:Time | RW | Domestic hot water single-charge duration of `dhw1` (15–2880 min). |
| `dhw` | `single-charge-setpoint` | Number:Temperature | RW | Domestic hot water single-charge temperature setpoint of `dhw1` (50–70 °C). |
| `dhw` | `operation-mode` | Number | RW | Domestic hot water operation mode of `dhw1`: `0`=Off, `1`=Eco+, `2`=Eco, `3`=Comfort, `4`=Auto. |
| `dhw` | `charge` | Switch | RW | Starts or stops a domestic hot water instant charge of `dhw1`. |
| `dhw` | `reduce-temp-on-alarm` | Switch | RW | Reduces the domestic hot water temperature while an alarm is active. |
| `heat-source` | `ch-status` | String | R | Raw central heating status of the heat source. Exact value shape not yet confirmed. |
| `heat-source` | `actual-supply-temperature` | Number:Temperature | R | Actual supply (flow) temperature of the heat source. |
| `heat-source` | `return-temperature` | Number:Temperature | R | Return temperature of the heat source. |
| `heat-source` | `number-of-starts` | Number | R | Number of times the heat source has started. |
| `heat-source` | `working-time-total-system` | Number:Time | R | Total working time of the heat source. Unit assumed to be hours, not yet confirmed. |
| `notifications` | `active` | String | R | Raw active notifications/faults as a JSON string. Exact response shape not yet confirmed. |

Current domestic hot water temperature and heating circuit room temperature are still not available in this binding — their resource paths were not confirmed against a real gateway response yet.

In addition to the channels above, the `gateway` Thing exposes `serialId`, `firmwareVersion`, and `hardwareVersion` as read-only Thing properties (visible in the Properties panel). These are refreshed once a day on their own schedule, independent of `refreshInterval` and the regular channel poll, since this metadata rarely changes.

## Full Example

### Thing Configuration

```java
Bridge boschthermotechnology:account:myaccount "Bosch Thermotechnology Account" [ ] {
    Thing gateway myboiler "Buderus Heating" [ gatewayId="123456789", refreshInterval=60 ]
}
```

### Item Configuration

```java
Number:Temperature   OutdoorTemperature      "Outdoor Temperature [%.1f %unit%]"       { channel="boschthermotechnology:gateway:myaccount:myboiler:system#outdoor-temperature" }
Number:Temperature   ManualRoomSetpoint      "Manual Room Setpoint [%.1f %unit%]"      { channel="boschthermotechnology:gateway:myaccount:myboiler:heating-circuit#manual-room-setpoint" }
Number:Time          DhwChargeDuration       "DHW Charge Duration [%d %unit%]"         { channel="boschthermotechnology:gateway:myaccount:myboiler:dhw#charge-duration" }
Number:Temperature   DhwSingleChargeSetpoint "DHW Single Charge Setpoint [%.1f %unit%]" { channel="boschthermotechnology:gateway:myaccount:myboiler:dhw#single-charge-setpoint" }
Number                DhwOperationMode        "DHW Operation Mode [%d]"                 { channel="boschthermotechnology:gateway:myaccount:myboiler:dhw#operation-mode" }
Switch                DhwCharge               "DHW Instant Charge"                      { channel="boschthermotechnology:gateway:myaccount:myboiler:dhw#charge" }
Switch                DhwReduceTempOnAlarm    "DHW Reduce Temp On Alarm"                 { channel="boschthermotechnology:gateway:myaccount:myboiler:dhw#reduce-temp-on-alarm" }
String                 HeatSourceChStatus      "Heat Source Status [%s]"                 { channel="boschthermotechnology:gateway:myaccount:myboiler:heat-source#ch-status" }
Number:Temperature    HeatSourceSupplyTemp    "Heat Source Supply Temp [%.1f %unit%]"    { channel="boschthermotechnology:gateway:myaccount:myboiler:heat-source#actual-supply-temperature" }
Number:Temperature    HeatSourceReturnTemp    "Heat Source Return Temp [%.1f %unit%]"    { channel="boschthermotechnology:gateway:myaccount:myboiler:heat-source#return-temperature" }
Number                 HeatSourceStarts        "Heat Source Starts [%d]"                 { channel="boschthermotechnology:gateway:myaccount:myboiler:heat-source#number-of-starts" }
Number:Time            HeatSourceRuntime       "Heat Source Total Runtime [%.1f %unit%]" { channel="boschthermotechnology:gateway:myaccount:myboiler:heat-source#working-time-total-system" }
String                 ActiveNotifications     "Active Notifications [%s]"               { channel="boschthermotechnology:gateway:myaccount:myboiler:notifications#active" }
```

### Sitemap Configuration

```perl
sitemap boschthermotechnology label="Bosch Thermotechnology" {
    Frame label="Heating" {
        Text item=OutdoorTemperature
        Setpoint item=ManualRoomSetpoint minValue=5 maxValue=30 step=0.5
    }
    Frame label="Domestic Hot Water" {
        Setpoint item=DhwSingleChargeSetpoint minValue=50 maxValue=70 step=0.5
        Setpoint item=DhwChargeDuration minValue=15 maxValue=2880 step=15
        Selection item=DhwOperationMode mappings=[0="Off", 1="Eco+", 2="Eco", 3="Comfort", 4="Auto"]
        Switch item=DhwCharge
        Switch item=DhwReduceTempOnAlarm
    }
}
```
