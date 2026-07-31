# Tasks: Separate Real and OH-Managed EEBus Peer Things

## 1. thing-types.xml

- [x] 1.1 Add new `eebus:oh-peer` Thing type, child of `eebus:service` only, `ski` parameter
      labeled "Trusted SKI", `shipId` unchanged. (Scenario: "Adding eebus:oh-peer under
      eebus:service")
- [x] 1.2 Restrict `eebus:peer`'s `supported-bridge-type-refs` to `eebus:network` only; keep
      `ski` labeled "SKI". (Scenarios: "eebus:peer cannot be added under eebus:service",
      "eebus:peer SKI label") — also dropped `shipId` from `eebus:peer` (found during
      implementation: it never performs a handshake, see ADR-009 Context).
- [x] 1.3 Update `eebus:service` Bridge type label to "EEBus OH Service". (Scenario:
      "eebus:service label in Add Thing list")

## 2. Handlers

- [x] 2.1 Add `EEBusOhPeerHandler` implementing the current pairing/trusted-SKI behavior,
      registered for `eebus:oh-peer` only. (Scenarios: "eebus:oh-peer added to a running
      service", "eebus:oh-peer removed from a running service" — trusted-SKI-set assertions
      covered indirectly via `EEBusHandler`'s renamed `currentOhPeerSkis()`/
      `recomputeTrustedSkis()`, not by a dedicated `EEBusHandler` integration test, see §5 below)
- [x] 2.2 Adjust `EEBusPeerHandler` (now `eebus:network`-only) to drop pairing-related
      Javadoc/logic. (Scenario: "eebus:peer Thing has no channels")
- [x] 2.3 Register both handlers in `EEBusHandlerFactory` for their respective Thing types.
- [x] 2.4 Add `THING_TYPE_OH_PEER` constant to `EEBusBindingConstants`; keep `THING_TYPE_PEER`
      for `eebus:peer`.

## 3. Discovery

- [x] 3.1 `EEBusMdnsDiscoveryParticipant` continues to target `THING_TYPE_PEER` (`eebus:peer`)
      as the Inbox result type — unchanged, compatible with the new `supported-bridge-type-refs`
      restriction. Renamed its `isAlreadyPaired(...)` filter to `isAlreadyKnown(...)` (not
      found during spec-writing): `eebus:peer` no longer implies trust, only that a device is
      already known.

## 4. Server-Role Metadata

- [x] 4.1 Updated `EEBusMetadataService#find(...)` to parse the
      `<oh-service-id>:<UseCase>.<Datapoint>` value format and match against the calling
      `eebus:service` Thing's UID segment (`thing.getUID().getId()`, threaded through
      `EEBusMpcServerUseCase`/`EEBusLpcServerUseCase`/`EEBusLppServerUseCase`/
      `AbstractEEBusLimitControllableSystemUseCase`). (Scenario: "Metadata resolves to the
      correct service")
- [x] 4.2 Logs a warning and skips Items whose `eebus` metadata value has no `<oh-service-id>:`
      prefix (missing colon). (Scenario: "Metadata value missing the oh-service-id prefix is
      ignored")
- [x] 4.3 (Found during implementation, not originally listed) `EEBusMpcClientUseCase`'s
      Client-role `find(...)` call was already inconsistent with the (pre-existing, unrelated to
      this change) CONCEPT.md §4.2 decision that Client-role consumption uses dynamic Channels,
      not metadata — it could not be adapted to the new Server-role-only `find(...)` signature.
      Removed that call; the SPINE subscription/measurement-resolution logic is kept intact and
      now logs the received value at debug level as an explicit not-yet-implemented placeholder,
      instead of either breaking the build or silently misusing the new metadata API.

## 5. Tests

- [ ] 5.1 Unit test: `eebus:oh-peer` initialization adds its `ski` to the parent Bridge's
      trusted-SKI set. **Not done** — would require mocking `EEBusHandler`'s Bridge/child-Thing
      lifecycle (`childHandlerInitialized`/`getThing().getThings()`), no existing test harness
      for that in this binding yet. `EEBusOhPeerHandlerTest` covers the handler's own
      online/offline behavior instead.
- [ ] 5.2 Unit test: removing an `eebus:oh-peer` Thing removes its `ski` from the trusted-SKI
      set. **Not done**, same reason as 5.1.
- [x] 5.3 Unit test: `EEBusMetadataService#find(...)` resolves the correct Item when two
      services offer the same use case/datapoint with different `oh-service-id` prefixes
      (`EEBusMetadataServiceTest#whenTwoServicesOfferSameUseCaseThenFindResolvesCorrectService`).
- [x] 5.4 Unit test: `EEBusMetadataService#find(...)` ignores (and logs a warning for) a
      metadata value without an `oh-service-id` prefix
      (`EEBusMetadataServiceTest#whenMetadataValueMissingOhServiceIdPrefixThenFindIgnoresIt`).
- [x] 5.5 (Added during implementation) `EEBusOhPeerHandlerTest`: online with configured SKI and
      an online Bridge, offline with a blank SKI, offline when the Bridge is not online. Copied
      `CallbackMock` fixture into `internal.mock` per `rules/testing-rules.md`.
- **Not verified by an actual build/test run** — this session's sandbox had no working shell
  access (`mcp__workspace__bash` timed out on every attempt). `$QA`/`$Review` or a local Maven
  run still needs to confirm all of the above actually compiles and passes, same caveat as
  `docs/changes/eebus-network-discovery/tasks.md` §4.

## 6. Documentation

- [x] 6.1 Updated binding `README.md` "Supported Things" table with `eebus:oh-peer`, the
      restricted `eebus:peer` description, and the "EEBus OH Service" label; updated the
      "Discovery" section's "already paired" wording to "already known" plus a pointer to
      `oh-peer` for actually pairing.
- [x] 6.2 CONCEPT.md §4.5 already documents the decision (added 2026-08-04, before this `$Dev`
      pass) — no further edits needed.

---

_Change ID: `separate-real-and-oh-peer-things`._
