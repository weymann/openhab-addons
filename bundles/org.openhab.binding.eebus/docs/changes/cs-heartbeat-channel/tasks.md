# Tasks: Controllable System Heartbeat Channel

## 1. Thing Type / Channel Definition

- [x] 1.1 Add `channel-type id="heartbeat" kind="trigger"` to `thing-types.xml`.
- [x] 1.2 Reference it from both the `lpc` and `lpp` `channel-group-type` `<channels>` lists.
- [x] 1.3 Add `CHANNEL_HEARTBEAT`/`CHANNEL_TYPE_UID_HEARTBEAT` constants to
      `EEBusBindingConstants`.

## 2. Handler

- [x] 2.1 Add `EEBusOhEntityHandler#ensureTriggerChannel(ChannelUID, ChannelTypeUID, String)`,
      the trigger-Channel counterpart to the existing `ensureChannel`.
- [x] 2.2 Add `EEBusOhEntityHandler#triggerHeartbeat(String channelGroup, @Nullable BigInteger
      heartbeatCounter)`, firing the Channel via `triggerChannel(...)`.

## 3. Use Case Wiring

- [x] 3.1 Change the Heartbeat subscription lambda in
      `AbstractEEBusLimitControllableSystemUseCase#maybeSubscribeToEnergyGuardHeartbeat` to pass
      the `RequestResult` through instead of discarding it.
- [x] 3.2 Extract `DeviceDiagnosisHeartbeatDataType`/`heartbeatCounter` in
      `onHeartbeatNotification` and call `energyGuardOhEntityHandler.triggerHeartbeat(...)` when
      that field is already resolved.

## 4. Documentation

- [x] 4.1 Write ADR-045 documenting the decision, including the architecture correction found
      while designing this (no new local-handler resolver needed).
- [x] 4.2 Write this change's delta spec (`specs/heartbeat/spec.md`).

## 5. Verification (not yet done - needs the user's build/test environment)

- [ ] 5.1 Compile (`$Release`'s `clean install` step, or a targeted `mvn compile` on this
      module) - not run from this session.
- [ ] 5.2 Retest against the local simulation rig and/or the real Hager Energy S10: confirm
      `lpc#heartbeat`/`lpp#heartbeat` fire roughly every 60s once an Energy Guard is paired.
