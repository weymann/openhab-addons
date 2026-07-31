# Proposal: SHIP Node Construction Migration to ConfigBuilder/ShipConfig

## Intent

ADR-048 bumped `org.openmuc.jeebus:ship` to `3.0.1` and `org.openmuc.jeebus:spine` to `4.1.1`
in `pom.xml`. Building against these versions now fails with four compile errors:
`EEBusHandler#startShipSpineLocked` constructs the SHIP node with the deprecated, now-removed
`ShipNodeConfiguration` thirteen-argument constructor and passes it straight to
`new ShipCommunication(nodeConfig)`, then chains `.withTrustedSkis(...)` on the result - none of
these three symbols exist any more in the pinned versions (`.withAutoAcceptMode(...)` is also
gone, though the compiler did not reach that line). This change migrates that one construction
site onto `ship` 3.0.0's `ConfigBuilder`/`ShipConfig` API so the binding compiles again against
the versions ADR-048 already committed to, without changing any observable behavior.

## Scope

In scope:

- Replace the `ShipNodeConfiguration` construction in `EEBusHandler#startShipSpineLocked` with a
  `ShipConfig` built via `ConfigBuilder`.
- Replace the keystore-file-based certificate handling (`getKeystoreFile()`,
  `restoreKeystoreFromStorage`, `persistKeystoreToStorage`, all unchanged) with a
  `KeyStoreCertificateStorage` instance passed to `ConfigBuilder#withCertificateStorage`, using
  the same alias (`"eebus"`) and empty passphrases (`new char[0]`) as today.
- Move `withTrustedSkis(currentTrustedSkis())` from `ShipCommunication` onto
  `ConfigBuilder#withTrustedSkis`.
- Move `withAutoAcceptMode(cfg.autoAcceptEnabled)` from `ShipCommunication` onto
  `ConfigBuilder#withAutoAcceptEnabled` (deprecated since ship 3.0.0, but still functional - no
  alternative exists).
- Update `ShipCommunication`'s construction to `new ShipCommunication(shipConfig)`, keeping the
  existing `.withConnectClientsTo(cfg.connectToPeers ? TRUSTED : NONE)` call unchanged (that
  method still exists on `ShipCommunication` in `spine:4.1.1`).
- Preserve every currently observable value 1:1: SHIP ID, distinguished name, bind address,
  port, `wssPath` (`"/ship/"`), `keepAlive` (`true`), mDNS service instance/domain, certificate
  validity (`3650` days).
- Update the stale in-code comments/javadoc in `EEBusHandler.java` that reference the old pinned
  versions (`ship:2.2.0`/`2.3.0`, `spine:4.0.1`) and the old constructor, so they describe the
  versions and API this change leaves in place.
- Review `EEBusMdnsBrowser.java`'s javadoc note about `ShipCommunication`'s internal
  `ConnectionHandler` against the `ConnectionMapper` deprecation and correct it if it now names
  removed API - comment-only, no behavior change expected.

Out of scope:

- Adopting a `StorageService`-backed `CertificateStorage` implementation to retire ADR-010's
  manual keystore-file mirroring - `ship` 3.0.0's pluggable `CertificateStorage` makes that
  possible now, but it is a separate, larger change with its own trade-offs.
- Any behavior change from `spine` 4.1.0's other changes (`SetpointFeature`, discovery rerun on
  reconnect, subscription/binding cleanup-on-disconnect) - these are internal library fixes this
  binding does not call into.
- Re-pinning the transitive dependency block in `pom.xml` (Jackson/JAXB/Netty versions) - already
  flagged in ADR-048 as needing a live `mvn dependency:tree` run this environment cannot perform.
- Migrating off `ship` 3.0.0's other new deprecations (`ClientConnectedListener`,
  `requestAccessMethods`) - neither is referenced anywhere in this binding's source (confirmed by
  search), so there is nothing to migrate.
- Any change to the `jeebus.ship`/`jeebus.spine` projects themselves.

## Open Questions

- `ConfigBuilder` binds addresses via `withServerBindAddresses`, either as a
  `Set<InetSocketAddress>` or as address strings (`"host:port"`, with IPv6 literals bracketed).
  `resolveBindAddress(cfg)` returns a plain address `String` today with the port handled
  separately (`resolvedPort`). `$Architect`/`$Dev` should decide whether to build an
  `InetSocketAddress` directly (avoids the bracket-formatting edge case for IPv6) or format a
  combined string - recommend the `InetSocketAddress` overload.
- `ConfigBuilder` exposes new `brand`/`type`/`model` fields (mDNS TXT record) that the old
  constructor had no equivalent for and this binding has never set. Leave them at their defaults
  (`"jEEBus"`/`"default"`/`"default"`) to preserve current behavior, or use this as an
  opportunity to populate them from `cfg.vendorCode`/`cfg.deviceModel`? Leaning toward leaving
  as-is (out of scope per above) but flagging since it is a visible new capability.
