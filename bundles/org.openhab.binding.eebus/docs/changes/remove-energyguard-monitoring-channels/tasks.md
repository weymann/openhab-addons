# Tasks: Remove EnergyGuard (Client-Role) LPC/LPP Monitoring Channels

## 1. Java: AbstractEEBusLimitEnergyGuardUseCase

- [x] 1.1 Remove `subscribeLimitStatus(FeatureAddressType, long, EEBusOhEntityHandler)`.
- [x] 1.2 Remove `applyLimitStatus(RequestResult, long, EEBusOhEntityHandler)`.
- [x] 1.3 Rename `resolveLimitIdAndSubscribe(...)` to
      `resolveLimitIdAndRegisterWriteListeners(...)`, drop its `subscribeLimitStatus(...)` call,
      keep `registerWriteListeners(...)`.
- [x] 1.4 Update class-level javadoc (no longer "read-only monitoring counterpart"; explains the
      write-only role and points at ADR-031) and the method/field javadoc referencing the
      renamed method or the removed status-read wording.
- [x] 1.5 Verify no other production call site depends on the removed methods (grep sweep).

## 2. thing-types.xml

- [x] 2.1 Remove `eebus:oh-eg-entity`'s `<channel-groups>` block.
- [x] 2.2 Update its preceding XML comment and `<description>` to describe the write-only role,
      referencing ADR-031 alongside the existing ADR-026/027 references.
- [x] 2.3 Re-parse the file to confirm it stays well-formed XML.

## 3. Docs

- [x] 3.1 `docs/ADR/031-remove-energyguard-monitoring-channels.md` (new, Accepted).
- [x] 3.2 `docs/ADR/015-lpc-lpp-client-role-channels.md`: Status → `Superseded by ADR-031`, note
      appended.
- [x] 3.3 `docs/ADR/026-oh-eg-entity-static-channels.md`: revision note appended, Status stays
      `Accepted`.
- [ ] 3.4 `README.md`: rewrite the Channels section's Client-role framing paragraph (LPC/LPP is
      no longer a Client-role Channel case) and the `oh-eg-entity`/Channels table rows.
- [ ] 3.5 `CONCEPT.md`: update §4.9 (`oh-eg-entity`) and add a new §7 checklist item recording
      this change.
- [x] 3.6 This `docs/changes/remove-energyguard-monitoring-channels/` folder
      (proposal.md/specs/tasks.md).

## 4. Self-QA

- [x] 4.1 Brace/paren balance check on the touched `.java` file.
- [x] 4.2 CRLF preserved (byte-checked) on every touched `.java`/`.xml`/`docs/**.md` file.
- [x] 4.3 `thing-types.xml` re-parsed with `xml.dom.minidom` (well-formed).
- [x] 4.4 Grep sweep: no stale references to `subscribeLimitStatus`/`resolveLimitIdAndSubscribe`/
      `applyLimitStatus` (production code) remain.

## 5. Verification (user-owned - no local Maven in this sandbox)

- [ ] 5.1 `mvn clean install`.
- [ ] 5.2 Live retest: an `eebus:oh-eg-entity` (or plain `eebus:oh-entity` used for EnergyGuard)
      shows no Channels, while its tagged-Item write path still successfully commands a real
      peer's LoadControl limit exactly as before this change.
- [ ] 5.3 Live retest: the Controllable-System side (`eebus:oh-cs-entity`) is unaffected - its
      `limit-active`/`limit-value`/`failsafe-*` Channels still populate on a real EnergyGuard
      write, unchanged.

---
