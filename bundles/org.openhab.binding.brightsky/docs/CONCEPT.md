# BrightSky Binding — Concept Document

**Role:** $Concept (Product Strategist)
**Date:** 2026-05-30
**Status:** Draft — open for $Architect review

---

## One-Sentence Summary

The BrightSky binding gives openHAB users real-time and hourly weather observations for any location in Germany, sourced from the official DWD (Deutscher Wetterdienst) data via the free, no-auth BrightSky API.

---

## What Problem Does This Solve?

openHAB users in Germany who want local, high-quality weather data currently rely on generic international services (e.g. OpenWeatherMap, meteoblue). Those require API keys, have rate limits, and use interpolated/forecast-only data. BrightSky proxies actual DWD ground station measurements — the most accurate source for Germany — and is completely free with no authentication required.

**Who benefits:** German openHAB users who want to:

- Trigger automations based on rain, wind gusts, or temperature (e.g. close roller shutters, irrigate garden).
- Display current conditions on a wall panel or dashboard.
- Log historical weather alongside energy or sensor data.

---

## BrightSky API Overview

Base URL: `https://api.brightsky.dev`

| Endpoint | What it returns | Poll strategy |
|---|---|---|
| `GET /current_weather` | Latest observation from nearest DWD station | Every 10–30 min |
| `GET /weather` | Hourly historical or recent observations (date range) | On demand / scheduled |
| `GET /forecast` | MOSMIX hourly forecast up to ~10 days | Every 1–6 h |
| `GET /alerts` | Active DWD weather warnings for a warn cell | Every 15–30 min |

**No API key required.** Rate limiting is courteous-use only. Data coverage: Germany only.

### Key fields from `/current_weather`

- `temperature` (°C), `dew_point` (°C), `relative_humidity` (%)
- `pressure_msl` (hPa)
- `wind_speed_10/30/60` (km/h), `wind_direction_10/30/60` (°), `wind_gust_speed_10/30/60` (km/h)
- `precipitation_10/30/60` (mm)
- `cloud_cover` (%), `visibility` (m)
- `sunshine_30/60` (min), `solar_10/30/60` (kWh/m²)
- `condition` (string: "dry", "rain", "snow", …), `icon` (string)

---

## Proposed Thing Model

### Thing: `weather-location`

Represents a geographic location for which weather is polled. The user configures latitude/longitude (or a DWD station ID as an alternative).

**Configuration parameters:**

| Parameter | Type | Required | Default | Notes |
|---|---|---|---|---|
| `latitude` | Decimal | yes* | — | *Required if no `stationId` |
| `longitude` | Decimal | yes* | — | *Required if no `stationId` |
| `stationId` | String | no | — | DWD station ID; overrides lat/lon |
| `refreshInterval` | Integer | no | 30 | Minutes between current-weather polls |
| `forecastRefreshInterval` | Integer | no | 60 | Minutes between forecast polls |

> **Smart default rationale:** DWD synop data is published every 10–30 minutes. A 30-minute default avoids hammering the API while staying reasonably fresh.

### Channel Groups

#### `current` — Current Conditions (from `/current_weather`)

| Channel | Item Type | Unit | Description |
|---|---|---|---|
| `temperature` | Number:Temperature | °C | Air temperature |
| `dew-point` | Number:Temperature | °C | Dew point |
| `humidity` | Number:Dimensionless | % | Relative humidity |
| `pressure` | Number:Pressure | hPa | Mean sea level pressure |
| `wind-speed` | Number:Speed | km/h | Wind speed (10-min avg) |
| `wind-direction` | Number:Angle | ° | Wind direction (10-min avg) |
| `wind-gust-speed` | Number:Speed | km/h | Wind gust speed (10-min) |
| `wind-gust-direction` | Number:Angle | ° | Wind gust direction (10-min) |
| `precipitation` | Number:Length | mm | Precipitation last 10 min |
| `cloud-cover` | Number:Dimensionless | % | Cloud cover |
| `visibility` | Number:Length | m | Visibility |
| `sunshine` | Number:Time | min | Sunshine last 30 min |
| `solar` | Number:Intensity | kWh/m² | Solar irradiance last 30 min |
| `condition` | String | — | Condition label ("rain", "dry", …) |
| `icon` | String | — | Icon key ("cloudy", "sunny", …) |
| `observation-time` | DateTime | — | Timestamp of the observation |

#### `forecast-hourly` — Hourly Forecast (from `/forecast`, repeating channel group)

Same channels as `current` plus `precipitation-probability` (%).
Up to 240 entries (10 days); practical UI use: next 24 h.

#### `alerts` — Weather Warnings (from `/alerts`)

| Channel | Item Type | Description |
|---|---|---|
| `alert-active` | Switch | ON if any warning is active |
| `alert-count` | Number | Number of active warnings |
| `alert-headline` | String | Headline of highest-severity warning |
| `alert-severity` | String | "minor", "moderate", "severe", "extreme" |
| `alert-event` | String | Event type (e.g. "STORM", "HEAVY_RAIN") |

---

## MVP Scope (Recommended First Release)

**Include:**

- `weather-location` Thing with `current` channel group only.
- Configurable lat/lon; station ID lookup as optional override.
- Configurable `refreshInterval`.
- All channels from `/current_weather` listed above.

**Defer to v2:**

- Forecast channel group (requires repeating group or separate Thing type).
- Alerts channels (requires additional polling loop and i18n for German warning texts).
- Historical query support.

**Rationale:** Current weather satisfies the primary automation use case (react to rain, wind, temperature). Forecast and alerts add complexity and edge cases (empty alerts array, large forecast payloads) that can be validated separately.

---

## Comparison with Existing Bindings

| Binding | Coverage | Auth | Data source | Accuracy (DE) |
|---|---|---|---|---|
| OpenWeatherMap | Global | API key required | Model + stations | Moderate |
| meteoblue | Global | Paid tiers | Model | Moderate |
| **BrightSky (proposed)** | Germany only | None | DWD ground stations | High |

**What BrightSky does better:** Zero setup friction (no key), official government measurement data, 10-minute granularity for current conditions.

**Limitation:** Germany only — not suitable as a general-purpose weather binding.

---

## Pros and Cons

### Pros

- No API key — zero onboarding friction for users.
- Highest data quality for Germany (official DWD measurements).
- Free, no rate-limit tiers to manage.
- Rich current-weather payload (multi-interval wind/precipitation/solar).
- Alerts endpoint enables safety-critical automations.

### Cons

- **Germany only** — limited audience.
- No push; polling required.
- Station proximity varies — rural areas may get readings from stations 20–40 km away.
- Forecast endpoint returned empty in testing (may require additional parameters or be temporarily unavailable).
- `condition` and `icon` are strings, not enums — may change over API versions.

---

## Open Questions / Decisions for $Architect

1. **Repeating channel groups vs. separate Things for forecast:** openHAB supports repeating channel groups but UI support is limited. Alternative: a separate `forecast-day` Thing per hour offset. Which pattern fits better with current openHAB 4.x conventions?
1. **Station ID resolution:** Should the binding auto-discover the nearest station on initialization (via `/sources` endpoint) and log it, or silently use whatever BrightSky returns? Surfacing the resolved station name would help users trust the data.
1. **`condition` channel type:** Model as a plain `String` item or define a custom `ConditionType` with `StateDescription` options? The latter enables rule autocompletion but requires maintaining the enum.
1. **Alerts i18n:** DWD warning texts are in German. Does the binding expose raw German strings, or should we attempt translation? Recommendation: expose raw + a `alert-severity` channel for language-neutral automation.
1. **Unit system:** BrightSky returns SI units (km/h, °C, hPa, mm). openHAB's UoM handles conversion. Should we declare canonical units in the channel definitions or leave it to item configuration?

---

## Recommendation

Build the MVP around the `current` channel group only. The binding delivers clear, immediate value with minimal complexity: no auth, no forecast parsing, no alert edge cases. Once community feedback confirms the Thing model feels natural, add forecast and alerts in a follow-up iteration.

**Next step:** Pass to $Architect to design the Java package structure, HTTP client strategy, and channel type definitions.
