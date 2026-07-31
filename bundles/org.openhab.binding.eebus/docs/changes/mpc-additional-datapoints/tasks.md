# Tasks: Additional MPC Client-role data points (Scenarios 2-5)

## 1. Constants

- [x] 1.1 `EEBusBindingConstants`: 15 new `CHANNEL_MPC_*` channel-id constants and 15 new `CHANNEL_TYPE_UID_MPC_*` constants (`power-phase-a`/`-b`/`-c`, `energy-consumed`, `energy-produced`, `current-phase-a`/`-b`/`-c`, `voltage-phase-a`/`-b`/`-c`, `frequency`, `voltage-a-b`/`-b-c`/`-c-a`)

## 2. thing-types.xml

- [x] 2.1 15 new `<channel-type>` declarations under the existing MPC comment block: `Number:Power` (power-phase x3), `Number:Energy` (energy x2), `Number:ElectricCurrent` (current-phase x3), `Number:ElectricPotential` (voltage-phase x3, voltage phase-to-phase x3), `Number:Frequency` (frequency x1)
- [x] 2.2 The 3 phase-to-phase voltage `channel-type`s' `description` explicitly notes they are never populated by this binding (no resolution mechanism), so a Main UI user isn't left guessing why they stay `NULL`
- [x] 2.3 `mpc` `channel-group-type`'s `<channels>` extended from just `power` to all 15
- [x] 2.4 Re-verified well-formed via `xml.dom.minidom.parse` after all edits
- [x] 2.5 CRLF preserved

## 3. EEBusMpcClientUseCase

- [x] 3.1 New private record/small data structure mapping each of the 12 additional data points to its `ScopeType`, channel id, `ChannelTypeUID`, accepted item type, label, and `Unit`
- [x] 3.2 New method resolving that map against a `MeasurementDescriptionListDataType` result - scope match only, no measurement-type or sole-entry fallback (per proposal.md "Open Questions", confirmed by `$Architect`)
- [x] 3.3 `subscribe()` restructured: one description read resolves both the mandatory `power` id (existing `resolvePowerMeasurementId`, unchanged) and the map of additional ids found; the combined set drives one initial value read and one subscription, exactly as today - no additional SPINE round trips
- [x] 3.4 `applyMeasurement` restructured to iterate every entry in a `MeasurementListData` result/notification and dispatch each one whose id was resolved, instead of filtering for a single id and returning after the first match
- [x] 3.5 `power`'s dispatch continues to call `EEBusOhEntityHandler#applyMpcPower` unchanged (log wording, method identity, existing tests untouched); the 12 additional data points dispatch through the new generic handler method (task 4.1)

## 4. EEBusOhEntityHandler

- [x] 4.1 New generic `applyMpcMeasurement(String channelId, ChannelTypeUID channelTypeUid, String acceptedItemType, String label, double value, Unit<?> unit)` method - same `ensureChannel`/`updateCachedState` pattern as `applyMpcPower`, parameterized instead of hardcoded to `power`/`Number:Power`/`Units.WATT`
- [x] 4.2 `applyMpcPower(double watts)` kept with its exact existing signature and behavior (delegates to the new generic method internally) - no change visible to `EEBusMpcClientUseCase`'s existing `power` call site or to `EEBusOhEntityHandlerTest`'s existing tests

## 5. Tests

- [x] 5.1 Unit test: `applyMpcMeasurement` creates the Channel and updates its state (mirrors `whenApplyMpcPowerCalledThenChannelCreatedAndStateUpdated`), for a representative additional data point (`current-phase-a`)
- [x] 5.2 Unit test: repeated `applyMpcMeasurement` calls for the same channel id do not duplicate the Channel (mirrors `whenApplyMpcPowerCalledTwiceThenChannelIsNotDuplicated`)
- [x] 5.3 **Known gap, same precedent as `dynamic-client-role-channels/tasks.md` 6.3:** no unit test exercises `EEBusMpcClientUseCase`'s new multi-data-point resolution/dispatch end-to-end - still needs a `jeebus.spine` `Device`/`NodeManagement`/`UseCasePartner` test fixture that does not exist in this binding yet

## 6. Documentation

- [x] 6.1 New ADR (`docs/ADR/037-mpc-additional-datapoints.md`) - refines ADR-014, supersedes none
- [x] 6.2 `docs/changes/mpc-additional-datapoints/{proposal.md,specs/mpc-additional-datapoints/spec.md,tasks.md}` (this change)
- [x] 6.3 `README.md` Channels table: 15 new rows (or a condensed multi-row block), each noting whether it is dynamic-on-oh-entity/static-on-oh-mpc-entity, and the 3 stub rows explicitly marked as never populated
- [x] 6.4 `CONCEPT.md` 5.4.3: "implementiert" markers updated for the 12 newly implemented data points; new decided-subsection; 7 checklist entry

## 7. Self-QA

- [x] 7.1 Brace/paren balance checked on every touched `.java` file
- [x] 7.2 `thing-types.xml` re-parsed with `xml.dom.minidom` after every edit
- [x] 7.3 CRLF preserved on every touched `.java`/ADR/changes-doc/`thing-types.xml` file; `CONCEPT.md` matched its current actual line-ending convention (verified via `file`, not assumed from stale notes)
- [x] 7.4 Grep sweep confirms every new constant/channel id is referenced consistently across `thing-types.xml`, `EEBusBindingConstants`, `EEBusMpcClientUseCase`, `EEBusOhEntityHandler`, `README.md`
- [x] 7.5 Markdownlint clean on every touched `.md` file

## 8. Verification (user-owned, same pattern as every prior ADR)

- [ ] 8.1 `mvn clean install` - no Maven available in the editing sandbox
- [ ] 8.2 Live retest against a real/test MPC Server peer that reports more than just total power: confirm each resolvable data point's Channel appears and updates correctly, confirm the 3 stub Channels exist but stay `NULL`
- [ ] 8.3 Live retest: confirm the `Unit`s chosen for energy/current/voltage/frequency (unverified against a real device, see ADR "Negative") actually match what a real peer sends - correct if not
- [ ] 8.4 Live retest: confirm a peer offering only `power` (no other data points) still behaves identically to before this change (Scenario "A peer offering only power behaves exactly as before")

---
