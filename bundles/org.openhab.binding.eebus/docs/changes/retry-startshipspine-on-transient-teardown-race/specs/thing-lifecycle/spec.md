# Delta for Thing Lifecycle

## ADDED Requirements

### Requirement: Bounded retry on transient SHIP start failures

The binding SHALL retry a Thing's SHIP session start a bounded number of times, with a short
backoff between attempts, when the attempt fails with `java.net.BindException` or
`java.nio.channels.OverlappingFileLockException` - the two exception types confirmed to result
from the previous generation's SHIP-layer teardown not yet having released the port/keystore.

#### Scenario: A transient bind failure succeeds on retry

- GIVEN a Thing's start attempt fails with `BindException` because the previous generation's
  SHIP server on the same port has not yet fully stopped
- WHEN fewer than the maximum number of attempts have been made
- THEN the binding MUST wait a short backoff and retry the same start attempt, succeeding once
  the previous generation's teardown has actually released the port

#### Scenario: A superseded generation stops retrying immediately

- GIVEN a Thing's start attempt is retrying after a transient failure
- WHEN a newer generation has superseded this one before the retry runs
- THEN the binding MUST NOT perform a further retry for the superseded generation

#### Scenario: A non-transient failure is not retried

- GIVEN a Thing's start attempt fails with an exception other than `BindException` or
  `OverlappingFileLockException`
- WHEN the failure occurs
- THEN the binding MUST NOT retry and MUST report the failure exactly as before this change
