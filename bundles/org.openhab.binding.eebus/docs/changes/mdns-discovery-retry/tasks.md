# Tasks: Retry mDNS Discovery When an eebus:network Bridge Is Added

## 1. Bridge-add listener

- [ ] 1.1 Add a component (either a new class or a method on `EEBusMdnsDiscoveryParticipant`,
      `$Architect`/`$Dev` to decide during implementation) that registers a
      `ThingRegistryChangeListener` with `ThingRegistry` on `@Activate` and deregisters it on
      `@Deactivate`.
- [ ] 1.2 On `added(Thing thing)`, if `thing.getThingTypeUID().equals(THING_TYPE_NETWORK)`, call
      `discoveryServiceRegistry.startScan(THING_TYPE_PEER, null)`.
- [ ] 1.3 Inject `DiscoveryServiceRegistry` as an OSGi `@Reference`.
- [ ] 1.4 Verify the exact `DiscoveryServiceRegistry.startScan` method signature against the
      openHAB core javadoc for the core version this binding builds against, before compiling.

## 2. Tests mapped to spec scenarios

- [ ] 2.1 Test: "Device discovered before eebus:network exists, still remains undiscovered" - no
      Inbox entry yet, no error.
- [ ] 2.2 Test: "Device discovered after eebus:network exists" - unchanged existing behavior,
      regression check only.
- [ ] 2.3 Test: "Device seen before the Bridge existed becomes discoverable once it is added" -
      simulate a cached mDNS record, add the Bridge, assert a scan is triggered and the entry
      appears.
- [ ] 2.4 Test: "Device's mDNS announcement has expired before the Bridge is added" - simulate an
      empty mDNS cache at Bridge-add time, assert no Inbox entry is created automatically.
- [ ] 2.5 Test: "Already-paired device is not re-added when the Bridge appears" - existing
      `eebus:peer` Thing with SKI `X`, assert no duplicate entry after Bridge add.
- [ ] 2.6 Test: "Multiple pending devices become discoverable together" - two or more cached
      records, assert one triggered scan surfaces all of them.

## 3. Documentation

- [ ] 3.1 Update `EEBusMdnsDiscoveryParticipant`'s class Javadoc (or the new listener's) to
      reference ADR-004 and explain why a Bridge-add listener exists.
- [ ] 3.2 On `$Release`, archive this change folder and merge its delta into
      `docs/specs/discovery/spec.md` - together with `eebus-network-discovery`'s delta if that
      change has not been archived first (see proposal.md, Open Questions).

---

_Change ID: `mdns-discovery-retry`._
