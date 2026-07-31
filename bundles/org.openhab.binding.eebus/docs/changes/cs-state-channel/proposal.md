# Proposal: Controllable System State Channel

## Intent

`EEBusLimitControlStateMachine`'s current state (`INIT`/`UNLIMITED_CONTROLLED`/`LIMITED`/
`FAILSAFE`/`UNLIMITED_AUTONOMOUS`) is not published anywhere a user can see it directly today -
only inferable from other Channels, or via a metadata Item a user has to tag manually first.
The user wants it visible as a Channel, encoded as a number, with each number offering a
readable alias as an option.

## Scope

In scope:

- A new `state` Channel (`Number` item type), added to both the `lpc` and `lpp` Channel
  Groups, on the peer's `eebus:oh-cs-entity`/`eebus:oh-entity` Thing.
- A readable label per numeric value, via the Channel-Type's `<options>`.
- Publishing on every state machine transition, from the same place that already publishes
  the SPINE echo-back and the `LPC.state`/`LPP.state` metadata Item.

Out of scope:

- Removing or changing the existing `LPC.state`/`LPP.state` metadata Item - kept unchanged,
  additive only (same precedent as ADR-021 for `limit-active`/`limit-value`).
- A separate `String` Channel with the enum's `name()` - the `Number` Channel's `<options>`
  already give a readable label without needing a second Channel.

## Open Questions

None - resolved during `$Concept`/`$Architect`; see ADR-046 for the ordinal-as-contract
trade-off this proposal accepts.
