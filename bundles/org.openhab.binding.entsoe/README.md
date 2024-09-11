# EntsoE Binding

This binding connects to EntsoE transparency platform to retrieve day ahead prices of the pan-European electricity market. The service is free but requires you to register for api-key.

Main site
https://www.entsoe.eu/

Go here for registration and api-key 
https://transparency.entsoe.eu/

## Supported Things

This binding supports only one ThingType: dayAheadPrices

- `dayAheadPrices`: Retrieve prices for next 24h hours

## Discovery

This binding supports auto-discovery based on your configured location. It uses https://www.openstreetmap.org/ to get your country based on your coordinates.

## Binding Configuration

There's no special binding configuration

## Thing Configuration


Security token - Your EntsoE api-key 
Area - Select your location where you want prices

## Channels

Binding has 24 channel groups that each have DateTime-type channel and number channel.

DateTime channel holds the time in even hours when that channel groups price is valid
Number channel holds that hours price in €/MWh.

## Full Example

```
DateTime EntsoE_PriceHours01_Time  "Aikaleima +01h"                    <price>     (GrElectricPrices) { channel="entsoe:dayAheadPrices:finland:priceHours01#time" }
Number   EntsoE_PriceHours01_Price "Sähkön hinta +01h [%.2f €/MWh]"  <price>     (GrElectricPrices) { channel="entsoe:dayAheadPrices:finland:priceHours01#price" }

Number EntsoE_PriceHours02_Time    "Aikaleima +2h"                     <price>     (GrElectricPrices) { channel="entsoe:dayAheadPrices:finland:priceHours02#price" }
Number EntsoE_PriceHours02_Price   "Sähkön hinta +2h [%.2f €/MWh]"     <price>     (GrElectricPrices) { channel="entsoe:dayAheadPrices:finland:priceHours02#price" }
...

```

## Any custom content here!

For discovery this binding uses OpenStreetMap
https://nominatim.openstreetmap.org/
https://www.openstreetmap.org/copyright
