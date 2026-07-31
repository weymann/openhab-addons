# Tasks: Controllable System State Channel

## 1. Thing Type / Channel Definition

- [x] 1.1 Add `channel-type id="state"` (`item-type` `Number`, `<options>` for all five
      states) to `thing-types.xml`.
- [x] 1.2 Reference it from both the `lpc` and `lpp` `channel-group-type` `<channels>` lists.
- [x] 1.3 Add `CHANNEL_STATE`/`CHANNEL_TYPE_UID_STATE` constants to `EEBusBindingConstants`.

## 2. Handler

- [x] 2.1 Add `EEBusOhEntityHandler#applyLimitControlState(String channelGroup,
      EEBusLimitControlState state)`, publishing `state.ordinal()` as a `DecimalType`.

## 3. Use Case Wiring

- [x] 3.1 Call `energyGuardOhEntityHandler.applyLimitControlState(...)` from
      `AbstractEEBusLimitControllableSystemUseCase#onStateChanged`, tolerating an unresolved
      peer the same way `publishLimitState` already does.

## 4. Documentation

- [x] 4.1 Write ADR-046 documenting the ordinal-as-contract decision.
- [x] 4.2 Document the ordinal contract directly on `EEBusLimitControlState`'s javadoc.
- [x] 4.3 Write this change's delta spec (`specs/state/spec.md`).

## 5. Verification (not yet done - needs the user's build/test environment)

- [ ] 5.1 Compile (`$Release`'s `clean install` step, or a targeted `mvn compile`).
- [ ] 5.2 Retest against the local simulation rig and/or the real Hager Energy S10: confirm
      `lpc#state`/`lpp#state` show the expected number and Main UI renders the readable label
      through a state transition (e.g. trigger a real limit write to see `LIMITED`/`2`).
