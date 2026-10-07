# Tasks: HEMS Convenience Thing

## 1. Documentation

- [x] 1.1 ADR-053.
- [x] 1.2 This proposal and task list.

## 2. Thing Type / Config

- [x] 2.1 `THING_TYPE_OH_HEMS_ENTITY` and Channel Group constants in `EEBusBindingConstants`.
- [x] 2.2 `gatewaySki`, `wallboxSki`, `heatPumpSki`, `egEntityType` in `EEBusOhEntityConfiguration`.
- [x] 2.3 `oh-hems-entity` in `thing-types.xml` (groups, config description).
- [x] 2.4 Register the Thing type in `EEBusHandlerFactory`.

## 3. Handler

- [x] 3.1 HEMS status derivation (all configured SKIs trusted), no `ski` auto-selection.
- [x] 3.2 Accept `<prefix>-lpc` / `<prefix>-lpp` groups in `handleLimitChannelCommand`.

## 4. Use Cases

- [x] 4.1 Target scope (`partnerSki`, `channelGroupPrefix`) in the Energy Guard base class.
- [x] 4.2 Scoped constructors in `EEBusLpcClientUseCase` / `EEBusLppClientUseCase`.

## 5. Bridge Handler

- [x] 5.1 `deriveLocalUseCases` returns a list of Entity specifications.
- [x] 5.2 `startShipSpineLocked` builds all Entities before `connect()`.
- [x] 5.3 Resolvers: gateway SKI match, HEMS write source, SKI of a communication address.

## 6. Verification (on the user's machine)

- [ ] 6.1 `mvn clean install` with Spotless.
- [ ] 6.2 Live test: gateway simulator 4200 W, wallbox and heat pump with different limits.

## 7. Phase 2a: read-only EV use cases (ADR-054)

- [x] 6.1 ADR-054.
- [x] 6.2 `AbstractEEBusEvClientUseCase`, `EEBusEvseccClientUseCase`, `EEBusEvccClientUseCase`, `EEBusEvcemClientUseCase`.
- [x] 6.3 Channel groups `wallbox-evse`, `wallbox-ev`, `wallbox-evcem` and `ev-*` Channel types.
- [x] 6.4 `applyEvText` / `applyEvSwitch` / `applyEvQuantity` in `EEBusOhEntityHandler`.
- [ ] 6.5 Compile, Spotless, live test with a wallbox (phase 2b: OPEV, EVCC scenario 6).
