# Delta for Thing Model

## ADDED Requirements

### Requirement: eebus:oh-peer Bridge child Thing type

The binding SHALL provide an `eebus:oh-peer` Thing type whose only supported parent Bridge is
`eebus:service`.

#### Scenario: Adding eebus:oh-peer under eebus:service

- GIVEN an `eebus:service` Bridge Thing exists
- WHEN a user adds a new Thing of type `eebus:oh-peer` as its child
- THEN the Thing SHALL accept a `ski` configuration parameter labeled "Trusted SKI"

#### Scenario: eebus:oh-peer cannot be added under eebus:network

- GIVEN an `eebus:network` Bridge Thing exists
- WHEN a user attempts to add a new Thing of type `eebus:oh-peer` as its child
- THEN the Main UI SHALL NOT offer `eebus:oh-peer` as a valid Thing type for that Bridge

### Requirement: Creating an eebus:oh-peer Thing performs pairing

The binding SHALL add the `ski` of a newly initialized `eebus:oh-peer` Thing to its parent
`eebus:service` Bridge's trusted-SKI set.

#### Scenario: eebus:oh-peer added to a running service

- GIVEN an `eebus:service` Bridge is `ONLINE`
- WHEN a new `eebus:oh-peer` Thing with `ski` value `X` is added as its child and initializes
- THEN `X` SHALL be included in the Bridge's trusted-SKI set

#### Scenario: eebus:oh-peer removed from a running service

- GIVEN an `eebus:service` Bridge is `ONLINE` with a child `eebus:oh-peer` Thing whose `ski` is
  `X`
- WHEN that `eebus:oh-peer` Thing is removed
- THEN `X` SHALL no longer be included in the Bridge's trusted-SKI set

### Requirement: eebus:oh-peer exposes dynamically created channels

The binding SHALL expose dynamically created Channels on an `eebus:oh-peer` Thing for each
client-role use case detected on the paired remote device, once use-case detection is
implemented (CONCEPT.md §5.4).

#### Scenario: Placeholder channel state before use-case detection is implemented

- GIVEN an `eebus:oh-peer` Thing is `ONLINE`
- AND client-role use-case detection is not yet implemented
- WHEN the Thing's channel list is inspected
- THEN it SHALL contain no channels (unchanged placeholder behavior, not a regression
  introduced by this change)

## MODIFIED Requirements

### Requirement: eebus:peer supports only eebus:network as parent

(Previously: `eebus:peer` accepted either `eebus:service` or `eebus:network` as its parent
Bridge.)

The binding SHALL allow `eebus:peer` Things only as children of an `eebus:network` Bridge.

#### Scenario: eebus:peer cannot be added under eebus:service

- GIVEN an `eebus:service` Bridge Thing exists
- WHEN a user attempts to add a new Thing of type `eebus:peer` as its child
- THEN the Main UI SHALL NOT offer `eebus:peer` as a valid Thing type for that Bridge

### Requirement: eebus:peer exposes no channels

(Previously: unspecified — the single `peer` type's channel behavior depended on which Bridge
it was configured under.)

The binding SHALL NOT define any Channels on the `eebus:peer` Thing type.

#### Scenario: eebus:peer Thing has no channels

- GIVEN an `eebus:peer` Thing exists under an `eebus:network` Bridge
- WHEN the Thing's channel list is inspected
- THEN it SHALL be empty

### Requirement: eebus:peer SKI label reflects discovery, not trust

(Previously: the single `ski` parameter was labeled "SKI" regardless of parent Bridge, ambiguous
about its meaning under `eebus:service`.)

The `eebus:peer` Thing type's `ski` configuration parameter SHALL be labeled "SKI".

#### Scenario: eebus:peer SKI label

- GIVEN a user opens the configuration form of an `eebus:peer` Thing
- WHEN the `ski` parameter is displayed
- THEN its label SHALL read "SKI"

### Requirement: eebus:service Bridge UI label

(Previously: labeled "EEBus Service".)

The `eebus:service` Bridge type SHALL be labeled "EEBus OH Service" in the Main UI.

#### Scenario: eebus:service label in Add Thing list

- GIVEN a user opens the "Add Thing" list for the eebus binding
- WHEN the `eebus:service` Bridge type entry is displayed
- THEN its label SHALL read "EEBus OH Service"

---

_Change ID: `separate-real-and-oh-peer-things`. Domain: `thing-model`. No prior
`docs/specs/thing-model/spec.md` exists yet — on archive, ADDED and MODIFIED requirements become
that file's initial content._
