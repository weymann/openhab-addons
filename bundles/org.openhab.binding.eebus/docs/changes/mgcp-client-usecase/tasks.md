# Tasks: MGCP Client-role Use Case (Total Active Power)

## 1. Constants

- [x] 1.1 `EEBusBindingConstants`: `USE_CASE_KEY_MGCP` (if not already generic), `CHANNEL_GROUP_MGCP`, `CHANNEL_MGCP_TOTAL_ACTIVE_POWER`, `CHANNEL_TYPE_UID_MGCP_TOTAL_ACTIVE_POWER`

## 2. thing-types.xml

- [x] 2.1 New `channel-group-type id="mgcp"` (mirrors `mpc`'s shape) with one `channel id="total-active-power" typeId="mgcp-total-active-power"`
- [x] 2.2 New `channel-type id="mgcp-total-active-power"` (`Number:Power`, read-only) - description explicitly states the load-convention sign meaning (positive = consumption, negative = feed-in), per spec.md's sign Scenario
- [x] 2.3 **Corrected against the actual XML while implementing:** `oh-entity` has no `<channel-groups>` element at all - `mpc`/`lpc`/`lpp` are never statically referenced there either (only `oh-mpc-entity`/`oh-cs-entity`, the dedicated static Thing types, reference their group). The new `mgcp` `channel-group-type` follows the same "dynamic, created at runtime by `EEBusOhEntityHandler`, deliberately NOT referenced from `oh-entity`'s own `<channel-groups>`" pattern as `mpc` - no XML reference added, consistent with docs/ADR/014-dynamic-client-role-channels.md.
- [x] 2.4 Re-verified well-formed via `xml.dom.minidom.parse`
- [x] 2.5 CRLF preserved

## 3. EEBusMgcpClientUseCase (new file)

- [x] 3.1 New class, structurally mirrors `EEBusMpcClientUseCase`: `getActor()` returns `"MonitoringAppliance"`, `getName()` returns `"monitoringOfGridConnectionPoint"` (both confirmed directly against `hagers10.json`, no further verification needed), `getScenarioSupport()` returns `List.of(2L)`, `@AllowedEntityTypes({EntityTypeEnumType.CEM})`
- [x] 3.2 `setup()`: `addUseCaseListener` with expected peer actor `"GridConnectionPoint"`, `Map.of(2L, PresenceIndication.MANDATORY)`, and `communicationPartnerFeatureRequirements` covering both `Measurement` (`measurementListData`/`measurementDescriptionListData`, Mandatory) and `ElectricalConnection` (`electricalConnectionDescriptionListData`/`electricalConnectionParameterDescriptionListData`, Mandatory) per Table 22 of the MGCP TS - resolved per proposal.md's Open Questions
- [x] 3.3 `resolveTotalActivePowerMeasurementId`: scope match (`ScopeTypeEnumType.AC_POWER_TOTAL`) -> measurement-type match (`MeasurementTypeEnumType.POWER`) -> sole-entry fallback, same 3-step chain as `EEBusMpcClientUseCase#resolvePowerMeasurementId` (same rationale: this is the one mandatory data point for this MVP)
- [x] 3.4 `subscribe()`/`applyMeasurement()`: one description read, one initial value read, one subscription per peer - same shape as `EEBusMpcClientUseCase`
- [x] 3.5 Dispatches to `EEBusOhEntityHandler` per the Open Question's resolution (either the generalized shared method, or a new `applyMgcpMeasurement`) - resolved: new fixed-signature `applyMgcpMeasurement(double)`, see ADR-040 Decision 4.
- [x] 3.6 `subscribedPartners` idempotency guard, same as `EEBusMpcClientUseCase` (2026-08-21 fix precedent)
- [x] 3.7 Class javadoc explains the actor-name reuse (`"MonitoringAppliance"` shared with MPC Client) and cites the primary source PDF

## 4. EEBusOhEntityHandler / EEBusHandler wiring

- [x] 4.1 New Channel-apply method for `mgcp#total-active-power` (per 3.5's resolution)
- [x] 4.2 `EEBusHandler#deriveLocalUseCases`: `if (clientUseCaseKeys.contains("MGCP"))` branch instantiating `EEBusMgcpClientUseCase`; `"MGCP"` added to the implemented-use-cases set so it drops out of `unimplementedClientUseCases`
- [x] 4.3 Javadoc on `deriveLocalUseCases` updated to mention MGCP alongside MPC/LPC/LPP

## 5. Tests

- [x] 5.1 Unit test: Channel-apply method creates the Channel and updates its state (mirrors `whenApplyMpcPowerCalledThenChannelCreatedAndStateUpdated`)
- [x] 5.2 Unit test: repeated calls do not duplicate the Channel
- [x] 5.3 **Known gap, same precedent as prior Client-role use-case changes (acknowledged, not fixed):** no unit test exercises `EEBusMgcpClientUseCase`'s resolution/dispatch end-to-end - no `jeebus.spine` `Device`/`NodeManagement`/`UseCasePartner` test fixture exists in this binding yet

## 6. Documentation

- [x] 6.1 New ADR (`docs/ADR/040-mgcp-client-usecase.md`) - documents the actor/name choices, the Open Questions' resolutions, and the deferred-scenarios rationale; references `EEBus_UC_TS_MonitoringOfGridConnectionPoint_V1.0.0_public.pdf`
- [x] 6.2 `docs/changes/mgcp-client-usecase/{proposal.md,specs/mgcp-client-usecase/spec.md,tasks.md}` (this change)
- [x] 6.3 `README.md`: new `oh-entity` Channels-table row for `mgcp#total-active-power`, new "Supported Things"/Channels-section prose mentioning MGCP alongside MPC/LPC/LPP
- [x] 6.4 `CONCEPT.md`: new §5.4.x MGCP data-point table (mirrors §5.4.3's MPC/LPC tables) marking Scenario 2 `(implementiert)` and Scenarios 1/3/4/5/6/7 as documented-but-not-yet-implemented; new decided-subsection (§4.12); new §7 checklist entry (29); also corrected two now-outdated "MGCP has no TS-PDF" claims in §5.4.2/Quellen while at it.

## 7. Self-QA

- [x] 7.1 Brace/paren balance checked on every touched/new `.java` file
- [x] 7.2 `thing-types.xml` re-parsed with `xml.dom.minidom` after every edit
- [x] 7.3 CRLF preserved on every touched file
- [x] 7.4 Grep sweep confirms constant/channel-id consistency across `thing-types.xml`, `EEBusBindingConstants`, `EEBusMgcpClientUseCase`, `EEBusOhEntityHandler`, `EEBusHandler`, `README.md`
- [x] 7.5 Markdownlint clean on every touched `.md` file

## 8. Verification (user-owned, same pattern as every prior ADR)

- [ ] 8.1 `mvn clean install` - no Maven available in the editing sandbox
- [ ] 8.2 Live retest against the real Hager Energy S10: confirm MGCP is detected for entity `[6]` (`GridConnectionPoint`), confirm `mgcp#total-active-power` appears and updates with a plausible sign (positive while net consuming)
- [ ] 8.3 Live retest: confirm this succeeding is independent of the still-open LPC `COMMAND_REJECTED` issue - i.e. general SHIP/SPINE communication is confirmed healthy via a second, unrelated use case
