# ADR-006: Shared Port Pool for eebus:service SHIP Servers

## Status

> Proposed

## Context

Each `eebus:service` Bridge (`EEBusHandler`) binds its own local SHIP server to a WebSocket
port (`EEBusConfiguration.port`, previously a plain `int` defaulting to `4711` both in the Java
class and in `thing-types.xml`). With more than one `eebus:service` Bridge configured on the same
openHAB instance, that shared default meant every Bridge tried to bind the same port unless the
user manually picked a distinct one for each - with no help from the binding to find a free one,
and no shared bookkeeping to prevent two Bridges from being configured with the same port by
mistake.

## Decision

We introduce `EEBusPortPool` (`internal.transport`), a single OSGi-singleton component that owns
the range of ports `4711`-`4810` (inclusive) available for `eebus:service` SHIP servers:

- `acquireFreePort()` - takes the lowest-numbered free port out of the pool.
- `reservePort(int port)` - removes a specific, explicitly configured port from the pool.
- `releasePort(int port)` - returns a port to the pool.

It is injected into `EEBusHandlerFactory` via `@Reference` (matching the existing
`EEBusMetadataService`/`MDNSClient` dependency-injection pattern) and passed on to each
`EEBusHandler` instance through its constructor.

`EEBusConfiguration.port` changes from `int port = 4711` to `@Nullable Integer port` (no XML
`<default>`), so "not configured" is distinguishable from "configured to 4711". `EEBusHandler`
resolves the actual bind port once, in `initialize()`:

- If `cfg.port` is set, it is reserved via `portPool.reservePort(...)` (removed from the pool so
  `acquireFreePort()` never hands it to a different Bridge).
- If `cfg.port` is unset, `portPool.acquireFreePort()` assigns one automatically.

The resolved port is stored on the handler instance and used to bind the SHIP server
(`ShipNodeConfiguration`). It is returned to the pool in `handleRemoval()` - **not** `dispose()` -
via `portPool.releasePort(...)`, mirroring the `StorageService` lifecycle pattern already
documented in `java-coding-rules.md`: `dispose()` runs on every disable/update/restart cycle, so
releasing the port there would let a different Bridge grab it out from under a Bridge that is
merely restarting; only an actual Thing deletion should give it back.

### Options considered

**Option A - keep the port a required, always-present `int`, add pool bookkeeping only as an
advisory check.** Would still let two Bridges collide on the same default value with no automatic
assignment. Rejected because it does not address the actual problem (no way to say "give me any
free port").

**Option B - nullable configured port + shared `EEBusPortPool` singleton (chosen).** Lets users
either pin a specific port or leave it to the binding, and guarantees uniqueness across all
`eebus:service` Bridges on the same instance via one shared pool.

## Consequences

### Positive

- Multiple `eebus:service` Bridges can be added without manually assigning distinct ports.
- A single shared component prevents two Bridges from silently binding the same port.
- Small, focused helper class (`EEBusPortPool`) with one clear responsibility, easy to unit test
  in isolation from `EEBusHandler`/OSGi.

### Negative

- `EEBusConfiguration.port` changing from `int` to `@Nullable Integer` is a small binary-ish
  behavior change for anyone with `.things` files that relied on the implicit `4711` default
  being applied even when the parameter was omitted - it now stays unset and is auto-assigned
  instead. Existing Things with `port` explicitly written out are unaffected.
- The pool is process-local, in-memory state (not persisted via `StorageService`): a full
  openHAB restart resets it and re-derives reservations from each Bridge's own `initialize()`
  call, which re-reserves/re-acquires as usual - acceptable since the pool's only job is
  preventing collisions between concurrently running Bridges, not remembering assignments across
  restarts.
- Repeated `initialize()` calls on the same already-running Bridge without an intervening
  `handleRemoval()` (e.g. a config update) call `reservePort`/`acquireFreePort` again; for an
  auto-assigned port this can hand out a second port if `dispose()`/`initialize()` runs more than
  once for the same Thing (see ADR-005's still-open "why does `initialize()` run more than once"
  question) without releasing the first. Not fully closed by this change - flagged for follow-up
  alongside ADR-005 rather than solved here, to avoid entangling this change with that unresolved
  investigation.

---

_Introduces `EEBusPortPool`; changes `EEBusConfiguration.port` to `@Nullable Integer`; wires
port resolution into `EEBusHandler#initialize()`/`#handleRemoval()`._
