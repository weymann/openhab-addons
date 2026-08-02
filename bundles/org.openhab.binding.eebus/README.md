# EEBus Binding

_Give some details about what this binding is meant for - a protocol, system, specific device._

_If possible, provide some resources like pictures (only PNG is supported currently), a video, etc. to give an impression of what can be done with this binding._
_You can place such resources into a `doc` folder next to this README.md._

_Put each sentence in a separate line to improve readability of diffs._

## Supported Things

_Please describe the different supported things / devices including their ThingTypeUID within this section._
_Which different types are supported, which models were tested etc.?_
_Note that it is planned to generate some part of this based on the XML files within ```src/main/resources/OH-INF/thing``` of your binding._

- `service` (Bridge): one local EEBus SHIP/SPINE service instance - own certificate, own mDNS
  presence. Required if openHAB itself should offer or consume EEBus use cases.
- `network` (Bridge): lightweight anchor with no mandatory configuration. Add this first - it
  has no local identity of its own, but is the parent Thing that discovered real EEBus devices
  attach to.
- `peer`: one paired remote EEBus device, identified by its SKI. Creating this Thing is the
  pairing action.

## Discovery

Real EEBus devices on the local network are discovered automatically via mDNS
(`_ship._tcp.local.`, SHIP 7.3.2), as soon as the binding is installed - no local identity needs
to be configured first.

Discovered devices only appear in the Inbox once an `eebus:network` Thing has been added
manually (it has no real-world counterpart to discover itself, so it is not auto-suggested).
Devices whose SKI already belongs to an existing `peer` Thing are excluded, since they are
already paired.

## Binding Configuration

_If your binding requires or supports general configuration settings, please create a folder ```cfg``` and place the configuration file ```<bindingId>.cfg``` inside it._
_In this section, you should link to this file and provide some information about the options._
_The file could e.g. look like:_

```properties
# Configuration for the EEBus Binding
#
# Default secret key for the pairing of the EEBus Thing.
# It has to be between 10-40 (alphanumeric) characters.
# This may be changed by the user for security reasons.
secret=openHABSecret
```

_Note that it is planned to generate some part of this based on the information that is available within ```src/main/resources/OH-INF/binding``` of your binding._

_If your binding does not offer any generic configurations, you can remove this section completely._

## Thing Configuration

_Describe what is needed to manually configure a thing, either through the UI or via a thing-file._
_This should be mainly about its mandatory and optional configuration parameters._

_Note that it is planned to generate some part of this based on the XML files within ```src/main/resources/OH-INF/thing``` of your binding._

### `sample` Thing Configuration

| Name            | Type    | Description                           | Default | Required | Advanced |
|-----------------|---------|---------------------------------------|---------|----------|----------|
| hostname        | text    | Hostname or IP address of the device  | N/A     | yes      | no       |
| password        | text    | Password to access the device         | N/A     | yes      | no       |
| refreshInterval | integer | Interval the device is polled in sec. | 600     | no       | yes      |

## Channels

_Here you should provide information about available channel types, what their meaning is and how they can be used._

_Note that it is planned to generate some part of this based on the XML files within ```src/main/resources/OH-INF/thing``` of your binding._

| Channel | Type   | Read/Write | Description                 |
|---------|--------|------------|-----------------------------|
| control | Switch | RW         | This is the control channel |

## Full Example

_Provide a full usage example based on textual configuration files._
_*.things, *.items examples are mandatory as textual configuration is well used by many users._
_*.sitemap examples are optional._

### Thing Configuration

```java
Example thing configuration goes here.
```

### Item Configuration

```java
Example item configuration goes here.
```

### Sitemap Configuration

```perl
Optional Sitemap configuration goes here.
Remove this section, if not needed.
```

## Any custom content here!

_Feel free to add additional sections for whatever you think should also be mentioned about your binding!_
