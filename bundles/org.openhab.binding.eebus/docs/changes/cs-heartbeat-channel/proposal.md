# Proposal: Controllable System Heartbeat Channel

## Intent

The Controllable System (LPC/LPP Server) role already receives a Heartbeat notification from
its paired Energy Guard roughly every 60 seconds, but nothing in openHAB ever observes it - the
notification only rearms an internal watchdog. The user wants a discoverable signal for "the
paired Energy Guard is alive right now", usable directly in a Rule, without waiting for the
watchdog to time out first.

## Scope

In scope:

- A new `heartbeat` trigger Channel, added to both the `lpc` and `lpp` Channel Groups.
- Firing that Channel once per Heartbeat notification actually received from the paired Energy
  Guard, on the peer's `eebus:oh-cs-entity`/`eebus:oh-entity` Thing.
- Passing the SPINE `heartbeatCounter` value as the trigger event payload, so a Rule can detect
  a skipped Heartbeat if it chooses to.

Out of scope:

- The Energy Guard (Client role) side sending its own Heartbeat - a separate, already-tracked
  TODO (CONCEPT.md §7 item 19), independent of this change.
- Any change to `jeebus.ship`/`jeebus.spine` - both already expose everything this change needs.
- A state-based ("last seen") Channel - considered and rejected, see ADR-045.

## Open Questions

None - resolved during `$Concept`/`$Architect` before this proposal was written; see ADR-045
for the rejected alternatives and the architecture correction that shaped the final design.
