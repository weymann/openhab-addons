# ADR-049: ConfigBuilder-Based SHIP Node Construction (ship 3.0.1/spine 4.1.1 Migration)

## Status

Accepted

## Context

ADR-048 bumped `org.openmuc.jeebus:ship` to `3.0.1` and `:spine` to `4.1.1`, which broke
compilation: `EEBusHandler#startShipSpineLocked` built a `ShipNodeConfiguration` (removed) and
chained `.withTrustedSkis(...)`/`.withAutoAcceptMode(...)` on `ShipCommunication` (both removed
from that class). `docs/changes/ship-spine-configbuilder-migration/proposal.md` scoped the fix
and left two open questions for this ADR to resolve.

## Decision

### 1. Bind address via `InetSocketAddress`, not a formatted string

`ConfigBuilder#withServerBindAddresses` accepts either a `Set<InetSocketAddress>` or address
strings (`"host:port"`, IPv6 literals require manual bracket-wrapping per its javadoc).
`resolveBindAddress(cfg)` returns a bare address literal (IPv4 dotted-quad or a bracket-free IPv6
literal, depending on `cfg.preferIpv4`/`networkAddressService.isUseIPv6()`), with the port handled
separately as `resolvedPort`. Building `InetAddress.getByName(resolveBindAddress(cfg))` and
wrapping it in `new InetSocketAddress(address, resolvedPort)` avoids reconstructing the
`"[ipv6]:port"` string format by hand for the IPv6 path - a formatting mistake there would
silently fail to bind the intended interface with no compile-time signal, only a runtime one.

### 2. Certificate storage via `KeyStoreCertificateStorage`

`new KeyStoreCertificateStorage(keystoreFile.getAbsolutePath(), "eebus", new char[0], new
char[0])`, passed to `ConfigBuilder#withCertificateStorage`, is a direct drop-in replacement for
the old constructor's `certPath`/`alias`/`keyStorePassphrase`/`keyPairPassphrase` parameters -
same four values, same file. `docs/ADR/010-storageservice-keystore-mirror.md`'s
`restoreKeystoreFromStorage`/`persistKeystoreToStorage` mirroring stays unchanged; it operates on
the same `keystoreFile` regardless of which SHIP-side type ultimately reads it.

### 3. mDNS `brand`/`type`/`model` left at `ConfigBuilder` defaults

`ConfigBuilder` exposes new `brand`/`type`/`model` mDNS TXT-record fields the old thirteen-argument
constructor had no equivalent for. Left at their defaults (`"jEEBus"`/`"default"`/`"default"`) -
populating them from `cfg.vendorCode`/`cfg.deviceModel` is a genuine new capability, but a
separate, not-yet-scoped product decision (see proposal.md's "Out of scope"), not something this
compile-fix should decide implicitly.

### 4. `trustedSkis`/`autoAcceptEnabled` move to `ConfigBuilder`

`ConfigBuilder#withTrustedSkis(currentTrustedSkis())` and
`ConfigBuilder#withAutoAcceptEnabled(cfg.autoAcceptEnabled)` replace the removed
`ShipCommunication#withTrustedSkis`/`#withAutoAcceptMode` calls - same values, relocated onto the
`ShipConfig` build per the new API. `withAutoAcceptEnabled` is deprecated since ship 3.0.0 (the
EEBus Initiative discourages auto-accept), but remains the only available mechanism - no
alternative exists, and this binding's own `EEBusConfiguration#autoAcceptEnabled` already documents
the same caution independently.

### 5. `withConnectClientsTo` stays on `ShipCommunication`, unchanged

Unlike the above, `ShipCommunication#withConnectClientsTo(ConnectClientsTo)` was not removed in
`spine:4.1.1` - `.withConnectClientsTo(cfg.connectToPeers ? TRUSTED : NONE)` is kept exactly as
it was, chained on `new ShipCommunication(shipConfig)`.

### Alternative considered: format a `"host:port"` string

Concatenate `resolveBindAddress(cfg) + ":" + resolvedPort` and use
`ConfigBuilder#withServerBindAddresses(String...)`.

- **Pros:** one fewer import (`InetAddress`/`InetSocketAddress` already needed elsewhere is a
  wash either way); marginally shorter.
- **Cons:** silently wrong for the IPv6 path, since that overload requires bracket-wrapped IPv6
  literals (`"[::1]:8080"`) which `resolveBindAddress` does not produce - would need a
  conditional bracket-insertion helper duplicating logic `InetSocketAddress`'s constructor already
  handles correctly. Rejected as needless, error-prone string formatting.

## Consequences

### Positive

- Behavior-preserving: every value carried over 1:1, no change to SHIP ID, distinguished name,
  bind address/port, `wssPath`, `keepAlive`, mDNS service instance/domain, certificate validity,
  trusted SKIs, auto-accept, or `connectClientsTo` semantics.
- Resolves the four compile errors reported against `ship:3.0.1`/`spine:4.1.1`.
- The `InetSocketAddress`-based bind address is more robust for the IPv6-preferred path than the
  string format would have been.

### Negative

- Three new imports (`org.openmuc.jeebus.ship.api.ConfigBuilder`,
  `org.openmuc.jeebus.ship.api.cert.KeyStoreCertificateStorage`,
  `org.openmuc.jeebus.ship.node.ShipConfig`), replacing the single
  `org.openmuc.jeebus.ship.api.ShipNodeConfiguration` import removed.
- Not yet compiled or live-tested - no Maven/openHAB build environment available in this session
  (same limitation noted in every prior ADR). Self-QA done on the touched method: brace/paren
  balance and import correctness checked by static review of the diff.

---

_Implements the decision proposal.md's "Open Questions" deferred to `$Architect` in
docs/changes/ship-spine-configbuilder-migration/. Refines ADR-048 (which bumped the versions but
explicitly left this migration undone) and touches, without changing, the mechanism
docs/ADR/010-storageservice-keystore-mirror.md established._
