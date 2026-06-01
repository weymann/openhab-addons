# BrightSky Binding

The BrightSky Binding integrates weather data from the [BrightSky API](https://brightsky.dev), a free and open service that provides real-time observations and forecasts sourced from the German Weather Service (Deutscher Wetterdienst, DWD).
No API key or account is required.
Data coverage is limited to **Germany**.

## Supported Things

| Thing Type ID      | Description                                                                 |
|--------------------|-----------------------------------------------------------------------------|
| `weather-location` | Represents a geographic location and provides current weather observations. |

## Discovery

Automatic discovery is not supported.
Things must be added manually via the UI or a `.things` file.

## Thing Configuration

### `weather-location` Thing Configuration

| Name              | Type    | Default | Required | Description                                                                                   |
|-------------------|---------|---------|----------|-----------------------------------------------------------------------------------------------|
| `latitude`        | decimal | —       | yes\*    | Geographic latitude of the location (-90 to 90). \*Required unless `stationId` is set.        |
| `longitude`       | decimal | —       | yes\*    | Geographic longitude of the location (-180 to 180). \*Required unless `stationId` is set.     |
| `stationId`       | text    | —       | no       | DWD station ID (e.g. `00433` for Berlin-Tempelhof). When set, overrides `latitude`/`longitude`. |
| `refreshInterval` | integer | 30      | no       | How often to poll the BrightSky API for updated observations, in minutes. Minimum: 5.         |

## Channels

All channels belong to the channel group `current`, which provides the latest observation from the nearest DWD ground station.

| Channel ID          | Type               | Description                                                         |
|---------------------|--------------------|---------------------------------------------------------------------|
| `temperature`       | Number:Temperature | Air temperature at 2 m above ground.                               |
| `dew-point`         | Number:Temperature | Dew point temperature.                                              |
| `humidity`          | Number:Dimensionless | Relative humidity.                                                |
| `pressure`          | Number:Pressure    | Mean sea level pressure.                                            |
| `wind-speed`        | Number:Speed       | Average wind speed over the last 10 minutes.                        |
| `wind-direction`    | Number:Angle       | Average wind direction over the last 10 minutes (meteorological convention: 0° = north). |
| `wind-gust-speed`   | Number:Speed       | Maximum wind gust speed in the last 10 minutes.                     |
| `wind-gust-direction` | Number:Angle     | Direction of the strongest wind gust in the last 10 minutes.        |
| `precipitation`     | Number:Length      | Total precipitation in the last 10 minutes.                         |
| `cloud-cover`       | Number:Dimensionless | Fraction of the sky covered by clouds.                            |
| `visibility`        | Number:Length      | Horizontal visibility.                                              |
| `sunshine`          | Number:Time        | Total sunshine duration in the last 30 minutes.                     |
| `solar`             | Number:Energy      | Solar energy received per square metre in the last 30 minutes (kWh/m²). |
| `condition`         | String             | Current weather condition (`dry`, `fog`, `rain`, `sleet`, `snow`, `hail`, `thunderstorm`). |
| `icon`              | String             | Weather icon key (e.g. `cloudy`, `sunny`, `partly-cloudy-day`).     |
| `observation-time`  | DateTime           | Timestamp of the most recent observation.                           |

All channels are read-only.
The channel full ID is `current#<channel-id>`, for example `current#temperature`.

## Full Example

### Thing Configuration

```java
Thing brightsky:weather-location:berlin [
    latitude=52.52,
    longitude=13.41,
    refreshInterval=30
]
```

### Item Configuration

```java
Number:Temperature  Berlin_Temperature        "Temperature [%.1f %unit%]"  { channel="brightsky:weather-location:berlin:current#temperature" }
Number:Temperature  Berlin_DewPoint           "Dew Point [%.1f %unit%]"    { channel="brightsky:weather-location:berlin:current#dew-point" }
Number:Dimensionless Berlin_Humidity          "Humidity [%.0f %unit%]"     { channel="brightsky:weather-location:berlin:current#humidity" }
Number:Pressure     Berlin_Pressure           "Pressure [%.1f %unit%]"     { channel="brightsky:weather-location:berlin:current#pressure" }
Number:Speed        Berlin_WindSpeed          "Wind Speed [%.1f %unit%]"   { channel="brightsky:weather-location:berlin:current#wind-speed" }
Number:Angle        Berlin_WindDirection      "Wind Direction [%.0f %unit%]" { channel="brightsky:weather-location:berlin:current#wind-direction" }
Number:Speed        Berlin_WindGustSpeed      "Gust Speed [%.1f %unit%]"   { channel="brightsky:weather-location:berlin:current#wind-gust-speed" }
Number:Angle        Berlin_WindGustDirection  "Gust Direction [%.0f %unit%]" { channel="brightsky:weather-location:berlin:current#wind-gust-direction" }
Number:Length       Berlin_Precipitation      "Precipitation [%.1f %unit%]" { channel="brightsky:weather-location:berlin:current#precipitation" }
Number:Dimensionless Berlin_CloudCover        "Cloud Cover [%.0f %unit%]"  { channel="brightsky:weather-location:berlin:current#cloud-cover" }
Number:Length       Berlin_Visibility         "Visibility [%d %unit%]"     { channel="brightsky:weather-location:berlin:current#visibility" }
Number:Time         Berlin_Sunshine           "Sunshine [%.0f %unit%]"     { channel="brightsky:weather-location:berlin:current#sunshine" }
Number:Energy       Berlin_Solar              "Solar [%.3f %unit%]"        { channel="brightsky:weather-location:berlin:current#solar" }
String              Berlin_Condition          "Condition [%s]"             { channel="brightsky:weather-location:berlin:current#condition" }
String              Berlin_Icon               "Icon [%s]"                  { channel="brightsky:weather-location:berlin:current#icon" }
DateTime            Berlin_ObservationTime    "Last Updated [%1$tH:%1$tM]" { channel="brightsky:weather-location:berlin:current#observation-time" }
```

### Sitemap Configuration

```perl
Text item=Berlin_Temperature label="Temperature"
Text item=Berlin_Humidity     label="Humidity"
Text item=Berlin_Condition    label="Condition"
Text item=Berlin_WindSpeed    label="Wind Speed"
Text item=Berlin_Precipitation label="Precipitation (10 min)"
```

## Notes

The BrightSky API uses data from official DWD ground stations.
The nearest station is automatically selected based on the configured coordinates.
The station name and its distance are logged at `INFO` level on the first successful poll — check the log to verify which station is providing data for your location.

In rural areas the nearest station may be 20–40 km away.
For the highest accuracy, configure a specific DWD station ID using the `stationId` parameter.
A list of available stations can be found at [brightsky.dev](https://brightsky.dev/docs/#/operations/getSources).
