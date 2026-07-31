# Delta for Discovery

## MODIFIED Requirements

### Requirement: Discovered devices require an existing eebus:network Bridge

(Previously: a device seen via mDNS before any `eebus:network` Bridge Thing existed was silently
and permanently dropped — no Inbox entry was ever created for it unless the device (or another
announcement of the same service) was seen again after a Bridge existed.)

The binding SHALL only create an Inbox entry for a discovered real EEBUS device once at least one
`eebus:network` Bridge Thing exists. The binding SHALL re-evaluate devices still resolvable via
mDNS at the moment an `eebus:network` Bridge Thing is added, without requiring a user-triggered
scan or a fresh mDNS announcement from the device.

#### Scenario: Device discovered before eebus:network exists, still remains undiscovered

- GIVEN no `eebus:network` Bridge Thing exists
- WHEN a real EEBUS/SHIP device is discovered via mDNS
- THEN no Inbox entry is created for that device yet

#### Scenario: Device discovered after eebus:network exists

- GIVEN an `eebus:network` Bridge Thing exists and is `ONLINE`
- WHEN a real EEBUS/SHIP device is discovered via mDNS with a non-blank `ski` TXT record field
- THEN an Inbox entry of type `eebus:peer` is created as a child of that `eebus:network` Bridge
- AND the entry's label is the brand and model from the TXT record if either is present,
  otherwise the mDNS service instance name
- AND the entry's `ski` property is set from the TXT record

#### Scenario: Device seen before the Bridge existed becomes discoverable once it is added

- GIVEN no `eebus:network` Bridge Thing exists
- AND a real EEBUS/SHIP device has already been seen via mDNS with a non-blank `ski` TXT record
  field
- AND that device's mDNS announcement is still present in the mDNS cache (i.e. a manual scan
  performed right now would find it)
- WHEN a user adds an `eebus:network` Bridge Thing
- THEN an Inbox entry of type `eebus:peer` is created for that device as a child of the new
  `eebus:network` Bridge
- AND this happens without the device sending a new mDNS announcement
- AND this happens without the user triggering a manual Inbox scan

#### Scenario: Device's mDNS announcement has expired before the Bridge is added

- GIVEN no `eebus:network` Bridge Thing exists
- AND a real EEBUS/SHIP device was seen via mDNS at some earlier point
- AND that device's mDNS announcement is no longer present in the mDNS cache by the time a
  `eebus:network` Bridge Thing is added (i.e. a manual scan performed right now would not find
  it either)
- WHEN a user adds an `eebus:network` Bridge Thing
- THEN no Inbox entry is created automatically for that device
- AND the device becomes discoverable again the normal way once it next announces itself, or via
  a manual scan while its announcement is present

#### Scenario: Already-paired device is not re-added when the Bridge appears

- GIVEN no `eebus:network` Bridge Thing exists
- AND a device advertising SKI `X` has already been seen via mDNS
- AND an `eebus:peer` Thing with SKI `X` already exists (paired through any means)
- WHEN a user adds an `eebus:network` Bridge Thing
- THEN no Inbox entry is created for SKI `X`

#### Scenario: Multiple pending devices become discoverable together

- GIVEN no `eebus:network` Bridge Thing exists
- AND two or more real EEBUS/SHIP devices with distinct, non-blank `ski` TXT record fields have
  already been seen via mDNS and remain present in the mDNS cache
- WHEN a user adds an `eebus:network` Bridge Thing
- THEN an Inbox entry of type `eebus:peer` is created for each of them as children of that Bridge

---

_Change ID: `mdns-discovery-retry`. Domain: `discovery`. Modifies the requirement of the same
name introduced by change `eebus-network-discovery` (ADR-003); applied against that change's
requirements since `docs/specs/discovery/spec.md` does not exist yet (see proposal.md, Open
Questions)._
