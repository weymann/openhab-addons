# Delta for Discovery

## MODIFIED Requirements

### Requirement: Discovered devices no longer require an existing Bridge

(Previously, in `eebus-network-discovery` as modified by the unimplemented
`mdns-discovery-retry`: "The binding SHALL only create an Inbox entry for a discovered real
EEBUS device once at least one `eebus:network` Bridge Thing exists", with a Bridge-add listener
proposed to re-surface anything seen earlier. That listener was never implemented
(`mdns-discovery-retry/tasks.md`, 0/12) - this change removes the precondition instead of
finishing the listener.)

The binding SHALL create an Inbox entry of type `eebus:eebus-peer` for every discovered real
EEBUS device with a non-blank `ski` TXT record field, regardless of whether any other eebus
Thing exists, except a SKI already known (see "Already-paired devices are excluded" and
"Own-service devices are excluded", unchanged) below.

#### Scenario: Binding installed, no Thing configured yet

- GIVEN the eebus binding is installed
- AND no eebus Thing of any type exists
- WHEN a real EEBUS/SHIP device advertises itself via `_ship._tcp.local.` mDNS
- THEN an Inbox entry of type `eebus:eebus-peer` is created immediately
- AND no error or "missing bridge" warning is logged

#### Scenario: Inbox entry has no parent Bridge

- WHEN an Inbox entry is created for a discovered device
- THEN its `bridgeUID` SHALL be absent
- AND its label is the brand and model from the TXT record if either is present, otherwise the
  mDNS service instance name
- AND its `ski` property is set from the TXT record

## REMOVED Requirements

### Requirement: eebus:network Bridge requires no mandatory configuration

(Introduced by `eebus-network-discovery`. The Thing type itself is removed - see
`specs/thing-model/spec.md` "eebus:peer is a bridgeless top-level Thing type" - so there is no
longer a Bridge for this requirement to describe.)

### Requirement: Device seen before the Bridge existed becomes discoverable once it is added

(Introduced by `mdns-discovery-retry`, never implemented. Moot: without a mandatory Bridge,
every device is evaluated on its own next mDNS announcement/scan with no "orphaned until a
Bridge shows up" state to recover from.)

---

_Change ID: `merge-network-peer-things`. Domain: `discovery`. Supersedes
`eebus-network-discovery`'s Bridge-gating requirement and all of `mdns-discovery-retry`; applied
against those changes' pending deltas since `docs/specs/discovery/spec.md` does not exist yet.
`eebus-network-discovery`'s other requirements (background scanning independent of
`eebus:service`, blank-SKI/already-paired exclusions, ADR-008's own-service exclusion) are
unaffected and remain in force._
