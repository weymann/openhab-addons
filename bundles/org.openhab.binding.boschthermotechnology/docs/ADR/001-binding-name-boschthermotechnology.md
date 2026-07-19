# ADR-001: Binding Name `boschthermotechnology`

## Status

> Accepted

## Context

The binding was scaffolded under the working name `boschoidc`, which describes the authentication mechanism (OpenID Connect) rather than the product or protocol. During the `$Concept` review (see `docs/CONCEPT.md`), we compared this against community search behavior: years of openHAB community requests for Buderus/Bosch cloud integration consistently use product/brand terms (`Buderus`, `Bosch`, `MyBuderus`, `PointT`), not the auth mechanism.

The binding also needs to be clearly distinguished from the existing `boschshc` binding (Bosch Smart Home Controller — a different product line and API) and from `km200` (the existing binding for the older local KM50/KM100/KM200 gateways, which this binding does not replace or extend).

## Decision

We will rename the binding to `boschthermotechnology`, anchored on the API's own domain name (`pointt-api.bosch-thermotechnology.com`). This name:

- covers Bosch, Buderus, and Junkers uniformly, since all three brands run on the same PointT backend
- avoids confusion with `boschshc`
- does not lock in a single brand name that could be wrong for some hardware variants

## Consequences

### Positive

- Discoverable name that matches the underlying API rather than an implementation detail.
- Room to support additional Bosch Thermotechnology brands under the same binding without a future rename.

### Negative

- Requires a full rename of artifact ID, Java package, class names, and OSGi component PID before further code is written (completed as part of this ADR — see `pom.xml`, `addon.xml`, `thing-types.xml`, `feature.xml`, and `src/main/java/org/openhab/binding/boschthermotechnology/`).
- The `src/main/resources/OH-INF/i18n/boschoidc.properties` placeholder file was intentionally left untouched — per project rules, i18n files are only ever created/modified via `mvn i18n:generate-default-translations`, never by hand. It must be regenerated under the new binding ID during the next `$Release` pass.
- The leftover Java files under the old `org.openhab.binding.boschoidc` package must be removed manually (see follow-up note) before the module will build cleanly, since automated deletion tooling could not reach them in this session.
