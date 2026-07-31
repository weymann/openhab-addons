# ADR-007: Corrected `GET /gateways/` and refEnum List Response Shapes (Confirmed Against a Live Gateway)

## Status

> Accepted

## Context

Two response shapes used by discovery were never enumerated in the reverse-engineering sources this binding is based on (`buderus-reverse.md`, `myapp-api-analysis.md`) and were left as explicit TODOs on `GatewayDto` and `ResourceListEntryDto`, to be verified "before release." A user-supplied `TRACE` log from a real gateway surfaced both assumptions as wrong, and explained why the gateway never appeared in the Inbox:

1. `GET /gateways/` returned `[{"deviceId":"...","deviceType":"k40"}]`. `GatewayDto` mapped the id field as `id`, not `deviceId`, so `gateway.id` was always `null`. `GatewayDiscoveryService.startScan()` correctly detected this (`"Skipping a gateway list entry without an id"`) and silently skipped the only gateway on the account - the gateway Thing was never proposed.
1. `GET resource/heatingCircuits` returned `{"id":"/heatingCircuits","type":"refEnum","references":[{"id":"/heatingCircuits/hc1","uri":"http://k40/heatingCircuits/hc1"}]}` - a single object of type `refEnum` with a nested `references` array - not the bare `[{"id":"hc1"}, ...]` array `ResourceListEntryDto`/`PointTApiClient.listResourceIds()` assumed. Every call to `listResourceIds()` (`heatingCircuits`, `dhwCircuits`, `solarCircuits`, `zones/list`) therefore failed with `JsonSyntaxException` ("Could not parse list response for path ..."), which meant `ChildThingDiscoveryService` could never discover a `heatpump`, `pv`, or `zone-thermostat` child thing either, even once a gateway Thing exists.

## Decision

- `GatewayDto.id` renamed to `deviceId` to match the confirmed field name; `GatewayDiscoveryService` reads `gateway.deviceId`.
- `PointTApiClient.listResourceIds()` now parses the response body as a `ResourceReferenceListDto` (`id`, `type`, `references: List<ResourceListEntryDto>`) instead of a bare `List<ResourceListEntryDto>`. `ResourceListEntryDto` gained a `uri` field and its `id` is now understood to be the _full resource path_ of the reference (e.g. `/heatingCircuits/hc1`); `listResourceIds()` takes the last `/`-segment as the circuit/zone id (e.g. `hc1`) before returning it, since that bare id is what `ChildThingDiscoveryService`/`HeatpumpHandler` use to build `ThingUID`s and substitute into path templates like `/heatingCircuits/%s/manualRoomSetpoint`.
- Both DTO TODOs are resolved and removed; their Javadoc now documents the confirmed shapes directly, including a JSON example.

## Consequences

### Positive

- Gateways registered to an account are now actually discovered - the root cause of "the device doesn't show up in discovery" is fixed, not just the symptom.
- `heatpump`/`pv`/`zone-thermostat` child-thing discovery (ADR-006) can now succeed too, since it depends on the same `listResourceIds()` method.
- The DTO Javadoc TODOs asking for live-gateway verification are resolved with an authoritative example instead of a guess.

### Negative

- `dhwCircuits`, `solarCircuits`, `pv/list`, and `zones/list` are assumed to share the same `refEnum`/`references` shape as `heatingCircuits` by analogy (same generic resource-tree API design per the `PointTApiClient` class Javadoc) - only `heatingCircuits` was confirmed directly against a live gateway. If one of these turns out to differ, `listResourceIds()` will again throw a parse error for that path specifically.
- `GatewayDto.firmwareVersion`/`hardwareVersion` remain unverified - the trace log shows those are actually fetched via separate `resource/gateway/versionFirmware`/`versionHardware` calls, not as fields of the `/gateways/` list response, so these two `GatewayDto` fields are currently dead code kept only for a possible future `GET /gateways/{gatewayId}` detail endpoint.
