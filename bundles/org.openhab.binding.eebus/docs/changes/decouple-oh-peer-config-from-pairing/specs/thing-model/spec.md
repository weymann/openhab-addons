# Delta for Thing Model

## ADDED Requirements

### Requirement: eebus:oh-peer ski parameter offers known SKIs as selectable options

The `ski` configuration parameter of `eebus:oh-peer` SHALL offer a list of selectable SKI
options, drawn from known `eebus:peer` Things and from other local `eebus:service` Bridges' own
SKI.

#### Scenario: Options include a discovered real device

- GIVEN an `eebus:peer` Thing exists with SKI `X` and a human-readable name
- WHEN a user opens the `ski` parameter's option list while configuring a new `eebus:oh-peer`
  Thing
- THEN the list SHALL contain an option representing `X`, labeled with that device's name

#### Scenario: Options include another local service's own identity

- GIVEN a second `eebus:service` Bridge exists with its own SKI `Y`
- WHEN a user opens the `ski` parameter's option list while configuring a new `eebus:oh-peer`
  Thing under a different `eebus:service` Bridge
- THEN the list SHALL contain an option representing `Y`, labeled with that Bridge's name

#### Scenario: Options exclude a SKI already paired under the target service

- GIVEN an `eebus:service` Bridge already has a child `eebus:oh-peer` Thing with SKI `X`
- WHEN a user opens the `ski` parameter's option list while configuring another new
  `eebus:oh-peer` Thing under the same Bridge
- THEN the list SHALL NOT contain an option for `X`

#### Scenario: Options include a SKI already paired under a different service

- GIVEN an `eebus:service` Bridge A already has a child `eebus:oh-peer` Thing with SKI `X`
- AND a second `eebus:service` Bridge B exists
- WHEN a user opens the `ski` parameter's option list while configuring a new `eebus:oh-peer`
  Thing under Bridge B
- THEN the list SHALL contain an option for `X`

#### Scenario: Manually entered SKI remains accepted

- GIVEN a user configures a new `eebus:oh-peer` Thing
- WHEN the user enters a SKI value that is not present in the option list
- THEN the configuration SHALL be accepted

### Requirement: eebus:oh-peer provides a pair() Thing Action

The binding SHALL provide a parameterless Thing Action, `pair()`, on `eebus:oh-peer` that adds
the Thing's configured `ski` to its parent `eebus:service` Bridge's trusted-SKI set.

#### Scenario: Invoking pair() establishes trust

- GIVEN an `eebus:oh-peer` Thing with `ski` value `X` exists and is not yet paired
- WHEN its `pair()` Thing Action is invoked
- THEN `X` SHALL be included in the parent Bridge's trusted-SKI set

#### Scenario: Invoking pair() on an already-paired Thing is a no-op

- GIVEN an `eebus:oh-peer` Thing is already paired
- WHEN its `pair()` Thing Action is invoked again
- THEN the Thing SHALL remain paired
- AND no error SHALL be raised

### Requirement: eebus:oh-peer provides an unpair() Thing Action

The binding SHALL provide a parameterless Thing Action, `unpair()`, on `eebus:oh-peer` that
removes the Thing's configured `ski` from its parent `eebus:service` Bridge's trusted-SKI set,
without removing the Thing.

#### Scenario: Invoking unpair() revokes trust

- GIVEN an `eebus:oh-peer` Thing with `ski` value `X` is paired
- WHEN its `unpair()` Thing Action is invoked
- THEN `X` SHALL NOT be included in the parent Bridge's trusted-SKI set
- AND the `eebus:oh-peer` Thing SHALL continue to exist with its `ski` configuration unchanged

#### Scenario: Invoking unpair() on an already-unpaired Thing is a no-op

- GIVEN an `eebus:oh-peer` Thing is not currently paired
- WHEN its `unpair()` Thing Action is invoked
- THEN no error SHALL be raised

### Requirement: Pairing state persists across openHAB restarts

The binding SHALL persist an `eebus:oh-peer` Thing's pairing state (established via `pair()`,
revoked via `unpair()`) as a Thing property, surviving an openHAB restart without requiring the
Action to be invoked again.

#### Scenario: Paired state survives a restart

- GIVEN an `eebus:oh-peer` Thing has been paired via `pair()`
- WHEN openHAB restarts and the parent `eebus:service` Bridge initializes
- THEN the Thing's `ski` SHALL be included in the Bridge's trusted-SKI set without any Action
  being invoked again

## MODIFIED Requirements

### Requirement: Creating an eebus:oh-peer Thing does not perform pairing

(Previously, in `separate-real-and-oh-peer-things`: creating and initializing an `eebus:oh-peer`
Thing added its `ski` to the parent Bridge's trusted-SKI set immediately.)

The binding SHALL NOT add a newly initialized `eebus:oh-peer` Thing's `ski` to its parent
`eebus:service` Bridge's trusted-SKI set until its `pair()` Thing Action is invoked.

#### Scenario: eebus:oh-peer added to a running service is not yet trusted

- GIVEN an `eebus:service` Bridge is `ONLINE`
- WHEN a new `eebus:oh-peer` Thing with `ski` value `X` is added as its child and initializes
- THEN `X` SHALL NOT be included in the Bridge's trusted-SKI set until `pair()` is invoked on
  that Thing

---

_Change ID: `decouple-oh-peer-config-from-pairing`. Domain: `thing-model`. Depends on
`separate-real-and-oh-peer-things` (not yet archived) for the `eebus:oh-peer` Thing type itself
and for the requirement this change modifies. If that change is archived first, its ADDED/
MODIFIED requirements form the baseline this delta applies to; if archived together, both sets
of requirements apply cumulatively to `docs/specs/thing-model/spec.md`._
