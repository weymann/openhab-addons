# ADR-052: Energy Guard Write Channels (value provider) next to Controllable System Channels (value consumer)

## Status

Accepted - partially supersedes ADR-031 (the "Energy Guard has no Channels" half).

## Context

User correction (2026-10-04): there are value _providers_ (Energy Guard) and value _consumers_
(Controllable System); both must be represented through Channels, read/write direction depending
on the role. SKIs and trust handling stay exactly as they are.

## Decision

- `eebus:oh-eg-entity` declares the Channel Groups `lpc` and `lpp` (group types `lpc-eg`/`lpp-eg`)
  with the same Scenario 1 data points as the Controllable System: `limit-active`, `limit-value`,
  `limit-duration`. Here they are **writable** (channel types `eg-limit-*`).
- A command on any of them is remembered, echoed as the Channel state, and triggers one combined
  SPINE LoadControl write (isLimitActive/value/timePeriod, last commanded values of the other two)
  to every bound partner (fan-out, ADR-047). `limit-duration` never commanded = unbounded limit.
- The shared `eg-limit-value` channel type offers the selectable state options 1234 W, 2345 W, 4200 W and 9876 W (both `lpc` and `lpp`); a selected option arrives as a plain number, which the write path treats as Watt.
- `eebus:oh-cs-entity` keeps its read-only Channels (state, `limit-*`, `failsafe-*`, heartbeat).
- The tagged-Item write path stays functional and unchanged (both paths feed the same
  `sendLimitWriteToAllPartners`).

## Consequences

- Commands issued before a partner is bound are only remembered, not sent.
- Not yet covered: reading the peer's reported status back onto the Energy Guard Channels.
