# Tasks: EnergyGuard Outgoing Heartbeat

## 1. Feature Requirements

- [x] 1.1 `AbstractEEBusLimitEnergyGuardUseCase#getFeatureRequirements` adds a
      `DeviceDiagnosis`/`SERVER`/`HeartbeatDataFunction` entry, mirroring
      `AbstractEEBusLimitControllableSystemUseCase`'s identical entry
- [x] 1.2 Verified (read jeebus.spine source, not modified) that `EntityImpl#satisfyFeatureRequirement`
      keys its `featuresMap` by `FeatureTypeEnumType` only, and `Feature#getOrAddFunction` /
      `DeviceDiagnosisFeature#addHeartBeatDataFunction` are idempotent - so an `oh-device` Bridge
      running both Controllable-System-role and EnergyGuard-role LPC/LPP Use Cases on the same
      local CEM Entity safely shares one `DeviceDiagnosis` feature and one Heartbeat schedule, no
      duplicate feature/timer

## 2. Java Implementation

- [x] 2.1 New private `setupDeviceDiagnosis(Entity)` method on
      `AbstractEEBusLimitEnergyGuardUseCase`, calling
      `deviceDiagnosisFeature.addHeartBeatDataFunction(60); deviceDiagnosisFeature.startHeartbeat();`
      - exact mirror of the Controllable-System side's method of the same name
- [x] 2.2 New private `findFeature`/`findFeatureWrapper` helpers (duplicated from the
      Controllable-System side, not shared - existing per-class-self-contained convention)
- [x] 2.3 `setup()` calls `setupDeviceDiagnosis(localEntity)`
- [x] 2.4 New imports: `Feature`, `FeatureWrapper`, `RoleType`,
      `devicediagnosis.DeviceDiagnosisFeature`, `devicediagnosis.HeartbeatDataFunction`
- [x] 2.5 Class-level Javadoc "Not implemented here" paragraph replaced with a "Scenario 3
      (Heartbeat)" paragraph describing the new behavior (mirroring the Controllable-System
      side's own Scenario 3 paragraph structure)
- [x] 2.6 `getFeatureRequirements()`'s inline comment updated

## 3. Documentation

- [x] 3.1 `docs/ADR/035-energy-guard-outgoing-heartbeat.md` (new, Accepted)
- [x] 3.2 `docs/changes/energy-guard-outgoing-heartbeat/{proposal.md,tasks.md,
      specs/energy-guard-heartbeat/spec.md}` (this proposal)
- [x] 3.3 `CONCEPT.md` §7 item (19) marked `[x]` UMGESETZT, cross-referencing ADR-035
- [x] 3.4 `README.md` - confirmed no update needed (no new Channel/config, no user-visible
      change)

## 4. Self-QA

- [x] 4.1 Brace/paren balance checked on the touched `.java` file (221/221 braces, 324/324
      parens)
- [x] 4.2 CRLF preserved on the touched `.java` file and every new/edited `.md` file (verified via
      `file` before and after, and a bare-LF scan on the `.java` file)
- [x] 4.3 Grep sweep confirms `setupDeviceDiagnosis`/`DeviceDiagnosisFeature`/
      `HeartbeatDataFunction` appear only where intended in
      `AbstractEEBusLimitEnergyGuardUseCase.java`

## 5. Release (user-owned build pipeline - not run in this sandbox, no local Maven)

- [ ] 5.1 `mvn spotless:apply`
- [ ] 5.2 `mvn i18n:generate-default-translations` (no i18n keys touched by this change, expected
      no-op)
- [ ] 5.3 `mvn clean install`
- [ ] 5.4 Live retest against a real ControllableSystem peer (e.g. Hager Energy S10) - confirm the
      peer now sees a `DeviceDiagnosis` Heartbeat from this binding's EnergyGuard entity
- [ ] 5.5 Once released, move this change folder to
      `docs/changes/archive/<date>-energy-guard-outgoing-heartbeat/`
