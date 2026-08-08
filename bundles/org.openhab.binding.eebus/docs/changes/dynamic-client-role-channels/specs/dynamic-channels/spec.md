# Delta for Dynamic Channels

## ADDED Requirements

### Requirement: Client-role Channels are gated by the parent service's configured use cases

The binding SHALL only create a dynamic Channel for a Client-role use case on an `eebus:oh-peer`
Thing if that use case is included in the parent `eebus:service` Bridge's
`supportedUseCasesClient` configuration parameter, regardless of what a peer's SPINE discovery
otherwise reports.

#### Scenario: Channel created for a use case that is both configured and detected

- GIVEN an `eebus:service` Bridge has `MPC` in `supportedUseCasesClient`
- AND an `eebus:oh-peer` Thing under that Bridge is paired with a peer that offers MPC as a
  Server actor
- WHEN SPINE reports that peer as an MPC use-case partner
- THEN the `eebus:oh-peer` Thing SHALL gain an `mpc` Channel Group containing an `mpc#power`
  Channel

#### Scenario: No channel created for a use case not configured on the Bridge

- GIVEN an `eebus:service` Bridge does NOT have `MPC` in `supportedUseCasesClient`
- AND an `eebus:oh-peer` Thing under that Bridge is paired with a peer that offers MPC
- WHEN the Bridge starts SPINE communication
- THEN no `mpc` Channel Group SHALL be created on that `eebus:oh-peer` Thing, because no
  `EEBusMpcClientUseCase` was registered to detect it in the first place

### Requirement: eebus:oh-peer exposes MPC total power via a dynamically created Channel

The binding SHALL create an `mpc` Channel Group with an `mpc#power` Channel
(`Number:Power`, read-only) on a paired `eebus:oh-peer` Thing once that peer's MPC total power
measurement ID has been resolved, and SHALL update that Channel's state on every subsequent
measurement notification.

#### Scenario: Channel appears after first successful measurement resolution

- GIVEN an `eebus:oh-peer` Thing is paired with a peer offering MPC
- AND that peer's `MeasurementDescriptionListData` has been read and a total-power measurement
  ID resolved
- WHEN the first `MeasurementListData` value for that ID is read or notified
- THEN the `eebus:oh-peer` Thing SHALL have an `mpc#power` Channel
- AND that Channel's state SHALL equal the received value, in Watts

#### Scenario: Channel state updates on subsequent notifications

- GIVEN an `eebus:oh-peer` Thing already has an `mpc#power` Channel with a state
- WHEN a new `MeasurementListData` notification arrives for the resolved measurement ID
- THEN the `mpc#power` Channel's state SHALL be updated to the new value

### Requirement: Dynamically created Channels are not removed on unpair or disconnect

Once created, a Client-role Channel on `eebus:oh-peer` SHALL remain part of the Thing -
retaining its last known state and any Item link - regardless of subsequent `unpair()`
invocations (see `decouple-oh-peer-config-from-pairing`) or loss of SPINE connectivity to the
peer.

#### Scenario: Channel remains after unpair()

- GIVEN an `eebus:oh-peer` Thing has an `mpc#power` Channel with a state
- WHEN its `unpair()` Thing Action is invoked
- THEN the `mpc#power` Channel SHALL still be present on the Thing
- AND its last known state SHALL be unchanged

#### Scenario: Channel retains last known state when the peer becomes unreachable

- GIVEN an `eebus:oh-peer` Thing has an `mpc#power` Channel with a state
- WHEN the peer stops responding (e.g. goes offline) and no further measurement notifications
  arrive
- THEN the `mpc#power` Channel SHALL still be present on the Thing
- AND its state SHALL remain at the last received value, not `UNDEF`/`NULL`

### Requirement: Detected use cases are recorded as a Thing property on eebus:oh-peer

Once a Client-role use case is detected for a paired peer, the binding SHALL record it as a
Thing property on the `eebus:oh-peer` Thing, keyed by the use case's lowercase name, with a
value of `server` or `client` describing the actor role the peer plays for that use case (see
`proposal.md` "Open Questions" for why this reading was chosen over the alternative).

#### Scenario: MPC detection is recorded as a Thing property

- GIVEN an `eebus:oh-peer` Thing is paired with a peer that has not yet been detected offering
  any use case
- WHEN SPINE reports that peer as an MPC use-case partner (playing the `CEM`/Server actor)
- THEN the Thing property `mpc` SHALL be set to `server` on that `eebus:oh-peer` Thing

---

_Change ID: `dynamic-client-role-channels`. Domain: `dynamic-channels` (new - no prior
`docs/specs/dynamic-channels/spec.md` baseline exists yet; this delta becomes that baseline once
archived). Depends on `decouple-oh-peer-config-from-pairing` (not yet archived) for
`EEBusOhPeerHandler.isPaired()`/`unpair()`._
