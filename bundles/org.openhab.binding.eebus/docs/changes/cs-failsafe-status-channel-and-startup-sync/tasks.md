# Tasks: Controllable System failsafe status Channel and startup/reconnect status sync

## 1. Failsafe status Channel

- [x] 1.1 Add `CHANNEL_FAILSAFE_LIMIT_VALUE`/`CHANNEL_FAILSAFE_DURATION_MINIMUM` and their
      `ChannelTypeUID`s to `EEBusBindingConstants`
- [x] 1.2 Add `failsafe-limit-value` (`Number:Power`)/`failsafe-duration-minimum`
      (`Number:Time`) `channel-type` declarations to `thing-types.xml`, and list both under the
      `lpc`/`lpp` `channel-group-type`s
- [x] 1.3 Add `EEBusOhPeerHandler#applyFailsafeStatus(String, double, long)`, mirroring
      `applyLimitStatus`
- [x] 1.4 Track `lastFailsafeLimitWatts` in `AbstractEEBusLimitControllableSystemUseCase`, add
      `publishFailsafeStatus()`, call it from `onFailsafeLimitWritten`/`onFailsafeDurationWritten`
- [ ] 1.5 Confirm no write path exists for the two new Channels (read-only `state`, no
      `handleCommand` forwarding) - self-reviewed, needs confirming once `mvn clean install` is
      available

## 2. Startup/reconnect status sync

- [x] 2.1 Explicitly publish the freshly constructed state machine's state
      (`onStateChanged(newStateMachine.getState())`) and `publishFailsafeStatus()` immediately in
      `setup()`, right after `setupLoadControl`/`setupDeviceConfiguration`/the state machine
      construction
- [x] 2.2 Explicitly re-publish current state and failsafe status once `onEnergyGuardFound`
      resolves a peer handler, so a late-resolving peer does not lag behind status already known

## 3. Documentation

- [x] 3.1 `docs/ADR/022-controllable-system-failsafe-status-channel-and-startup-sync.md`
- [x] 3.2 CONCEPT.md §7 checklist item (18); data-point table note
- [x] 3.3 README.md Channels table

## 4. Verification (user-owned, same pattern as ADR-016 through ADR-021)

- [ ] 4.1 `mvn clean install` - no Maven available in the editing sandbox, self-review only so
      far (brace/paren balance checked programmatically, XML well-formedness checked, CRLF
      preserved on all touched files)
- [ ] 4.2 Live retest: pair a real (or second self-built) Energy Guard against openHAB's
      Controllable System role, confirm `failsafe-limit-value`/`failsafe-duration-minimum`
      populate on write, and confirm `LPC.state`/`limit-active` reset to a safe value immediately
      after an openHAB restart (not stale for up to 120s)

---
