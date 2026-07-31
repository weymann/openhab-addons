# ADR-016: Remove the `lpp-controllablesystem` pom.xml Dependency

## Status

> Accepted (2026-08-21)

## Context

`mvn clean install` fails at the `karaf-maven-plugin:verify` (`karaf-feature-verification`) goal:

```text
missing requirement [org.openhab.binding.eebus/5.3.0.202608212001] osgi.wiring.package;
filter:="(osgi.wiring.package=org.openmuc.jeebus.usecase.powerlimitation.controllablesystem)"
```

`pom.xml` declares a direct dependency:

```xml
<dependency>
  <groupId>org.openmuc.jeebus.usecase.powerlimitation</groupId>
  <artifactId>lpp-controllablesystem</artifactId>
  <version>1.0.0</version>
</dependency>
```

This is auto-expanded into `src/main/history/dependencies.xml` (the generated Karaf feature
consumed by `karaf-feature-verification`) as its own standalone
`mvn:org.openmuc.jeebus.usecase.powerlimitation/lpp-controllablesystem/1.0.0` bundle line,
alongside `ship`/`spine` themselves. Unlike `ship`/`spine` (which resolve cleanly), this
artifact's own OSGi manifest declares an `Import-Package` for exactly its own primary package
(`org.openmuc.jeebus.usecase.powerlimitation.controllablesystem`) that nothing - including the
artifact itself - exports. That is the shape of an incomplete or broken stub bundle, not a
correctly published library.

Evidence that this dependency is unused and out of process:

- No source file anywhere under `org.openhab.binding.eebus/src` references this package or any
  `LppControllableSystem`-style class (checked via full-tree search).
- No matching module exists in the connected `jeebus.spine` repository - its only Gradle modules
  are `demo`, `spine`, and `spine-test-utilities`; there is no per-use-case module structure at
  all - nor in `jeebus.ship`.
- No entry in `docs/changes/` (`decouple-oh-peer-config-from-pairing`,
  `dynamic-client-role-channels`, `eebus-network-discovery`, `lpc-lpp-client-role-channels`,
  `mdns-discovery-retry`, `separate-real-and-oh-peer-things`) or in `docs/ADR/` mentions this
  dependency or any plan to split the LPP Controllable System use case into a separate published
  library.
- `org.openhab.binding.eebus` is a subfolder-only checkout in the environment this was diagnosed
  from (no `.git` reachable), so the commit that introduced this line - and its rationale, if any
  - could not be inspected as part of this analysis.

Only 3 of the ~43 EEBUS catalogue use cases are implemented today (MPC, LPC, LPP - see
`CONCEPT.md` §7), all coded directly inside this binding rather than pulled in as separate
per-use-case libraries. A dependency on an external `lpp-controllablesystem` artifact would be a
new architectural pattern for this binding and, if intentional, should be its own change proposal
under `docs/changes/` with a working, correctly-built artifact behind it - not a silent pom.xml
addition with no consuming code.

## Decision

Remove the `org.openmuc.jeebus.usecase.powerlimitation:lpp-controllablesystem:1.0.0` dependency
from `pom.xml`. If a real "LPP Controllable System" library is intended in the future, it should
be reintroduced through the normal spec-driven workflow (`$Spec` → `$Architect`), with the actual
artifact built and its OSGi manifest verified before the dependency is added back.

## Consequences

### Positive

- `mvn clean install` passes `karaf-feature-verification` again.
- Removes a dependency with no consuming code and no tracked rationale, keeping `pom.xml`
  auditable against what the binding actually uses.

### Negative

- If this dependency actually was scaffolding for planned, not-yet-visible work (for example a
  library being developed outside the folders available for this analysis), removing it discards
  that scaffolding; it would need to be re-added deliberately once the real artifact exists and
  resolves correctly.

---

_Removes the `org.openmuc.jeebus.usecase.powerlimitation:lpp-controllablesystem:1.0.0`
`<dependency>` block from `pom.xml`. No other file changes._
