# Tasks: Merge eebus:network and eebus:peer into a Bridgeless Thing

## 1. Thing model

- [x] 1.1 `thing-types.xml`: remove `bridge-type id="network"`; change `thing-type id="peer"` to
      `id="eebus-peer"` with no `<supported-bridge-type-refs>`.
- [x] 1.2 `EEBusBindingConstants.java`: remove `THING_TYPE_NETWORK`; point `THING_TYPE_PEER` at
      `"eebus-peer"` instead of `"peer"`.
- [x] 1.3 `EEBusHandlerFactory.java`: drop the `EEBusNetworkHandler` import, its creation branch,
      and `THING_TYPE_NETWORK` from `SUPPORTED_THING_TYPES_UIDS`.
- [x] 1.4 Delete `EEBusNetworkHandler.java` (moved to `_to_delete/` - this sandbox's filesystem
      mount rejects delete/unlink, same limitation noted on `EEBusDiscoveryService.java` since
      ADR-003; user to delete both manually).
- [x] 1.5 `EEBusPeerHandler.java`: remove `bridgeStatusChanged`/`applyBridgeStatus`; go `ONLINE`
      directly once `ski` is non-blank, matching "eebus:eebus-peer goes ONLINE without a Bridge".
- [x] 1.6 `EEBusPeerConfiguration.java`: update javadoc (bridgeless, not a child of
      `eebus:network`).

## 2. Discovery

- [x] 2.1 `EEBusMdnsDiscoveryParticipant.java`: remove `findNetworkBridgeUid()`; build
      `new ThingUID(THING_TYPE_PEER, ski)` (no bridge) in both `createResult()` and
      `getThingUID()`; drop `.withBridge(...)` from the `DiscoveryResultBuilder` call.
      `isAlreadyKnown()`/`isOwnService()` (ADR-008) are unaffected and stay as-is.
- [x] 2.2 Delete `EEBusDiscoveryService.java` (already an empty, unreferenced ADR-003 stub -
      moved to `_to_delete/` alongside `EEBusNetworkHandler.java`, same mount limitation).

## 3. Tests

- [x] 3.1 `EEBusMdnsDiscoveryParticipantTest.java`: drop `THING_TYPE_NETWORK`/`NETWORK_UID`
      fixtures; replace "no network bridge -> null" with "top-level UID, no bridge"; rebuild the
      already-known-SKI fixture around the bridgeless `ThingUID`.
- [ ] 3.2 `mvn clean install` - not runnable in this sandbox (no local Maven, confirmed again for
      `device_bash`'s Linux VM). User-owned, per the existing verification queue in project
      memory.

## 4. Documentation

- [x] 4.1 `docs/ADR/020-merge-network-peer-things.md`: new ADR, superseding ADR-003 decision 1
      and ADR-004 in full.
- [x] 4.2 `docs/ADR/003-decouple-mdns-discovery-from-bridge.md` /
      `docs/ADR/004-retry-mdns-discovery-on-network-bridge-added.md`: Status updated to
      `Superseded by ADR-020`.
- [x] 4.3 `docs/changes/eebus-network-discovery/proposal.md` /
      `docs/changes/mdns-discovery-retry/proposal.md`: superseded-by note added at the top; left
      in place (not archived - archiving is reserved for changes whose deltas get merged into
      `docs/specs/`, which does not apply to requirements being removed instead).
- [x] 4.4 `CONCEPT.md`: Thing-model table (§3-ish overview), §4.5, §7 item (15) marked done, Thing-
      type count references (4 → 3).
- [x] 4.5 `README.md`: "Supported Things" bullets, "Discovery" section.
- [x] 4.6 `TEST_PAIRING.md`: the two `eebus:network`-specific passages (intro paragraph, step 1
      parenthetical). Its pre-existing, unrelated "peer under service" inconsistency in step 4
      is out of scope (see proposal.md).
- [x] 4.7 `SKI.md`: note that its captured `eebus:peer:<networkId>:<ski>` UID reflects the old,
      bridged model - a freshly discovered device now gets `eebus:eebus-peer:<ski>`.

## 5. Risk accepted

This proceeds ahead of the pending live retest/build for `lpc-lpp-limitid-resolution`
(ADR-018/019 - see project memory's verification queue), per explicit user direction
(2026-08-23). Consequence to watch for: the user's next live-retest log will exercise a build
that contains both this change and the still-unverified ADR-019 fix at once, so a discovery- or
Thing-status-shaped surprise in that log needs to be checked against _this_ change first before
being attributed to ADR-019.

---

_Change ID: `merge-network-peer-things`._
