# ADR-003: `dhw-operation-mode` Channel Uses Number, Not String

## Status

> Accepted

## Context

The PointT API reports and expects the DHW operation mode at `/dhwCircuits/dhw1/operationMode` as one of five String values: `Off`, `Eco+`, `Eco`, `Comfort`, `Auto` (confirmed against a real gateway capture, see the `buderus-main` project's README). The channel was originally implemented as a `String` item type with a matching `option` list of the same five String values, which is the most direct mapping onto the API.

The binding owner requested that the channel instead be declared as `Number`, with `option` values linking numeric codes to the same human-readable labels.

## Decision

`dhw-operation-mode` is declared as item type `Number` in `thing-types.xml`, with options `0`=Off, `1`=Eco+, `2`=Eco, `3`=Comfort, `4`=Auto. These codes are a binding-internal convention only — the PointT API itself is unaware of them. `GatewayHandler` maintains a bidirectional mapping (`DHW_OPERATION_MODE_TO_API_VALUE` / `DHW_OPERATION_MODE_TO_CODE`) and translates in both directions: API String -> channel Number when polling, channel Number -> API String when handling a command.

## Consequences

### Positive

- Matches the binding owner's preferred convention for enum-like channels.
- Number channels work naturally with Rules DSL numeric comparisons (`if (DhwOperationMode.state == 2)`) without String equality/pattern matching.

### Negative

- Adds a translation layer (two `Map`s plus mapping logic in `GatewayHandler`) that has no counterpart in the API itself — a new PointT-reported value not present in the map is now reported as `UNDEF` on read and rejected on write, whereas the previous String channel would have passed an unrecognized value through unchanged.
- Breaking change for anyone who already linked an Item to this channel: a `String` Item bound to `dhw-operation-mode` should be changed to `Number`, and any Sitemap `Selection`/`Switch` mappings or rules referencing the old String values must be updated to the numeric codes. This binding has not been released yet, so no external users are affected.
- The numeric codes are not part of any Bosch/Buderus specification; they are this binding's own convention and must be kept in sync by hand between `thing-types.xml` and `GatewayHandler.DHW_OPERATION_MODE_TO_API_VALUE`.
