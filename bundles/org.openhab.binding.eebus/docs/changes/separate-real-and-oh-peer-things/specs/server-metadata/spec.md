# Delta for Server-Role Item Metadata

## ADDED Requirements

### Requirement: Server-role metadata value includes an oh-service-id prefix

The binding SHALL require the `eebus` Item metadata value for server-role use cases to be
prefixed with the offering `eebus:service` Thing's ID, separated by a colon, in the form
`<oh-service-id>:<UseCase>.<Datapoint>`.

#### Scenario: Metadata resolves to the correct service

- GIVEN two `eebus:service` Bridges with Thing IDs `ems1` and `ems2`, both configured to offer
  `MPC` as a server-role use case
- AND an Item tagged with metadata value `ems1:MPC.power`
- WHEN the binding resolves server-role metadata for the `ems1` Bridge's `MPC.power` datapoint
- THEN it SHALL find this Item
- AND the `ems2` Bridge's `MPC.power` datapoint resolution SHALL NOT find this Item

#### Scenario: Metadata value missing the oh-service-id prefix is ignored

- GIVEN an Item tagged with metadata value `MPC.power` (no `<oh-service-id>:` prefix)
- WHEN the binding resolves server-role metadata for any `eebus:service` Bridge's `MPC.power`
  datapoint
- THEN it SHALL NOT find this Item
- AND a warning SHALL be logged identifying the malformed metadata value and the Item name

### Requirement: oh-service-id is the eebus:service Thing's UID segment

The `<oh-service-id>` referenced in server-role metadata values SHALL be the Thing ID segment of
the offering `eebus:service` Thing's UID (i.e. the value after the second colon in
`eebus:service:<id>`), requiring no additional configuration parameter.

#### Scenario: oh-service-id matches the Thing ID

- GIVEN an `eebus:service` Bridge with UID `eebus:service:ems1`
- WHEN determining which metadata values this Bridge should resolve
- THEN the applicable `<oh-service-id>` prefix SHALL be `ems1`

---

_Change ID: `separate-real-and-oh-peer-things`. Domain: `server-metadata`. No prior
`docs/specs/server-metadata/spec.md` exists yet — on archive, these requirements become that
file's initial content._
