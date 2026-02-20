# EnergyChartsInfo Binding

Binding provides energy information & pricing from [Frauenhofer Institue for Solar Energysystems ISE](https://www.energy-charts.info/).
Pricing forecast is provided by [Energy Forecast Provider](https://www.energyforecast.de/).
 
## Binding Configuration

### `energychartsinfo` Thing Configuration

Thing provides data from two different services which are both relying on [Energy Charts Info API](https://api.energy-charts.info/).
Licensing [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) by [Bundesnetzagentur](https://www.smard.de/home).

Check for correct configuration

- available [bidding zones](https://api.energy-charts.info/#/prices/day_ahead_price_price_get) for day-ahead pricing
- available [countries](https://api.energy-charts.info/) for energy information

| Name          | Type    | Description                             | Default | Required |
|---------------|---------|-----------------------------------------|---------|----------|
| zone          | text    | Bidding zone for price queries          | N/A     | yes      |
| country       | text    | Password to access the device           | N/A     | yes      |

[Energy Forecast](https://www.energyforecast.de/) service which is providing a forecast beyond day-ahead spot pricing.
Access to forecast pricing requires [Registration](https://www.energyforecast.de/users/sign_up).
If no token is configured the `forecast` channel will stay empty.

Forecast doesn't serve all bidding zones.
Check [API](https://www.energyforecast.de/api-docs/index.html) if your bidding zone is present.

| Name          | Type      | Description                                                                       | Default   | Required |
|---------------|-----------|-----------------------------------------------------------------------------------|-----------|----------|
| token         | text      | Token for energy forecast service to provide forecast data                        | N/A       | no       |
| fixCost       | decimal   | Fix costs in ct/kWh which will be added on top of the forecast price, e.g. 15,3   | 0         | no       |
| vat           | decimal   | VAT in percent which will be added on top of the forecast price, e.g. 19,0        | 0         | no       |

## Channels

All channels delivering forecast information. 
Attaching items which are bound only to `rrd4j` persistence will not work.
If you don't have a database installed [InMemory persistence](https://www.openhab.org/addons/persistence/inmemory/) can be used.

### Group `price`

| Channel       | Type                  | Description                               |
|---------------|-----------------------|-------------------------------------------|
| day-ahead     | Number:EnergyPrice    | Day-Ahead energy price                    |
| forecast      | Number:EnergyPrice    | Forecast energy price                     |

`forecast` only delivers data if token for energy forecast service.
Depending on your booked tariff you'll receive values for the next 48 or 96 hours.

### Group `renewables`

| Channel           | Type                  | Description                               |
|-------------------|-----------------------|-------------------------------------------|
| total             | Number:Dimensionless  | Production share of all renewables        |
| solar             | Number:Dimensionless  | Production share of solar                 |
| wind-onshore      | Number:Dimensionless  | Production share of wind onshore          |
| wind-offshore     | Number:Dimensionless  | Production share of wind offshore         |

## Full Example

