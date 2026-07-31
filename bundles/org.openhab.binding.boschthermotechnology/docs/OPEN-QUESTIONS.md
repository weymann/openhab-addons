# Open Questions: Unconfirmed Assumptions Against a Live Gateway

This binding was built from two reverse-engineering sources (`buderus-reverse.md`,
`myapp-api-analysis.md`) that document endpoints and their purpose but not always the exact
response schema. Every place where a schema was guessed instead of confirmed carries an inline
`TODO ($Dev)` in the code, i18n properties, or `thing-types.xml`. This document is the single
consolidated list of those TODOs, their current status, and - for anything still open - exactly
what TRACE-level evidence from a real gateway would close it.

Update this file whenever a TODO above is resolved or a new one is added; do not let it drift out
of sync with the inline TODOs it tracks.

---

## Resolved

| # | Item | Resolved | Evidence |
|---|---|---|---|
| 1 | `GET /gateways/` list entry field is `deviceId`, not `id` | 2026-07-31 | Live `TRACE` log: `body=[{"deviceId":"...","deviceType":"k40"}]`. See [ADR-007](ADR/007-gateway-list-and-refenum-response-shapes-confirmed.md). |
| 2 | `heatingCircuits` list response is a `refEnum` object with a `references` array, not a bare array | 2026-07-31 | Live `TRACE` log: `body={"id":"/heatingCircuits","type":"refEnum","references":[{"id":"/heatingCircuits/hc1","uri":"..."}]}`, seen from both `ChildThingDiscoveryService` and `HeatpumpHandler`. See [ADR-007](ADR/007-gateway-list-and-refenum-response-shapes-confirmed.md). |
| 3 | `BoschThermotechnologyHandlerFactory` constructor-based `@Reference`/`@Activate` injection may need a newer bnd/SCR version than this bundle's inherited parent `pom.xml` provides | 2026-07-31 | The generated `target/classes/OSGI-INF/....BoschThermotechnologyHandlerFactory.xml` component descriptor (built after the current source) correctly declares `init="2"` with both constructor parameters wired as SCR v1.4.0 `<reference parameter="...">` elements - constructor injection compiles and wires correctly, no field-based fallback needed. |

---

## Open — Blocked on Live-Gateway Data

Everything below needs a `TRACE`-level log excerpt (`org.openhab.binding.boschthermotechnology.internal.api.PointTApiClient` at `TRACE`) captured while the relevant subsystem is actually present and active, the same way items 1 and 2 above were resolved. Several of these only apply if the gateway in question actually has that subsystem (PV, AC, ventilation, water softener, a cascade heat source) - if you don't own that hardware, these will likely stay open indefinitely and that is fine.

### Heat source status and metering

- **`heat-source-ch-status`** (`GET resource/heatSources/chStatus`) - value shape unconfirmed: boolean, numeric code, or string. Currently passed through as-is via `StateMappers.rawString`. Source: `thing-types.xml` channel-type `heat-source-ch-status`.
- **`heat-source-working-time-total-system`** (`GET resource/heatSources/workingTime/totalSystem`) - unit assumed to be hours, unconfirmed. Source: `thing-types.xml` channel-type `heat-source-working-time-total-system`.
- **`recordings/*`** (e.g. `GET resource/recordings/heatSources/actualCHPower`) - unconfirmed whether this returns a single scalar or a `YRecording`-style time series (per `myapp-api-analysis.md`). Currently `EnergyMonitoringHandler` treats all `recordings/*` channels as scalars. Source: `EnergyMonitoringHandler.java` class Javadoc, `thing-types.xml` channel-type `energy-actual-ch-power` (and siblings).

### System-level modes

- **`season-optimizer-mode`** (`GET resource/system/seasonOptimizer/mode`) - exact enumeration of allowed values unconfirmed. Source: `thing-types.xml` channel-type `season-optimizer-mode`.
- **`notifications-active`** (`GET resource/notifications`) - response shape unconfirmed; currently exposed as a raw JSON string. Needs a capture while at least one notification/fault is active, since an empty-state response won't reveal the real shape. Source: `thing-types.xml` channel-type `notifications-active`.

### Zone thermostat heat/cool mode

- **Cool-mode variant of `zones/zone{n}/{heatCoolMode}/manualRoomSetpoint`** - only the `"heat"` segment has been exercised; `ZoneThermostatHandler` hardcodes it (`ZONE_MANUAL_ROOM_SETPOINT_TEMPLATE = "/zones/zone%s/heat/manualRoomSetpoint"`). A gateway actively running a heat pump in cooling mode would be needed to confirm the `"cool"` path segment and whether it needs a separate channel or a mode-switched one. Source: `ZoneThermostatHandler.java` class Javadoc, `BoschThermotechnologyBindingConstants.ResourcePaths`, `thing-types.xml` channel-type `zone-manual-room-setpoint`.

### Cascade heat sources

- **Multiple heat sources per gateway (`hs1`/`hs2`-style sub-ids)** - the tested gateway exposes exactly one heat source with no sub-id, so `HEAT_SOURCE_ID_DEFAULT = "1"` is hardcoded as the single instance id. A cascade system would be needed to confirm the actual sub-id shape and generalize this the same way heating/DHW circuits already are (ADR-006). Source: `BoschThermotechnologyBindingConstants.java` class Javadoc.

### Photovoltaic (only if PV/solar hardware is present)

- **`pv-inverter-info`** (`GET resource/pv/commissioning/inverterInfo`) - response shape unconfirmed; currently exposed as a raw JSON string. Source: `thing-types.xml` channel-type `pv-inverter-info`.

### Ventilation (only if a ventilation/HRV unit is present)

- **`ventilation-operation-mode`** (`GET resource/ventilation/zone1/operationMode`) - exact enumeration of allowed values unconfirmed. Source: `thing-types.xml` channel-type `ventilation-operation-mode`.

### Air conditioning / RAC (only if an AC unit is present)

- **`ac-operation-mode`** (`GET resource/airConditioning/operationMode`) - exact enumeration of allowed values unconfirmed. Source: `thing-types.xml` channel-type `ac-operation-mode`.
- **`ac-fan-speed`** (`GET resource/airConditioning/fanSpeed`) - exact enumeration of allowed values unconfirmed. Source: `thing-types.xml` channel-type `ac-fan-speed`.

### Water softener (only if a water softener is present)

- **Resource path entirely unknown.** `myapp-api-analysis.md` only confirms the `deviceType` string `watersoftener` (`PointtConstants.POINT_DEVICE_TYPE_WATER_SOFTENER`), not any resource-tree prefix under it. Unlike every other item in this document, this one cannot be narrowed down to "capture this one `GET`" - the path itself needs to be discovered first, most likely by inspecting the MyBuderus app's network traffic against a gateway that actually has a water softener attached. Until then, `ChildThingDiscoveryService` does not probe for `water-softener` at all (see ADR-006), so it is never proposed for discovery. Source: `ChildThingDiscoveryService.java` class Javadoc, ADR-006.
