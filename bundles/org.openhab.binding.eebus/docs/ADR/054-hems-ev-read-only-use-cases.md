# ADR-054: HEMS Phase 2a - read-only EV use cases (EVSECC, EVCC, EVCEM) for the wallbox

## Status

Proposed (implemented, not yet compiled or live-tested)

## Context

ADR-053 gives the HEMS Thing a Controllable System, Energy Guards and a Monitoring entity.
Phase 2 adds the EV use cases so the HEMS also sees the wallbox and the connected vehicle.
Phase 2 is split: this ADR covers the three read-only use cases; OPEV (writes a charging
current limit, needs measurement input) follows in Phase 2b.

In all three use cases the HEMS plays the `CEM` actor (client); the wallbox side is the server:

| Use case | Wire name | Remote actor | Remote entity |
|---|---|---|---|
| EVSECC | `evseCommissioningAndConfiguration` | `EVSE` | EVSE |
| EVCC | `evCommissioningAndConfiguration` | `EV` | EV |
| EVCEM | `measurementOfElectricityDuringEvCharging` | `EV` | EV |

The wire names and actor strings were taken from the evcc discovery dump in `src/test/resources`
(the TS titles differ for EVCEM).

## Decision

1. Three Client use cases extend one base class `AbstractEEBusEvClientUseCase`. They are added to
   the Monitoring `CEM` entity of the HEMS Thing. With a blank `wallboxSki` they match no partner.
1. Like the scoped Energy Guards (ADR-053) each instance reacts only to the partner whose SKI
   equals `wallboxSki`.
1. Values go to dynamically created, read-only Channels in three groups:

| Group | Use case | Channels |
|---|---|---|
| `wallbox-evse` | EVSECC | `connected`, `device-name`, `vendor-name`, `brand-name`, `serial-number`, `software-revision`, `hardware-revision`, `manufacturer-label`, `operating-state`, `last-error-code` |
| `wallbox-ev` | EVCC | `connected`, `communication-standard`, `asymmetric-charging`, `identification`, `identification-type`, manufacturer data as above, `operating-state` |
| `wallbox-evcem` | EVCEM | `power`, `energy-charged`, `current-phase-a`, `current-phase-b`, `current-phase-c` |

1. EVCEM maps current measurements to phases through the ElectricalConnection parameter
   description (`acMeasuredPhases`); a current entry without a phase mapping is ignored.
1. EVCC scenario 7 (sleep mode) is visible as the `operating-state` value of the EV.

## Consequences

- No new Thing, no new config parameter. Setting `wallboxSki` now also binds the EV groups.
- Channels appear only after the partner is detected and delivered a value.

## Known limitations

- **EV disconnected (EVCC scenario 8) is not reliably detected.** jeebus.spine calls the use case
  listener only for a non-empty partner list. `connected` turns `OFF` only if a later, non-empty
  list no longer contains the EV; an empty list gives no callback.
- EVCC scenario 6 (charging power limits) is not implemented; it comes with OPEV in Phase 2b.
- Feature requirements are not enforced (matching by use case name and actor only), so devices
  that omit optional scenarios are still found.
- Not compiled and not tested against a real wallbox. The evcc and Porsche/Spelsberg dumps can
  serve as test data.
