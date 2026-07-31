# ADR-048: Upgrade jeebus.ship to 3.0.1 and jeebus.spine to 4.1.1

## Status

Accepted. Source migration completed by
docs/ADR/049-configbuilder-ship-node-construction.md - see "Update" below.

## Context

The binding was pinned to `org.openmuc.jeebus:spine:4.0.1` / `org.openmuc.jeebus:ship:2.3.0`.
The user asked to move to `ship:3.0.1` / `spine:4.1.1`. A first pass (chat, 2026-09-17) applied
the two numbers swapped (`ship:4.1.1` / `spine:3.0.1`); the user corrected this immediately
after. Before applying the correction, the pairing was checked against the actual upstream
projects (both mounted locally as `jeebus.ship`/`jeebus.spine`, tracking
`github.com/openmuc/jeebus.ship`/`jeebus.spine` as an `upstream` remote):

- Neither `ship:4.1.1` nor `spine:3.0.1` exist as releases at all - `ship` has never had a
  `4.x` line, and `spine` has never had a `3.x` line. The original request could not have been
  correct.
- `ship:3.0.1` and `spine:4.1.1` do exist, and are the matched pair: `spine`'s own
  `CHANGELOG.md` for `[4.1.1]` reads _"update jEEBus.SHIP dependency to 3.0.1"_ - i.e. `spine`
  4.1.1 is itself built and tested against `ship` 3.0.1. The corrected request is the version
  the upstream projects intend to be used together.
- Both tags were only present on the `upstream` remote at the time of this change - the user's
  own `origin` fork of `jeebus.ship`/`jeebus.spine` had not yet synced them (`origin` topped out
  at `ship:v3.0.0` / `spine:v4.0.1`). Fetching `upstream --tags` pulled `ship:v3.0.1` and
  `spine:v4.1.0`/`v4.1.1` into the local checkouts, but this does not by itself guarantee the
  Maven repository this build resolves `org.openmuc.jeebus` artifacts from (Maven Central, per
  the project's own README badge) already has `3.0.1`/`4.1.1` published - see "Not yet done"
  below.

This ADR documents only the version-pin decision in `org.openhab.binding.eebus/pom.xml`. It does
not modify the `jeebus.ship`/`jeebus.spine` projects themselves, which stay subject to the
project instruction that they require prior human approval to change.

### What changed upstream between the pinned and target versions

`ship` 3.0.0 (3.0.1 is a patch on top of it):

- Overhauled the configuration system: a new `ConfigBuilder` with fluent methods, validation and
  an immutable config object, replacing the deprecated, file-path-only `ShipNodeConfiguration`
  constructor this binding currently uses.
- Deprecated `ClientConnectedListener` in favor of `ConnectionHandler#clientConnected`.
- Deprecated `ShipConnectionInterface#requestAccessMethods` (now automatic).
- Deprecated `ConnectionHandler` itself in favor of `ConnectionMapper`.
- Closes connections that do not respond to `AccessMethodRequests` within 60 seconds, and adds
  double-connection handling at the TLS handshake.

`ship` 3.0.1: _"Properly close and clean up connections with inactive websocket channels"_ - a
bug fix in the same area this project has independently traced multiple times (dispose()/stop()
latency, stuck connections on teardown).

`spine` 4.1.0:

- _"update jEEBus.SHIP dependency to 3.0.0 - replace `ShipCommunication` constructor parameter
  with a `ShipConfig` object"_ - this is the breaking change that affects this binding directly,
  see below.
- Adds `SetpointFeature` (not used by this binding today).
- Cleans up subscriptions and bindings on disconnect; fixes a bug where client subscriptions were
  not stored in `SubscriptionDataFunction` and one where unsuccessful bindings were still stored
  in `BindingDataFunction`.
- Changes the default of `connectClientsTo` from `ALL` to `TRUSTED`.
- Reruns discovery on reconnections.
- Deprecates `Communication#open` in favor of `Communication#openConnection`.

`spine` 4.1.1: _"update jEEBus.SHIP dependency to 3.0.1"_ - the matching patch release.

### Confirmed impact on this binding's source

`EEBusHandler#startShipSpineLocked` (`EEBusHandler.java`, around line 980) constructs:

```java
ShipNodeConfiguration nodeConfig = new ShipNodeConfiguration(resolveBindAddress(cfg),
        resolvedPort, "/ship/", true, shipId, "local.", cfg.mdnsServiceInstance, "eebus",
        keystoreFile.getAbsolutePath(), new char[0], new char[0], distinguishedName, 3650);

ShipCommunication communication = new ShipCommunication(nodeConfig)
        .withTrustedSkis(currentTrustedSkis())
        .withConnectClientsTo(cfg.connectToPeers ? TRUSTED : NONE)
        .withAutoAcceptMode(cfg.autoAcceptEnabled);
```

`spine` 4.1.0's changelog states the `ShipCommunication` constructor parameter changed from
`ShipNodeConfiguration` to a `ShipConfig` object. This will not compile against `spine:4.1.1`
without migrating `nodeConfig` to whatever the new `ConfigBuilder`/`ShipConfig` type is, and
re-verifying where `withTrustedSkis`/`withConnectClientsTo`/`withAutoAcceptMode` now live in the
overhauled API. The binding's own existing javadoc on this method (and on the class-level comment
around line 129) already anticipated exactly this migration as future work once the pinned
versions changed.

`EEBusMdnsBrowser.java` has a javadoc note referencing `ShipCommunication`'s internal
`ConnectionHandler` - worth a compile-time check against the new `ConnectionMapper` deprecation
too.

## Decision

Bump the two direct dependency version pins in `org.openhab.binding.eebus/pom.xml`:

- `org.openmuc.jeebus:spine` - `4.0.1` to `4.1.1`
- `org.openmuc.jeebus:ship` - `2.3.0` to `3.0.1`

Nothing else in `pom.xml` was touched in this pass: the block of roughly fifteen transitive
dependency versions further down (Jackson, Jakarta/Glassfish JAXB, Netty, etc.) stays pinned to
whatever `mvn dependency:tree -Dscope=runtime` resolved against the _old_ `spine:4.0.1`/
`ship:2.3.0` (see ADR-001). No Java source was touched in this pass either.

## Consequences

### Positive

- Moves onto the matched, non-deprecated release pair the upstream projects themselves test
  together, instead of a version combination that does not exist (the original, swapped
  request) or a stale pre-overhaul pair.
- Picks up `ship` 3.0.1's inactive-websocket-channel cleanup fix and `spine` 4.1.0's
  subscription/binding storage fixes, both in areas this project has independently spent
  significant investigation on (see the dispose/stop-latency and use-case-partner-subscription
  ADRs already in this history).
- Once migrated, unblocks the `ConfigBuilder#withCertificateStorage(CertificateStorage)` path
  this binding's own comments already flagged as blocked purely by the old pinned version - a
  separate, not-yet-scoped follow-up, not part of this ADR.

### Negative

- **The module will not compile as-is.** `ShipCommunication`'s constructor signature changed
  (`ShipNodeConfiguration` to `ShipConfig`) - `EEBusHandler.startShipSpineLocked` needs a source
  migration before this is buildable, see "Not yet done" below.
- The transitive dependency block (lines ~56-217 of `pom.xml`) was not re-resolved against the
  new versions. Per ADR-001's embed-dependencies mechanism, a stale or missing entry there can
  surface as a runtime `ClassNotFoundException`/`NoClassDefFoundError` rather than a build
  failure.
- Whether `org.openmuc.jeebus:ship:3.0.1`/`spine:4.1.1` are actually published on whatever
  repository this build resolves against has not been confirmed - only that the tags exist on
  the upstream GitHub repos.
- No Maven/openHAB build environment is available in this session (the same limitation noted in
  every prior ADR) - none of the above has been compiled or tested.

## Not yet done / user-owned

- Confirm `org.openmuc.jeebus:ship:3.0.1` and `:spine:4.1.1` resolve from the repository this
  build actually uses (Maven Central or an internal mirror).
- Re-run `mvn dependency:tree -Dscope=runtime` against the new versions and re-pin the transitive
  dependency block to match, per ADR-001.
- Migrate `EEBusHandler.startShipSpineLocked` off `ShipNodeConfiguration`/the old
  `ShipCommunication(ShipNodeConfiguration)` constructor onto `ship` 3.0.0's `ConfigBuilder`/
  `ShipConfig`, re-verifying where `withTrustedSkis`/`withConnectClientsTo`/`withAutoAcceptMode`
  now live.
- Check `EEBusMdnsBrowser.java`'s `ConnectionHandler` reference against the `ConnectionMapper`
  deprecation.
- `mvn clean install`, then a live retest against both the local simulated rig and the real
  Hager S10, per this project's usual verification pattern.
- This is a breaking-API source migration, not a mechanical version bump - per this project's
  spec-driven workflow, it should go through `$Spec` before `$Dev` touches
  `EEBusHandler.java`/`EEBusMdnsBrowser.java`.

## Update (2026-09-17)

The source migration this ADR flagged as required was scoped via `$Spec`
(`docs/changes/ship-spine-configbuilder-migration/`) and implemented per
docs/ADR/049-configbuilder-ship-node-construction.md: `EEBusHandler#startShipSpineLocked` now
builds a `ShipConfig` via `ConfigBuilder` instead of the removed `ShipNodeConfiguration`/
`ShipCommunication#withTrustedSkis`/`#withAutoAcceptMode`. `EEBusMdnsBrowser.java`'s
`ConnectionHandler` reference was checked against `ship:3.0.1` and found still accurate - not
changed. The transitive dependency block and an actual `mvn clean install` verification remain
outstanding, unchanged from this ADR's original "Not yet done" list.
