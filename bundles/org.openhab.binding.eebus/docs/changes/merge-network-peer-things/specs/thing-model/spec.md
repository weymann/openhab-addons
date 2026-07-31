# Delta for Thing Model

## MODIFIED Requirements

### Requirement: eebus:peer is a bridgeless top-level Thing type

(Previously, in `separate-real-and-oh-peer-things`: "The binding SHALL allow `eebus:peer`
Things only as children of an `eebus:network` Bridge." That requirement itself modified an even
earlier one allowing `eebus:service` or `eebus:network` as parent.)

The binding SHALL provide the real-device Thing type as `eebus:eebus-peer`, with no supported
parent Bridge type. The binding SHALL NOT provide an `eebus:network` Bridge Thing type.

#### Scenario: Adding eebus:eebus-peer requires no Bridge

- GIVEN no `eebus:service` Bridge and no other eebus Thing exists
- WHEN a user adds a new Thing of type `eebus:eebus-peer`
- THEN the Main UI SHALL NOT prompt for a parent Bridge
- AND the Thing SHALL accept a `ski` configuration parameter labeled "SKI"

#### Scenario: eebus:network is no longer offered as a Thing type

- GIVEN a user opens the "Add Thing" list for the eebus binding
- WHEN the list of available Thing types is displayed
- THEN it SHALL NOT contain `eebus:network`

### Requirement: eebus:eebus-peer exposes no channels

(Previously (`separate-real-and-oh-peer-things`): "The binding SHALL NOT define any Channels on
the `eebus:peer` Thing type." Restated under the new identifier; behavior unchanged.)

The binding SHALL NOT define any Channels on the `eebus:eebus-peer` Thing type.

#### Scenario: eebus:eebus-peer Thing has no channels

- GIVEN an `eebus:eebus-peer` Thing exists
- WHEN the Thing's channel list is inspected
- THEN it SHALL be empty

### Requirement: eebus:eebus-peer goes ONLINE without a Bridge

(Previously: status was derived from the parent `eebus:network` Bridge's own status via
`bridgeStatusChanged`, which was itself unconditionally `ONLINE` - i.e. always effectively
`ONLINE` once configured, just indirected through a Bridge that added no information.)

The binding SHALL set an `eebus:eebus-peer` Thing to status `ONLINE` once its `ski` parameter is
non-blank, and to `OFFLINE` (`CONFIGURATION_ERROR`) if `ski` is blank.

#### Scenario: Configured peer goes online

- GIVEN a user adds an `eebus:eebus-peer` Thing with a non-blank `ski`
- WHEN the Thing initializes
- THEN its status SHALL be `ONLINE`

#### Scenario: Peer with blank SKI stays offline

- GIVEN a user adds an `eebus:eebus-peer` Thing with a blank `ski`
- WHEN the Thing initializes
- THEN its status SHALL be `OFFLINE` with detail `CONFIGURATION_ERROR`

---

_Change ID: `merge-network-peer-things`. Domain: `thing-model`. Further modifies the requirement
introduced by `separate-real-and-oh-peer-things` (not yet archived); applied against that
change's pending delta since `docs/specs/thing-model/spec.md` does not exist yet, the same
pattern `decouple-oh-peer-config-from-pairing` used._
