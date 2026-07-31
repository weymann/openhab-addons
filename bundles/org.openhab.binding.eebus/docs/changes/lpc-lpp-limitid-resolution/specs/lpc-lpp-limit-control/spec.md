# Delta for LPC/LPP Limit Control

## ADDED Requirements

### Requirement: Server-role LPC and LPP limits use distinct limitId values

The binding SHALL assign a distinct, stable `limitId` to the Server-role LPC limit and the
Server-role LPP limit when both use cases are active on the same local Entity, so that neither
use case's `LoadControlLimitDescriptionData`/`LoadControlLimitData` entry overwrites the
other's.

#### Scenario: Both LPC and LPP Server use cases active on the same Bridge

- GIVEN an `eebus:service` Bridge configured with `supportedUseCasesServer` including both LPC
  and LPP
- WHEN the Bridge starts and registers its local Controllable System use cases
- THEN the peer-visible `LoadControl` feature exposes two separate
  `LoadControlLimitDescriptionData` entries, one with `limitDirection=consume` and one with
  `limitDirection=produce`, each with its own `limitId`
- AND writing a limit to one direction's `limitId` does not change the other direction's
  `isLimitActive`/`value`

### Requirement: Client-role LPC/LPP resolve limitId by limitDirection instead of assuming it

The binding SHALL resolve which `limitId` corresponds to the LPC (consume) vs. LPP (produce)
direction by reading the peer's `loadControlLimitDescriptionListData` and matching on
`limitDirection`, rather than assuming a fixed `limitId`.

#### Scenario: Peer exposes distinct limitId values per direction

- GIVEN a paired `eebus:oh-entity` exposing a `LoadControl` feature with two limit description
  entries, one `limitDirection=consume` (`limitId=X`) and one `limitDirection=produce`
  (`limitId=Y`), with `X != Y`
- WHEN the LPC Client use case reads/subscribes to that peer's `LoadControl` feature
- THEN it reports `isLimitActive`/`value` from the description entry whose `limitDirection` is
  `consume` (`limitId=X`), not any other entry
- AND the LPP Client use case independently reports `isLimitActive`/`value` from the entry whose
  `limitDirection` is `produce` (`limitId=Y`)

#### Scenario: Peer's description read fails or contains no matching direction

- GIVEN the `loadControlLimitDescriptionListData` read fails, or contains no entry with the
  expected `limitDirection`
- WHEN the Client use case processes a subsequent `loadControlLimitListData` notification
- THEN it does not report a limit status for that peer/use case, rather than falling back to
  guessing an ID

### Requirement: LPC and LPP Client-role write paths remain independent per direction

The binding SHALL keep the LPC and LPP Client-role write paths (`limitActive`/`limitValue`
tagged Items) independent, such that writing a limit for one direction never changes the
read-back status Channel of the other direction on the same paired peer.

#### Scenario: User toggles only the LPC write-path Item

- GIVEN an `eebus:oh-entity` with separate Items tagged for the LPC and LPP write-path
  `limitActive` data point
- WHEN the LPC-tagged Item is switched ON
- THEN the peer's LPC read-back status (`lpc#` channel group) becomes active
- AND the peer's LPP read-back status (`lpp#` channel group) remains unchanged

### Requirement: Server-role LPC and LPP write handling stays isolated per direction

The binding SHALL ensure that, on the Server (Controllable System) role, a write to one
direction's `LoadControlLimitData` entry (identified by `limitId`) affects only that direction's
own state machine and Item status, and that publishing a state change back to the peer updates
only that direction's own peer-visible list entry - never the other direction's - even though
both directions share one local `LoadControl` feature and one `LimitListDataFunction`.

#### Scenario: Energy Guard writes only the LPC limit

- GIVEN an `eebus:service` Bridge running both LPC and LPP Server use cases on the same local
  Entity, both currently `UNLIMITED_CONTROLLED`
- WHEN the paired Energy Guard writes an active limit to the LPC `limitId` only
- THEN only the LPC use case's `EEBusLimitControlStateMachine` transitions to `LIMITED`
- AND the LPP use case's state machine and Item status remain unchanged

#### Scenario: A Server-role use case publishes a state change

- GIVEN LPC's and LPP's `LoadControlLimitData` entries were both registered via `addData()` on
  the shared `LimitListDataFunction`, at whatever list index each was assigned
- WHEN either use case's own state machine transitions and publishes the new state back to the
  peer
- THEN the update is written to that use case's own previously-assigned list index
- AND the other direction's list entry (index, `limitId`, `isLimitActive`, `value`) is left
  completely untouched by that publish

---

_Stored at
`org.openhab.binding.eebus/docs/changes/lpc-lpp-limitid-resolution/specs/lpc-lpp-limit-control/spec.md`.
No prior `org.openhab.binding.eebus/docs/specs/lpc-lpp-limit-control/spec.md` exists yet - all
requirements above are `ADDED`; on archive this becomes that domain's first source-of-truth
spec._
