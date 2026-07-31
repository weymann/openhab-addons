# Delta for Thing Lifecycle

## ADDED Requirements

### Requirement: Per-Thing serialized SHIP session start

The binding SHALL ensure that, for a given `eebus:oh-device`/`eebus:oh-cs-device` Bridge Thing, no
two concurrent attempts to start its local SHIP/SPINE session (keystore access, port bind,
`Device` build) execute at the same time, regardless of how many overlapping
`initialize()`/`dispose()` generations exist for that Thing.

#### Scenario: A stale generation queued behind a running attempt exits without touching shared resources

- GIVEN a start attempt for generation N of a Thing is currently in progress
- WHEN a newer generation N+2 for the same Thing is superseded before generation N's attempt
  finishes
- THEN generation N+2's own start attempt, once it is its turn to run, MUST detect the
  supersession and return without ever accessing the keystore file or the port pool

#### Scenario: Two overlapping generations never open the same keystore file at once

- GIVEN generation N's start attempt is currently accessing this Thing's SHIP keystore file
- WHEN generation N+1's (or later) start attempt becomes due to run for the same Thing
- THEN it MUST wait until generation N's attempt has finished before it may access the same
  keystore file

### Requirement: Debounced Entity-change rebuild

The binding SHALL coalesce a burst of near-simultaneous
`EEBusEntityChangeListener#onEntityChanged()` notifications for one Bridge into a single
`dispose()`/`initialize()` rebuild, rather than running one rebuild per notification.

#### Scenario: Two child Entity changes in quick succession produce one rebuild

- GIVEN a Bridge has just received an `onEntityChanged()` notification and the resulting rebuild
  has not yet started
- WHEN a second `onEntityChanged()` notification for the same Bridge arrives before the debounce
  window elapses
- THEN only one `dispose()`/`initialize()` rebuild MUST run, reflecting the state after both
  notifications

#### Scenario: A pending debounced rebuild is cancelled if the Bridge is disposed for another reason

- GIVEN a debounced rebuild is scheduled but has not yet run
- WHEN `dispose()` runs for a reason other than that scheduled rebuild (e.g. the Bridge is
  disabled or its own configuration changed)
- THEN the pending scheduled rebuild MUST be cancelled and MUST NOT call `initialize()`
  afterward
