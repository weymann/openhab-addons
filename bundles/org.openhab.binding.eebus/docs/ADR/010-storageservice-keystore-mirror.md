# ADR-010: Mirror the SHIP Keystore into StorageService

## Status

> Proposed

## Context

An `eebus:service` Bridge's own SHIP identity (certificate, and therefore its SKI - the value
real devices like a Hager Energy S10 must trust) is generated and read by jeebus.ship's
`ShipNodeConfiguration`, which only knows how to persist it as a `.jks` file on disk
(`EEBusHandler#getKeystoreFile()`, under `${userdata}/eebus/<thing-uid>.jks`). This is a plain
file outside of any openHAB-managed persistence layer.

java-coding-rules.md establishes `StorageService` as this project's standard mechanism for
persistent Thing state ("Persistent state - use `StorageService`, not custom persistence"). The
keystore file is exactly this kind of state - it was simply never routed through it, because
`ShipNodeConfiguration` predates that convention being written down and only accepts a file path.

The "correct" fix would be to stop using a file at all: jeebus.ship 2.3.0 (CHANGELOG.md,
2026-07-09) introduced `ConfigBuilder#withCertificateStorage(CertificateStorage)`, a pluggable
storage interface that a `StorageService`-backed `CertificateStorage` implementation could satisfy
directly. However, `ShipCommunication` in the pinned `jeebus.spine:4.0.1` (2026-05-26, predates
ship 2.3.0) only exposes a constructor taking the older, file-path-only `ShipNodeConfiguration` -
confirmed by reading both projects' CHANGELOG.md files and `ShipCommunication`'s source, since no
build/bytecode inspection was possible in this session (see Negative). Wiring up
`CertificateStorage` would require changing `jeebus.spine`, which per this project's rules
("jeebus.ship und jeebus.spine dürfen nicht ohne vorherige menschliche Zustimmung geändert
werden") needs explicit prior human approval that has not been sought for this change.

## Decision

Keep the `.jks` file as the operational source of truth handed to `ShipNodeConfiguration` (no
behavior change there), and additionally mirror its bytes into a `StorageService`-backed
`Storage<String>` (base64-encoded, one entry keyed by a fixed `"keystore"` key, in a per-Thing
storage obtained via `storageService.getStorage(thing.getUID().toString(), ...)` - the same
storage-naming convention already documented in java-coding-rules.md):

- `EEBusHandler#restoreKeystoreFromStorage(File)` runs at the start of `startShipSpine()`, before
  `ShipNodeConfiguration` is constructed: if the file is missing but storage has a mirror, it
  writes the file back from storage first. A no-op whenever the file already exists, so the file
  is never overwritten from a possibly-stale snapshot.
- `EEBusHandler#persistKeystoreToStorage(File)` runs right after a successful start, mirroring the
  file's current bytes into storage.
- Unlike the standard `StorageService` pattern, the entry is **not** removed in
  `handleRemoval()` - see "Negative" below and `SKI.md`/README.md, which already document that the
  keystore file itself intentionally survives Thing deletion so a Bridge recreated with the same
  Thing ID keeps the same SKI.

`EEBusHandlerFactory` and `EEBusHandler`'s constructors gained a `StorageService` parameter,
following the existing `@Reference`-per-constructor-parameter pattern already used for
`EEBusMetadataService`/`MDNSClient`/`EEBusPortPool`.

## Consequences

### Positive

- The SHIP identity is now reachable through this project's standard persistence mechanism, not
  only as a loose file under userdata - e.g. it is included wherever `StorageService`'s own
  backing store is backed up, independent of whether `${userdata}/eebus/` specifically is.
- No change to `jeebus.ship`/`jeebus.spine` - stays within the protected-library constraint
  without waiting on a human-approval round trip.
- Consistent with the already-published guidance in README.md ("The Thing ID matters") and
  SKI.md ("Deleting and recreating the oh-service Bridge itself"): reusing the same Thing ID still
  reuses the same identity, now via two independent paths (file survives deletion; storage mirror
  is never cleared) instead of one.

### Negative

- This is a workaround, not the "real" fix. The `.jks` file is still required and still the
  actual thing `ShipNodeConfiguration` reads/writes - `StorageService` only holds a mirror,
  restored on demand if the file is missing. A future dependency bump past the point where
  `jeebus.spine` exposes `ConfigBuilder`/`ShipConfig` (needs prior human approval, out of scope
  here) could replace this entirely with a `StorageService`-backed `CertificateStorage`
  implementation and drop the file dependency altogether.
- Deliberately not cleaning up the storage entry in `handleRemoval()` diverges from the documented
  `StorageService` convention (java-coding-rules.md). This is an intentional, narrow exception for
  this one field, matching the pre-existing (already documented, unchanged by this ADR) behavior
  of the keystore file itself - not a general license to skip cleanup elsewhere.
- No compile or runtime verification was possible for this change in the session it was written
  in (the available shell sandbox was non-functional); the constructor signature change,
  `Storage<String>`/`StorageService` API usage, and base64 round-trip should be exercised by
  `$QA`/a build before merging.

---

_Adds a `StorageService storageService` constructor parameter to `EEBusHandler` and
`EEBusHandlerFactory`; adds `EEBusHandler#restoreKeystoreFromStorage`/`#persistKeystoreToStorage`
and the `keystoreStorage` field; `handleRemoval()` intentionally does not clear this entry._
