# Tasks: SHIP Node Construction Migration to ConfigBuilder/ShipConfig

## 1. EEBusHandler SHIP node construction

- [x] 1.1 Build a `ship.api.cert.KeyStoreCertificateStorage` from
      `getKeystoreFile().getAbsolutePath()`, alias `"eebus"`, and empty passphrases, matching
      today's values.
- [x] 1.2 Build a `ShipConfig` via `ConfigBuilder.aShipConfig()` with `withId(shipId)`,
      `withCertificateDistinguishedName(distinguishedName)`, `withCertificateValidity(3650)`,
      `withCertificateStorage(...)` (1.1), `withMDnsServiceInstance(cfg.mdnsServiceInstance)`,
      `withMDnsDomain("local.")`, `withWssPath("/ship/")`, `withKeepAlive(true)`,
      `withTrustedSkis(currentTrustedSkis())`, `withAutoAcceptEnabled(cfg.autoAcceptEnabled)`,
      and the resolved bind address/port (see Open Question in proposal.md).
- [x] 1.3 Replace `new ShipCommunication(nodeConfig)` with `new ShipCommunication(shipConfig)`,
      keeping `.withConnectClientsTo(cfg.connectToPeers ? TRUSTED : NONE)` unchanged.
- [x] 1.4 Remove the now-unused `ShipNodeConfiguration` import and any now-dead code.

## 2. Documentation cleanup

- [x] 2.1 Update `EEBusHandler.java`'s class-level javadoc (around the
      `ShipNodeConfiguration`/`CONCEPT.md §7 item 1` note) to reflect the new
      `ConfigBuilder`/`ShipConfig` API and current pinned versions (`ship:3.0.1`/`spine:4.1.1`).
- [x] 2.2 Update the inline comment above `startShipSpineLocked`'s SHIP node construction (the
      one citing `ship:2.2.0`/`ship:2.3.0`/`spine:4.0.1`) the same way.
- [x] 2.3 Update `docs/ADR/010-storageservice-keystore-mirror.md` if its description of "the
      pinned jeebus.ship/jeebus.spine versions do not support pluggable CertificateStorage" is
      now stale - note that it is now supported but not yet adopted (see proposal.md's "Out of
      scope"), rather than removing the constraint outright.

## 3. EEBusMdnsBrowser review

- [x] 3.1 Check the `ConnectionHandler` javadoc reference in `EEBusMdnsBrowser.java` against
      `spine:4.1.1`'s actual API; correct the comment if it now names removed/renamed types. No
      functional change expected.

## 4. Verification

- [ ] 4.1 `mvn clean install` (user-run; no Maven/openHAB build environment available in this
      session) and confirm the four reported compile errors are resolved.
- [ ] 4.2 Live retest against the local simulated rig and the real Hager S10 per this project's
      usual verification pattern, confirming no behavior change (SKI trust, auto-accept, mDNS
      advertisement, bind address/port all unchanged).
