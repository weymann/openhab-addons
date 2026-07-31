# Delta for MGCP Client-role Use Case

## ADDED Requirements

### Requirement: MGCP Client-role use-case detection

`eebus:oh-device` SHALL detect a paired, trusted peer offering the MGCP use case
(`monitoringOfGridConnectionPoint`) as actor `GridConnectionPoint`, for any `eebus:oh-entity`
child Thing with `MGCP` checked in `supportedUseCasesClient`.

#### Scenario: MGCP detected for a trusted peer

- GIVEN an `eebus:oh-entity` Thing with `MGCP` checked in `supportedUseCasesClient`, paired and
  trusted with a peer that supports MGCP Scenario 2 as actor `GridConnectionPoint`
- WHEN the peer's use-case partner information is received via SPINE
- THEN the binding records MGCP as detected for that Entity
- AND subscribes to the peer's `Measurement` feature for Total Active Power

#### Scenario: MGCP not configured is a no-op

- GIVEN an `eebus:oh-entity` Thing without `MGCP` checked in `supportedUseCasesClient`
- WHEN the Bridge derives its local Client-role use cases
- THEN no `EEBusMgcpClientUseCase` instance is created for that Bridge

### Requirement: Total Active Power Channel

`eebus:oh-entity` SHALL expose the paired peer's MGCP Total Active Power (Scenario 2,
[MGCP-021]) as a dynamically created read-only Channel, once resolved.

#### Scenario: Channel appears after first successful measurement resolution

- GIVEN a trusted `eebus:oh-entity` Thing with MGCP detected for its peer
- WHEN the peer's `MeasurementDescriptionListData` contains an entry whose `scopeType` is
  `acPowerTotal` and a subsequent `MeasurementListData` reports a value for that measurement id
- THEN the `mgcp#total-active-power` Channel is created on the Thing (if not already present)
- AND its state is updated to the reported value, in Watts

#### Scenario: Repeated measurements update the same Channel, not duplicate it

- GIVEN the `mgcp#total-active-power` Channel already exists on a Thing
- WHEN another `MeasurementListData` notification reports a new value for the same resolved
  measurement id
- THEN the existing Channel's state is updated
- AND no second `mgcp#total-active-power` Channel is created

#### Scenario: No resolvable measurement id means no Channel

- GIVEN a trusted `eebus:oh-entity` Thing with MGCP detected for its peer
- WHEN the peer's `MeasurementDescriptionListData` has no entry whose `scopeType` is
  `acPowerTotal`, no entry whose `measurementType` is `power`, and more than one description
  entry exists (so no sole-entry fallback applies either)
- THEN no `mgcp#total-active-power` Channel is created for that peer

#### Scenario: Value sign follows the load convention

- GIVEN a resolved MGCP Total Active Power measurement
- WHEN the peer's local premises are net consuming from the public grid
- THEN the reported value is positive
- AND WHEN the peer's local premises are net feeding into the public grid, the reported value is
  negative ([MGCP-001], the "load convention" - the same sign convention MPC's own `power`
  already uses ([MPC-001]/§2.5.1 of that Use Case's TS), so no new interpretation rule for
  consumers of both Channels
