# Delta for Discovery

## ADDED Requirements

### Requirement: Background scanning starts without any configured Thing

The binding SHALL scan for `_ship._tcp.local.` mDNS announcements as soon as the binding is
installed, without requiring any `eebus:network` or `eebus:service` Thing to exist or be
`ONLINE`.

#### Scenario: Binding installed, no Thing configured yet

- GIVEN the eebus binding is installed
- AND no eebus Thing of any type exists
- WHEN a real EEBUS/SHIP device advertises itself via `_ship._tcp.local.` mDNS
- THEN the binding's mDNS scan detects the announcement
- AND no error or "missing bridge" warning is logged

### Requirement: eebus:network Bridge requires no mandatory configuration

The binding SHALL allow an `eebus:network` Thing to be added with no mandatory configuration
parameters.

#### Scenario: Adding eebus:network with no parameters

- GIVEN a user adds a new Thing of type `eebus:network`
- WHEN no configuration parameters are provided
- THEN the Thing SHALL initialize successfully and go `ONLINE`

### Requirement: Discovered devices require an existing eebus:network Bridge

The binding SHALL only create an Inbox entry for a discovered real EEBUS device once at least
one `eebus:network` Bridge Thing exists.

#### Scenario: Device discovered before eebus:network exists

- GIVEN no `eebus:network` Bridge Thing exists
- WHEN a real EEBUS/SHIP device is discovered via mDNS
- THEN no Inbox entry is created for that device

#### Scenario: Device discovered after eebus:network exists

- GIVEN an `eebus:network` Bridge Thing exists and is `ONLINE`
- WHEN a real EEBUS/SHIP device is discovered via mDNS with a non-blank `ski` TXT record field
- THEN an Inbox entry of type `eebus:peer` is created as a child of that `eebus:network` Bridge
- AND the entry's label is the brand and model from the TXT record if either is present,
  otherwise the mDNS service instance name
- AND the entry's `ski` property is set from the TXT record

### Requirement: Devices without a usable SKI are ignored

The binding SHALL NOT create an Inbox entry for a device whose TXT record has no `ski` field or
a blank `ski` field.

#### Scenario: Missing ski TXT field

- GIVEN an `eebus:network` Bridge Thing exists
- WHEN a `_ship._tcp.local.` service is resolved with a blank or missing `ski` TXT record field
- THEN no Inbox entry is created for that service

### Requirement: Already-paired devices are excluded

The binding SHALL exclude a device from new Inbox entries if its SKI already belongs to an
existing `eebus:peer` Thing.

#### Scenario: Skip already-paired SKI

- GIVEN an `eebus:peer` Thing already exists with SKI `X`
- WHEN a device advertising SKI `X` is seen via mDNS
- THEN no new Inbox entry is created for SKI `X`

### Requirement: Discovery does not require a local SHIP identity

The binding SHALL detect and report real EEBUS devices without requiring an `eebus:service`
Bridge (local SHIP identity/certificate) to exist or be `ONLINE`.

#### Scenario: No eebus:service configured

- GIVEN no `eebus:service` Bridge exists
- AND an `eebus:network` Bridge Thing exists
- WHEN a real EEBUS/SHIP device is discovered via mDNS
- THEN it appears in the Inbox as a child of the `eebus:network` Bridge

---

_Change ID: `eebus-network-discovery`. Domain: `discovery`. No prior `docs/specs/discovery/spec.md`
exists yet — on archive, these requirements become that file's initial content._
