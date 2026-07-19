# ADR-004: Automatic Discovery Scan When the Account Bridge Goes ONLINE

## Status

> Accepted

## Context

`GatewayDiscoveryService.startScan()` calls `GET /gateways/` and proposes a `gateway` Thing for each result. It is registered per bridge instance directly by `BoschThermotechnologyHandlerFactory` (`bundleContext.registerService(DiscoveryService.class, ...)`), with `backgroundDiscoveryEnabledByDefault=false` in its `AbstractDiscoveryService` super constructor. Until this decision, nothing ever called `startScan()` except a user manually pressing "Scan" for this binding in the Inbox - the README's Discovery section claimed gateways are discovered "automatically" once the bridge is authorized, which was not actually true. This was caught by inspecting a real `TRACE` log: it showed the SingleKey ID token refresh and per-resource `GET`s for a manually-configured `gateway` Thing, but no `GET /gateways/` call at all, and the gateway Thing in question had an obviously invalid, hand-entered `gatewayId`.

## Decision

`AccountBridgeHandler` now accepts a `Runnable discoveryScanTrigger`, set by `BoschThermotechnologyHandlerFactory` right after constructing both the handler and its `GatewayDiscoveryService` (`bridgeHandler.setDiscoveryScanTrigger(() -> discoveryService.startScan(null))`). The handler invokes this trigger, asynchronously via `scheduler.execute(...)`, immediately after every transition to `ThingStatus.ONLINE` - both in `completeAuthorization()` (fresh login) and `refreshAndGoOnline()` (restored session on `initialize()`). Manual scanning from the Inbox continues to work unchanged; this is purely additive.

## Consequences

### Positive

- Matches what the README already (prematurely) claimed: gateways now genuinely appear in the Inbox without a manual scan step, right after the bridge is authorized or restored.
- Fires on every reconnect that brings the bridge back online, not just the very first login, so a gateway added to the account later is picked up the next time the bridge restarts or re-authorizes without the user remembering to press Scan.

### Negative

- A scan (and its `GET /gateways/` call) now also runs on every `initialize()` that successfully restores a session, e.g. after every openHAB restart - a small amount of extra PointT API traffic compared to before, when only an explicit user action triggered it.
- The scan runs fire-and-forget (`scheduler.execute`, no result handling in `AccountBridgeHandler`); failures are only visible via `GatewayDiscoveryService`'s own `logger.debug` in `startScan()`, not surfaced to the bridge's `ThingStatus` - this is intentional (a failed discovery scan must not affect the bridge's own online/offline state) but means a silently failing scan is only visible at DEBUG/TRACE level.
