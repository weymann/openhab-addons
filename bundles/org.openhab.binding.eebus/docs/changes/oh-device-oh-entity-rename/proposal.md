# Proposal: EEBUS-vocabulary Thing-model rename and Bridge-level trust relocation

## Intent

Follow-up to a `$Concept` discussion (2026-08-23) about the binding's Bridge/Thing type names
drifting from actual EEBUS/SHIP/SPINE spec vocabulary (`service`/`oh-service`, `peer`/`oh-peer`).
Grounded against primary sources (`EEBus_SHIP_TS_Specification_v1.1.0.pdf` §3, `EEBus_SPINE_
V1.3.0_Final_hp` §2.2): SHIP formally defines "SHIP Node"/"Trusted SHIP Node" (not "peer"/
"pairing" - "Pairing" names a distinct, unimplemented QR/PIN mechanism per `EEBus_SHIP_Pairing_
Service_TS_Specification_V1.0.0.pdf`); SPINE formally defines a three-level Device -> Entity ->
Feature hierarchy. The user confirmed, iteratively refining across several turns, a full rename
plus one structural change: trust/pairing management moves from the paired-device Thing up to
the Bridge, because SKI-based trust is Device-granular in SHIP, not Entity-granular in SPINE - a
child Thing representing one SPINE Entity is the wrong place to hold it.

The user explicitly confirmed the structural change and instructed implementation "genau nach
deinem Vorschlag" (exactly per your proposal), then separately chose, via a scoping question, the
exact trust mechanism: a Bridge config-list of trusted SKIs as source of truth, with `trust()`/
`untrust()` Bridge Actions as convenience mutators of that same list (not a competing storage).

## Scope

In scope:

- Rename `eebus:service` (Bridge) -> `eebus:oh-device` ("EEBus OH Device").
- Rename `eebus:cs-service` (Bridge) -> `eebus:oh-cs-device` ("EEBus OH Controllable System
  Device").
- Rename `eebus:eebus-peer` (bridgeless Thing) -> `eebus:hw-device` ("EEBus Hardware Device") -
  unchanged behavior (passive, mDNS-populated real-device record, no channels, no trust).
- Rename `eebus:oh-peer` (Thing) -> `eebus:oh-entity` ("EEBus OH Entity"), re-scoped to represent
  one SPINE Entity (not an entire device) - adds a new `entityAddress` config field alongside the
  existing `ski`.
- Move trust management off `eebus:oh-entity` and onto its parent `eebus:oh-device`/
  `eebus:oh-cs-device` Bridge: a new `trustedSkis` Bridge config parameter (list) is the source of
  truth, with `trust(ski)`/`untrust(ski)` Bridge Actions (`EEBusDeviceActions`, replacing
  `EEBusOhPeerActions`) as convenience mutators of that same list - not a second, competing
  persistence mechanism. `eebus:oh-entity`'s status now reflects whether its parent Bridge
  currently trusts its configured `ski`, instead of carrying its own pairing property.
- This explicitly supersedes docs/ADR/012-pairing-trust-property-and-actions.md's decision to
  keep trust as a per-peer Thing property/Actions pair - see docs/ADR/024 for the full
  before/after and why this is a revision, not a silent contradiction.
- Channels on `eebus:oh-entity` (Features grouped by Use Case: `mpc`/`lpc`/`lpp` Channel Groups)
  are structurally unchanged from today's `oh-peer` Channel-Group pattern - only the Thing they
  live on is renamed/re-scoped, not the Channel model itself.
- Mechanical rename of every affected Java identifier (classes, files, method names, constants)
  and `thing-types.xml` id/label/description, so the code itself matches the new vocabulary
  consistently - not just the Thing-type ids.
- Fixed while touching `EEBusMdnsDiscoveryParticipant#isOwnService` for the rename: it only
  checked `THING_TYPE_SERVICE`/`eebus:service` Bridges' own SKI, not `THING_TYPE_CS_SERVICE`/
  `eebus:cs-service` - a `cs-service` Bridge's own mDNS self-announcement could have been
  incorrectly offered back as a `hw-device` Inbox suggestion. Now checks both Bridge types.

Out of scope:

- Entity-address-based routing/resolution in the transport layer. The transport-layer resolver
  (`EEBusHandler#ohEntityHandlerForSki`, feeding every `*UseCase` class's `ohEntityHandlerResolver`)
  continues to resolve purely by SKI (Device-level), returning the first matching `eebus:oh-entity`
  child Thing. `entityAddress` is recorded as Thing config now so a future change can wire in
  per-Entity routing without another Thing-model rename, but multiple `eebus:oh-entity` Things
  sharing one `ski` will all currently observe the same Device-level events; `entityAddress` does
  not yet filter/route them apart. This mirrors the size of change already flagged as a bigger,
  separate task by the jeebus.spine NodeManagement-notification stub
  (github.com/openmuc/jeebus.spine#11) - true live per-Entity structural updates need that fixed
  upstream first, and jeebus.spine is protected (no changes without prior human approval).
- A UI/wizard beyond the standard "Add Thing" flow and the existing `trust()`/`untrust()` Action
  buttons and `trustedSkis` config-list editor Main UI already renders for a `text`, `multiple`
  config parameter.
- Any change to `eebus:hw-device`'s own behavior beyond the rename - it stays a thin, passive
  status holder exactly as `eebus:eebus-peer` was.
- Renaming `EEBusHandler` itself (the Bridge handler class) - its own class javadoc already
  documents it kept the archetype's file/class name deliberately; this pass only updates its
  Thing-type references, not its own name.

## Open Questions

- None blocking. The two items flagged open in project memory after the `$Concept` discussion are
  both resolved: the trust mechanism was decided via an explicit scoping question (config-list +
  convenience Actions), and the `oh-entity` auto-discovery risk was already re-scoped to low
  severity (initial per-connection discovery is unaffected by the jeebus.spine stub; only live
  post-connection structural updates are, which is the same, already-out-of-scope limitation
  noted above).

---
