# Tasks: EEBus Network Discovery

## 1. eebus:network Thing Type

- [x] 1.1 Add `eebus:network` bridge-type to `thing-types.xml` with no mandatory configuration
      parameters. (Scenario: "Adding eebus:network with no parameters")
- [x] 1.2 Add `THING_TYPE_NETWORK` constant to `EEBusBindingConstants`.
- [x] 1.3 Add a minimal `EEBusNetworkHandler extends BaseBridgeHandler` that goes `ONLINE` in
      `initialize()` with no further setup. Register it in `EEBusHandlerFactory`.

## 2. mDNS Discovery Participant

- [x] 2.1 Add a new `EEBusMdnsDiscoveryParticipant implements MDNSDiscoveryParticipant`
      (`org.openhab.core.config.discovery.mdns`), registered as an OSGi `@Component` with no
      dependency on any Thing handler. (Scenario: "Binding installed, no Thing configured yet")
- [x] 2.2 `getServiceType()` returns `_ship._tcp.local.`; `getSupportedThingTypeUIDs()` returns
      `Set.of(THING_TYPE_PEER)`.
- [x] 2.3 `createResult(ServiceInfo)`/`getThingUID(ServiceInfo)`: look up an existing
      `eebus:network` Thing via `ThingRegistry` (`@Reference`); return `null` if none exists.
      (Scenarios: "Device discovered before eebus:network exists", "Device discovered after
      eebus:network exists")
- [x] 2.4 Read `ski`/`brand`/`model`/`type` from the TXT record; skip (return `null`) if `ski`
      is missing or blank. (Scenario: "Missing ski TXT field") — extracted into the shared
      `EEBusShipTxtRecord.from(ServiceInfo)` helper (`internal.transport`), used by both
      `EEBusMdnsDiscoveryParticipant` and `EEBusMdnsBrowser` instead of duplicating the parsing.
- [x] 2.5 Exclude SKIs that already belong to an existing `eebus:peer` Thing (iterate
      `ThingRegistry`, filter by `THING_TYPE_PEER` + `ski` configuration property). (Scenario:
      "Skip already-paired SKI")
- [x] 2.6 Build the result label from brand+model, falling back to the mDNS service instance
      name, matching the previous `EEBusDiscoveryService` label logic.

## 3. Remove Bridge-scoped real-device discovery

- [x] 3.1 Remove `EEBusDiscoveryService` (the `AbstractThingHandlerDiscoveryService`-based
      class) — its responsibility is replaced by `EEBusMdnsDiscoveryParticipant`. **Caveat:**
      the working copy's filesystem mount rejected file deletion (`rm`/`unlink` returned
      "Operation not permitted", while writing/overwriting content worked). The file was
      instead emptied of all logic and its `@Component` registration, so it has zero runtime
      effect — still needs a manual `git rm`/delete before this change is merged.
- [x] 3.2 Remove its registration from `EEBusHandler#getServices()` (the override itself was
      removed, since the base class default is sufficient once nothing needs registering).
- [x] 3.3 Confirm `EEBusMdnsBrowser` (the `javax.jmdns.ServiceListener`-based class) keeps its
      SKI ↔ `communicationAddress` mapping for an active `eebus:service` session (§5.4 TODO 2 in
      `CONCEPT.md`) — a separate concern from Inbox population. Added a class-level comment
      clarifying why two mDNS listeners coexist.
- [x] 3.4 (Added during implementation, not originally listed) Removed
      `EEBusMdnsBrowser.Listener`/`addListener`/`removeListener`/`notifyListeners()` — this
      observer mechanism existed solely to feed the now-removed `EEBusDiscoveryService` and had
      no other subscriber; left in place it would have been dead code with no compiler warning
      to catch it. Also removed `EEBusHandler#pairedSkis()` for the same reason (its only
      caller was the removed discovery service; `EEBusMdnsDiscoveryParticipant` checks
      `eebus:peer` Things across the whole `ThingRegistry` directly instead, since it is not
      tied to one specific `eebus:service` Bridge).

## 4. Tests

- [x] 4.1 Unit test: `createResult()` returns `null` when no `eebus:network` Thing exists.
- [x] 4.2 Unit test: `createResult()` returns a result with the correct bridge UID when an
      `eebus:network` Thing exists.
- [x] 4.3 Unit test: blank/missing `ski` TXT field yields no result.
- [x] 4.4 Unit test: SKI matching an existing `eebus:peer` Thing yields no result.
- [x] 4.5 Unit test: label falls back to service instance name when brand and model are both
      blank.
- **Not verified by an actual build/test run** — the session's sandbox only has Java 11 and no
  `mvn` on `PATH`, and this binding targets Java 21. `$QA`/`$Review` (or a local Maven run)
  still needs to confirm these tests actually compile and pass.

## 5. Documentation

- [x] 5.1 Update `CONCEPT.md` §4.1/§7 to reflect the new discovery design (supersedes the
      Bridge-scoped `EEBusDiscoveryService` description).
- [x] 5.2 Add `eebus:network` to the binding `README.md` Thing overview table, and describe the
      new discovery behavior in the README's Discovery section. (Note: `README.md` was still
      the unfilled openHAB archetype template otherwise — only the "Supported Things" and
      "Discovery" sections were touched; filling in the rest is out of scope for this change.)

---

_Change ID: `eebus-network-discovery`._
