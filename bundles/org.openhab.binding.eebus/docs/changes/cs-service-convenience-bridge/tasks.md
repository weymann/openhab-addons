# Tasks: eebus:cs-service convenience Bridge

## 1. Thing type and constants

- [x] 1.1 `EEBusBindingConstants#THING_TYPE_CS_SERVICE`
- [x] 1.2 New `<bridge-type id="cs-service">` in `thing-types.xml` - same infra params as
      `service` (vendorCode/deviceBrand/deviceModel/serialNumber/mdnsServiceInstance/port/
      autoAcceptEnabled/connectToPeers/preferIpv4), no `supportedUseCasesClient`/
      `supportedUseCasesServer`, plus the three new failsafe seed parameters
- [x] 1.3 `EEBusHandlerFactory`: `THING_TYPE_CS_SERVICE` added to the supported-types set and to
      `createHandler` (same `EEBusHandler` class/constructor as `eebus:service`)
- [x] 1.4 (found via live testing, 2026-08-23) `oh-peer`'s `<supported-bridge-type-refs>` was
      missing a `<bridge-type-ref id="cs-service"/>` entry - without it no `eebus:oh-peer`
      could be added under a `cs-service` Bridge, so it never got any Channels. Fixed by adding
      the missing ref alongside the existing `service` one; see ADR-023's "Post-acceptance
      fix" section.

## 2. Configuration

- [x] 2.1 `EEBusConfiguration`: `failsafeConsumptionLimitSeedWatts`, `failsafeProductionLimitSeedWatts`
      (double, default `0.0`), `failsafeDurationMinimumSeedSeconds` (long, default `120`) - shared
      POJO with `eebus:service`, harmless no-ops there since that Thing type's config-description
      does not declare them

## 3. Server-role use-case construction

- [x] 3.1 `AbstractEEBusLimitControllableSystemUseCase`: constructor gains
      `initialFailsafeLimitWatts`/`initialFailsafeDurationMinimumSeconds`, seeds
      `lastFailsafeLimitWatts`/`failsafeDurationMinimumSeconds` from them; `setupDeviceConfiguration`'s
      initial SPINE-visible failsafe limit value now comes from `lastFailsafeLimitWatts`, not a
      hardcoded `0.0`; `DEFAULT_FAILSAFE_DURATION_MINIMUM_SECONDS` made `public` so `EEBusHandler`
      can pass it explicitly for the unchanged checkbox path
- [x] 3.2 `EEBusLpcServerUseCase`/`EEBusLppServerUseCase`: constructors updated to accept and pass
      through the two new seed parameters
- [x] 3.3 `EEBusHandler`: server-use-case construction branches on `thing.getThingTypeUID()` -
      `eebus:cs-service` always adds LPC+LPP with the Bridge's own Thing-config seed values;
      `eebus:service` keeps its exact prior checkbox-driven behavior, now passing the previously
      implicit `0.0`/`DEFAULT_FAILSAFE_DURATION_MINIMUM_SECONDS` defaults explicitly. Client-role
      use-case construction needed no change - `cfg.supportedUseCasesClient` naturally stays empty
      for `eebus:cs-service` since its config-description does not declare that parameter.

## 4. Documentation

- [x] 4.1 `docs/ADR/023-cs-service-convenience-bridge.md`
- [x] 4.2 CONCEPT.md Thing-model table + §7 checklist item
- [x] 4.3 README.md Supported Things section

## 5. Verification (user-owned, same pattern as ADR-016 through ADR-022)

- [ ] 5.1 `mvn clean install` - no Maven available in the editing sandbox; self-review only so far
      (brace/paren balance checked per touched `.java` file, `thing-types.xml` re-parsed for
      well-formedness, CRLF preserved on every touched file)
- [x] 5.2a Live retest step: adding an `eebus:cs-service` Bridge - confirmed ONLINE, no
      checkbox needed. Surfaced the missing-bridge-type-ref bug (task 1.4), now fixed.
- [ ] 5.2b Live retest step: after re-deploying the task-1.4 fix, add an `eebus:oh-peer` under
      the `cs-service` Bridge, pair a real (or self-built) Energy Guard, confirm the failsafe
      seed values are used at first startup, confirm a subsequent Thing-config edit does not
      push immediately (only takes effect after a restart), and confirm the ADR-022 Channels
      populate on the `oh-peer` exactly as they do under `eebus:service`

---
