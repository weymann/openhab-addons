# ADR-025: Dedicated `oh-cs-entity` Thing Type with Statically Declared LPC/LPP Channels

## Status

> Accepted

## Context

`eebus:oh-cs-device` (docs/ADR/023-cs-service-convenience-bridge.md) is a convenience Bridge
that always offers exactly LPC+LPP as the Controllable System (Server) actor - no Use Case
checkboxes, no other configuration path exists for it. Its child Entity Thing, until now,
was the same generic `eebus:oh-entity` used under the fully generic `eebus:oh-device` Bridge
(docs/ADR/024-oh-device-oh-entity-rename.md added `eebus:oh-cs-device` to `oh-entity`'s
`supported-bridge-type-refs` as an incidental fix, not a deliberate design choice - see that
ADR's "Post-acceptance bug" note in `docs/changes/cs-service-convenience-bridge/`).

`eebus:oh-entity`'s `lpc`/`lpp` Channel Groups are deliberately **not** referenced from its own
`<channel-groups>` in `thing-types.xml` (docs/ADR/014-dynamic-client-role-channels.md,
docs/ADR/015-lpc-lpp-client-role-channels.md): since a generic `eebus:oh-entity` under
`eebus:oh-device` might represent an Entity offering any use case or none, its Channels are
created dynamically, on first EEBus event, so an Entity that never actually does LPC/LPP never
shows misleading empty Channels for it.

That reasoning does not hold for `eebus:oh-cs-device`. Every Entity trusted under it is, by the
Bridge's own fixed configuration, always paired against LPC+LPP server functionality - there is
no "maybe" to gate on. Waiting for the first EEBus event before showing `limit-active`/
`limit-value`/`failsafe-limit-value`/`failsafe-duration-minimum` therefore only delays
Channels the user already knows will appear, and leaves the Thing looking channel-less
immediately after being trusted (i.e. exactly the window in which a user is most likely to be
looking at it to confirm it worked).

## Decision

### 1. New `eebus:oh-cs-entity` Thing type, exclusive to `eebus:oh-cs-device`

`eebus:oh-cs-device`'s `supported-bridge-type-refs` no longer includes `oh-entity` at all;
`eebus:oh-cs-entity` is now the only child Thing type it accepts. `eebus:oh-entity`'s own
`supported-bridge-type-refs` is narrowed back down to `oh-device` only - restoring the
pre-ADR-023-Post-acceptance-fix intent, now met by giving `oh-cs-device` its own dedicated type
instead of re-widening `oh-entity`.

This mirrors the existing `oh-device`/`oh-cs-device` split at the Bridge level: a convenience
Bridge Thing type paired with its own convenience Entity Thing type, rather than the generic
Entity type growing an implicit "and also serves the CS convenience Bridge" responsibility.

### 2. `lpc`/`lpp` Channel Groups declared statically

`eebus:oh-cs-entity` declares:

```xml
<channel-groups>
    <channel-group id="lpc" typeId="lpc"/>
    <channel-group id="lpp" typeId="lpp"/>
</channel-groups>
```

which instantiates all four Channels in each group (`limit-active`, `limit-value`,
`failsafe-limit-value`, `failsafe-duration-minimum` - the `lpc`/`lpp` `channel-group-type`
definitions themselves are unchanged) at Thing creation time, before any EEBus event has
occurred. They start at their Item's default (`NULL`) state until the first real
`applyLimitStatus`/`applyFailsafeStatus` call, exactly as any other openHAB Channel does before
its first update - no placeholder/sentinel value is invented.

### 3. Same config and handler classes as `eebus:oh-entity` - no duplication

`eebus:oh-cs-entity` reuses `EEBusOhEntityConfiguration` (`ski`, `entityAddress`, `shipId` -
identical fields, identical meaning) and `EEBusOhEntityHandler` (identical trust-status
derivation, identical `applyLimitStatus`/`applyFailsafeStatus`/`applyMpcPower` methods) rather
than introducing parallel classes. This is safe and requires no code changes to either class:

- `EEBusHandler#ohEntityHandlerForSki`/`#ohEntityHandlerForCommunicationAddress` already resolve
  by `child.getHandler() instanceof EEBusOhEntityHandler`, not by Thing-type UID - an
  `eebus:oh-cs-entity` child is found exactly the same way an `eebus:oh-entity` child is.
- `EEBusOhEntityHandler#ensureChannel` already no-ops when the target Channel already exists
  (`if (thing.getChannel(channelUID) != null) return;`) - since `eebus:oh-cs-entity` declares its
  `lpc`/`lpp` Channels statically, `ensureChannel` simply finds them pre-existing and skips
  straight to `updateCachedState`. No branch on Thing type is needed anywhere in the handler.

This mirrors the precedent already set by `EEBusConfiguration`/`EEBusHandler` themselves being
shared, unmodified, between `eebus:oh-device` and `eebus:oh-cs-device` (docs/ADR/023-cs-service-
convenience-bridge.md) - a config/handler class is shared whenever two Thing types genuinely need
identical behavior, and only `thing-types.xml`'s declaration differs.

### 4. `entityAddress` retained

`eebus:oh-cs-entity` keeps the same `entityAddress` config field `eebus:oh-entity` has
(docs/ADR/024-oh-device-oh-entity-rename.md), for the same reason and with the same current
limitation: it identifies which SPINE Entity on the trusted device this Thing represents, but is
not yet wired into the transport-layer resolver (still Device/`ski`-granular). Keeping the field
here, even though not yet acted upon, keeps both Entity Thing types consistent and ready for the
same future per-Entity-routing work without a second follow-up migration later.

## Consequences

### Positive

- `eebus:oh-cs-device`'s Channels are visible immediately after an Entity is trusted, rather than
  only after the first limit/failsafe event - matching the "always LPC+LPP" guarantee the Bridge
  itself already makes.
- `eebus:oh-device`/`eebus:oh-entity` stays exactly as generic and use-case-agnostic as before -
  this change does not compromise the dynamic-Channel design for that pairing, it only opts the
  CS-specific pairing out of it, where the "maybe" the dynamic design guards against does not
  apply.
- Zero code duplication: reusing `EEBusOhEntityConfiguration`/`EEBusOhEntityHandler` keeps the
  actual behavior in exactly one place, consistent with the `EEBusHandler` precedent.

### Negative

- Two Entity Thing types now exist (`oh-entity`, `oh-cs-entity`) instead of one, each with its
  own `thing-types.xml` declaration and Main UI "Add Thing" entry - a small increase in surface
  area for a difference that is otherwise invisible in the Java source (the shared handler class
  javadoc calls this out explicitly to keep it discoverable).
- `eebus:oh-cs-entity`'s Channels exist even for a device that has not yet sent any LPC/LPP data
  (e.g. immediately after trust, before the CS use case's own `setup()`/`onEnergyGuardFound` has
  run) - they simply read `NULL` until then, same as any Channel before its first update; no
  functional risk, just something a user reading this ADR should expect rather than be surprised
  by.

## Migration

No automated migration. Any `eebus:oh-entity` Thing currently configured under an
`eebus:oh-cs-device` Bridge (possible since ADR-024's Post-acceptance fix widened
`supported-bridge-type-refs`) will, after upgrading, no longer be offered as addable under that
Bridge type in the "Add Thing" wizard, and openHAB will report it with an unknown/invalid
bridge-type reference on the next restart if left in place. Delete the old `eebus:oh-entity`
Thing and recreate it as `eebus:oh-cs-entity` with the same `ski`/`entityAddress` values - the
underlying trust state (the parent Bridge's `trustedSkis`) is unaffected by this and does not
need to be re-granted.

---

_Refines docs/ADR/023-cs-service-convenience-bridge.md and docs/ADR/024-oh-device-oh-entity-rename.md -
neither superseded, both remain in force. ADR-024's incidental widening of `oh-entity`'s
`supported-bridge-type-refs` to include `oh-cs-device` (a Post-acceptance bug fix, not a
deliberate design choice - see `docs/changes/cs-service-convenience-bridge/`) is reverted here in
favor of this dedicated type._

## Revision (docs/ADR/027-derive-local-use-cases-from-entities.md, 2026-08-24)

`eebus:oh-cs-device` (the Bridge this ADR's Decision 1 made `eebus:oh-cs-entity` exclusive to) is
removed by ADR-027 - the local Use-Case set a Bridge builds is now derived from its children
instead of fixed by a dedicated Bridge Thing type. `eebus:oh-cs-entity`'s
`supported-bridge-type-refs` changes from "exclusive to `eebus:oh-cs-device`" to "additive under
`eebus:oh-device`" - structurally identical to `eebus:oh-eg-entity` (ADR-026) now. It also gains
`failsafeConsumptionLimitSeedWatts`/`failsafeProductionLimitSeedWatts`/
`failsafeDurationMinimumSeedSeconds` as its own Thing-config parameters, carried over from the
removed Bridge, since it is the only remaining Thing that still needs them to seed the LPC/LPP
Server Use Cases it continues to imply unconditionally.

This ADR's Decisions 2-4 (statically declared `lpc`/`lpp` Channel Groups, shared config/handler
classes, `entityAddress`) are unaffected and remain in force.
