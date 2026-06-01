# ADR-003: Gson for JSON Deserialization

## Status

`Accepted`

## Context

The BrightSky API returns JSON. openHAB core provides two JSON libraries on the OSGi classpath:

- **Gson** (`com.google.gson`) — provided by openHAB core
- **Jackson** — available in some openHAB configurations but not guaranteed

A third option is manual JSON parsing (e.g. `org.json`), which is not on the openHAB core classpath.

The BrightSky response structure is flat and predictable. The `/current_weather` response has one top-level `weather` object and a `sources` array — no polymorphism, no complex nesting.

Field naming in the API uses `snake_case` (e.g. `wind_speed_10`); Java convention is `camelCase`.

## Decision

We will use **Gson** with a `FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES` configuration to map `snake_case` API fields to `camelCase` Java fields automatically. No custom type adapters are needed for the MVP.

```java
private static final Gson GSON = new GsonBuilder()
    .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
    .create();
```

DTO classes will use `@SerializedName` only for fields that deviate from the policy (e.g. none expected in MVP).

`null` fields in the JSON (BrightSky uses JSON `null` for unavailable measurements) will map to Java `null` automatically — the handler checks for null before constructing `QuantityType` and falls back to `UnDefType.UNDEF`.

No additional `pom.xml` dependency needed — Gson is already on the openHAB core classpath.

## Consequences

### Positive

- Zero new dependencies.
- Automatic `snake_case` → `camelCase` mapping reduces boilerplate.
- Well-understood within the openHAB binding community.

### Negative

- Gson does not validate required fields — a missing field silently becomes `null`. The handler's null checks must be thorough.
- If BrightSky adds fields with naming patterns that break `LOWER_CASE_WITH_UNDERSCORES` (e.g. numeric suffixes like `wind_speed_10` → `windSpeed10`), `@SerializedName` annotations must be added. This is already the case for the multi-interval fields; the DTO field names will use `windSpeed10`, `windSpeed30`, `windSpeed60` with the policy producing a match since Gson treats the digit as part of the word.
