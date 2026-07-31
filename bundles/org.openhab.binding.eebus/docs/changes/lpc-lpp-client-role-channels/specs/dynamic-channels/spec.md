# Delta for Dynamic Channels (LPC/LPP)

## ADDED Requirements

### Requirement: LPC/LPP Client-role Channels are gated by the parent service's configured use cases

The binding SHALL only create a dynamic Channel for LPC or LPP on an `eebus:oh-entity` Thing if
that use case is included in the parent `eebus:service` Bridge's `supportedUseCasesClient`
configuration parameter, regardless of what a peer's SPINE discovery otherwise reports - same
gating rule as MPC (`dynamic-client-role-channels`), applied per use case.

#### Scenario: LPC Channel Group created for a use case that is both configured and detected

- GIVEN an `eebus:service` Bridge has `LPC` in `supportedUseCasesClient`
- AND an `eebus:oh-entity` Thing under that Bridge is paired with a peer that offers LPC as a
  Server actor ("ControllableSystem")
- WHEN SPINE reports that peer as an LPC use-case partner
- THEN the `eebus:oh-entity` Thing SHALL gain an `lpc` Channel Group containing `limit-active` and
  `limit-value` Channels

#### Scenario: No LPC Channel Group created for a use case not configured on the Bridge

- GIVEN an `eebus:service` Bridge does NOT have `LPC` in `supportedUseCasesClient`
- AND an `eebus:oh-entity` Thing under that Bridge is paired with a peer that offers LPC
- WHEN the Bridge starts SPINE communication
- THEN no `lpc` Channel Group SHALL be created on that `eebus:oh-entity` Thing

#### Scenario: LPP Channel Group created independently of LPC

- GIVEN an `eebus:service` Bridge has `LPP` but NOT `LPC` in `supportedUseCasesClient`
- AND an `eebus:oh-entity` Thing under that Bridge is paired with a peer offering both LPC and LPP
- WHEN SPINE reports that peer as an LPP use-case partner
- THEN the `eebus:oh-entity` Thing SHALL gain an `lpp` Channel Group
- AND SHALL NOT gain an `lpc` Channel Group

### Requirement: eebus:oh-entity exposes the peer's current LPC limit status via read-only Channels

The binding SHALL create an `lpc` Channel Group with read-only Channels `limit-active`
(`Switch`) and `limit-value` (`Number:Power`) on a paired `eebus:oh-entity` Thing once that peer's
LPC Scenario 1 status has been resolved (`isLimitActive`/`value` per CONCEPT.md §5.4.3 Table 23),
and SHALL update these Channels' state on every subsequent notification. Setting a new limit
(writing to these Channels), a `limit-duration` Channel (`timePeriod.endTime`), and Scenario
2/4 (Failsafe values/Constraints) are explicitly out of scope for this change - see
`proposal.md` "Scope"/"Open Questions" and `tasks.md`.

#### Scenario: LPC status Channels appear after first successful resolution

- GIVEN an `eebus:oh-entity` Thing is paired with a peer offering LPC
- AND that peer's LPC Scenario 1 status (`isLimitActive`/`value`) has been read or notified for
  the first time
- THEN the `eebus:oh-entity` Thing SHALL have `lpc#limit-active` and `lpc#limit-value` Channels
- AND their states SHALL equal the received values

#### Scenario: LPC status Channels update on subsequent notifications

- GIVEN an `eebus:oh-entity` Thing already has `lpc#limit-active`/`limit-value` Channels with a
  state
- WHEN a new LPC Scenario 1 status notification arrives
- THEN those Channels' states SHALL be updated to the new values

### Requirement: eebus:oh-entity exposes the mirrored LPP Channel Group

The binding SHALL provide an `lpp` Channel Group structurally identical to `lpc`
(`limit-active`/`limit-value`), populated from the LPP use case's mirrored SPINE fields
(CONCEPT.md §5.4.2/§5.4.3: "LPP ist strukturell identisch").

#### Scenario: LPP status Channels appear after first successful resolution

- GIVEN an `eebus:oh-entity` Thing is paired with a peer offering LPP
- AND that peer's LPP Scenario 1 status has been read or notified for the first time
- THEN the `eebus:oh-entity` Thing SHALL have `lpp#limit-active` and `lpp#limit-value` Channels
- AND their states SHALL equal the received values

### Requirement: LPC/LPP Channels follow the same persistence rules as MPC

Once created, an LPC/LPP Channel on `eebus:oh-entity` SHALL remain part of the Thing - retaining
its last known state and any Item link - regardless of subsequent `unpair()` invocations or loss
of SPINE connectivity to the peer, identical to the rule `dynamic-client-role-channels`
established for `mpc#power`.

#### Scenario: LPC Channels remain after unpair()

- GIVEN an `eebus:oh-entity` Thing has `lpc#limit-value` with a state
- WHEN its `unpair()` Thing Action is invoked
- THEN `lpc#limit-value` SHALL still be present on the Thing with its last known state unchanged

### Requirement: Detected LPC/LPP use cases are recorded as Thing properties

Once LPC or LPP is detected for a paired peer, the binding SHALL record it as a Thing property
on the `eebus:oh-entity` Thing (keys `lpc`/`lpp`), reusing
`EEBusOhPeerHandler#recordDetectedUseCase` unchanged from `dynamic-client-role-channels`.

#### Scenario: LPC detection is recorded as a Thing property

- GIVEN an `eebus:oh-entity` Thing is paired with a peer that has not yet been detected offering
  any use case
- WHEN SPINE reports that peer as an LPC use-case partner (playing the `ControllableSystem`/
  Server actor)
- THEN the Thing property `lpc` SHALL be set to `server` on that `eebus:oh-entity` Thing

---

_Change ID: `lpc-lpp-client-role-channels`. Domain: `dynamic-channels` - delta on top of the
baseline `dynamic-client-role-channels` is establishing (not yet archived). Depends on that
change's `EEBusOhPeerHandler#ensureChannel`/`#recordDetectedUseCase` and
`EEBusHandler#ohPeerHandlerForSki`/`#ohPeerHandlerForCommunicationAddress`. Scope narrowed twice
during `$Dev` (2026-08-12) relative to the original delta - see `tasks.md` for the full
reasoning; `limit-duration` and Scenario 2/4 Channels are tracked there as deferred follow-up,
not represented in this spec's Requirements._
