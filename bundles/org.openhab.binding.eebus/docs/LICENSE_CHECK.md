# License Check: EEBus Binding Dependencies

## Purpose

This document tracks the license compliance check for the external libraries the
`org.openhab.binding.eebus` add-on depends on, so the declared license of each dependency
can be verified against its authoritative Maven repository record at any time.

## Scope

Direct compile-time dependencies declared in `pom.xml`:

```xml
<dependency>
  <groupId>org.openmuc.jeebus</groupId>
  <artifactId>spine</artifactId>
  <version>4.0.1</version>
</dependency>
<dependency>
  <groupId>org.openmuc.jeebus</groupId>
  <artifactId>ship</artifactId>
  <version>2.2.0</version>
</dependency>
<dependency>
  <groupId>com.fasterxml.jackson.module</groupId>
  <artifactId>jackson-module-jakarta-xmlbind-annotations</artifactId>
  <version>2.21.0</version>
  <type>jar</type>
</dependency>
<!-- ...plus 24 more transitive runtime dependencies of spine/ship, declared
     directly for the same reason (excludeTransitive=true in the reactor's
     embedding step — see ADR-001). Full list: ADR-001 and the table below. -->
```

Beyond `ship`/`spine` themselves, every other entry in `pom.xml` is a transitive
runtime dependency that had to be pinned as an explicit direct dependency to be
picked up by the reactor's embedding step (`excludeTransitive=true`) — see the
ADR-001 corrections dated 2026-07-31. Each is checked in the table below, not
separately here, since its license was already covered there.

## Checked Components

| Component     | Maven Coordinates              | Declared License          | License URL                                                            | Result |
|---------------|---------------------------------|----------------------------|--------------------------------------------------------------------------|--------|
| jEEBus.SHIP   | `org.openmuc.jeebus:ship:2.2.0` | Eclipse Public License 2.0 | <https://www.eclipse.org/legal/epl-2.0>                                  | OK     |
| jEEBus.SPINE  | `org.openmuc.jeebus:spine:4.0.1`| Eclipse Public License 2.0 | <https://www.eclipse.org/legal/epl-2.0>                                  | OK     |

Both licenses match the license used across the `org.openhab.binding.eebus` project itself
(EPL-2.0, see project `NOTICE` file) and are compatible with distribution through
openHAB add-ons.

## Maven Repository Links (traceability)

Use these links to re-verify the published license metadata at any time — the POM served by
Maven Central is the authoritative source, independent of what is checked into the
`jeebus.ship` / `jeebus.spine` source repositories.

### jEEBus.SHIP (`org.openmuc.jeebus:ship`)

| Link type | URL |
|-----------|-----|
| Maven Central (artifact overview) | <https://central.sonatype.com/artifact/org.openmuc.jeebus/ship/2.2.0> |
| Maven Central (directory listing) | <https://repo1.maven.org/maven2/org/openmuc/jeebus/ship/> |
| POM used for this check (version 2.2.0) | <https://repo1.maven.org/maven2/org/openmuc/jeebus/ship/2.2.0/ship-2.2.0.pom> |
| mvnrepository.com mirror | <https://mvnrepository.com/artifact/org.openmuc.jeebus/ship/2.2.0> |
| Source repository | <https://github.com/openmuc/jeebus.ship> |

### jEEBus.SPINE (`org.openmuc.jeebus:spine`)

| Link type | URL |
|-----------|-----|
| Maven Central (artifact overview) | <https://central.sonatype.com/artifact/org.openmuc.jeebus/spine/4.0.1> |
| Maven Central (directory listing) | <https://repo1.maven.org/maven2/org/openmuc/jeebus/spine/> |
| POM used for this check (version 4.0.1) | <https://repo1.maven.org/maven2/org/openmuc/jeebus/spine/4.0.1/spine-4.0.1.pom> |
| mvnrepository.com mirror | <https://mvnrepository.com/artifact/org.openmuc.jeebus/spine/4.0.1> |
| Project page | <https://www.openmuc.org/eebus/> |

## Verification Method

1. Resolved the exact versions consumed by the binding from `org.openhab.binding.eebus/pom.xml`
   (`ship:2.2.0`, `spine:4.0.1`).
1. Fetched the published POM for each version directly from Maven Central
   (`repo1.maven.org`) — this is the artifact actually resolved by a Maven/Tycho build, not
   just the source-repository declaration.
1. Confirmed the `<licenses>` block in both POMs declares `Eclipse Public License 2.0`
   (`https://www.eclipse.org/legal/epl-2.0`), matching the `LICENSE` file in the
   `jeebus.ship` / `jeebus.spine` source repositories.
1. Cross-checked against Maven Central's directory listing to confirm the consumed
   versions are actually published releases (not snapshots).

## Transitive Runtime Dependencies (Embedded)

As of ADR-001 (`docs/ADR/001-embed-third-party-dependencies.md`), `ship`, `spine`, and
their transitive runtime dependencies are embedded directly into the
`org.openhab.binding.eebus` bundle via the `openhab-addons` reactor's
`maven-dependency-plugin` `embed-dependencies` execution (`unpack-dependencies` goal,
`includeScope=runtime`) — not via `bnd.bnd`/`Embed-Dependency`, which was an earlier,
incorrect assumption corrected in ADR-001. Since these libraries now become part of the
distributed binding artifact, their licenses were checked the same way as `ship`/`spine`
themselves — against the POM published on Maven Central.

| Component | Maven Coordinates | Declared License | Result |
|-----------|--------------------|--------------------|--------|
| Gson | `com.google.code.gson:gson:2.13.2` | Apache-2.0 | OK |
| Error Prone Annotations (transitive of Gson) | `com.google.errorprone:error_prone_annotations:2.41.0` | Apache License, Version 2.0 | OK |
| jmDNS | `org.jmdns:jmdns:3.6.3` | Apache License, Version 2.0 | OK |
| FindBugs jsr305 | `com.google.code.findbugs:jsr305:3.0.2` | Apache License, Version 2.0 | OK |
| SLF4J API | `org.slf4j:slf4j-api:2.0.17` | MIT | OK — **not embedded**, kept on `Import-Package` (see ADR-001) |
| Netty (common, buffer, codec-base, codec-http, codec-compression, resolver, transport, transport-native-unix-common, handler) | `io.netty:*:4.2.10.Final` | Apache-2.0 | OK |
| BouncyCastle (bcprov/bcpkix/bcutil-jdk18on) | `org.bouncycastle:*:1.83` | Bouncy Castle Licence | ⚠️ Accepted as exception — OSI-approved, MIT-derivative permissive license, not literally on the standard approved list. See ADR-001. |
| Jackson (databind, core, annotations, jakarta-xmlbind-module) | `com.fasterxml.jackson.*:2.21.0` (jackson-annotations resolves to `2.21`, no patch suffix) | Apache License, Version 2.0 | OK |
| Jakarta XML Binding API | `jakarta.xml.bind:jakarta.xml.bind-api:4.0.5` | EDL 1.0 (SPDX: BSD-3-Clause) | OK |
| Jakarta Activation API | `jakarta.activation:jakarta.activation-api:2.1.0` | EDL 1.0 (SPDX: BSD-3-Clause) | OK |
| JAXB Plugins Runtime | `org.jvnet.jaxb:jaxb-plugins-runtime:4.0.12` | Not resolved in this check (POM license inherited several parent-POM levels deep) | ⚠️ Provisionally assumed EDL-1.0/BSD (consistent with the rest of the Eclipse EE4J JAXB stack it ships alongside). **Follow-up:** confirm with `mvn license:add-third-party` before next release. |
| Glassfish JAXB Core (transitive of `jaxb-plugins-runtime`) | `org.glassfish.jaxb:jaxb-core:4.0.6` | EDL 1.0 (SPDX: BSD-3-Clause) — confirmed via POM header | OK |
| TXW2 Runtime (transitive of `jaxb-core`) | `org.glassfish.jaxb:txw2:4.0.6` | EDL 1.0 (SPDX: BSD-3-Clause) — confirmed via POM header | OK |
| Angus Activation (transitive of `jaxb-core`) | `org.eclipse.angus:angus-activation:2.0.3` | EDL 1.0 (SPDX: BSD-3-Clause) — confirmed via POM header | OK |
| Istack Commons Runtime (transitive of `jaxb-core`) | `com.sun.istack:istack-commons-runtime:4.1.2` | Not independently confirmed — Maven Central POM fetch returned no content in this session | ⚠️ Same Oracle/Eclipse EE4J JAXB-RI family as `jaxb-core`/`txw2` (consistent copyright/header pattern observed on sibling artifacts); provisionally assumed EDL-1.0/BSD. **Follow-up:** confirm with `mvn license:add-third-party` before next release. |

## Notes / Follow-ups

- Confirm `org.jvnet.jaxb:jaxb-plugins-runtime` and `org.glassfish.jaxb:jaxb-core`
  licenses with `mvn license:add-third-party` once a build environment with full
  Maven Central access is available (see table above).
- `jeebus.spine` and `jeebus.ship` also expose an internal Fraunhofer GitLab package
  registry (`https://gitlab.cc-asp.fraunhofer.de/api/v4/groups/18477/-/packages/maven`) for
  development/snapshot builds. The binding itself only ever resolves the public Maven
  Central releases referenced above.
- `jeebus.ship` and `jeebus.spine` repositories are change-protected in this workspace
  (require prior human approval) — this check was performed read-only against those repos
  and against public Maven Central metadata; no files were modified in either repository.

## Change Log

| Date       | Change                                                                 |
|------------|-------------------------------------------------------------------------|
| 2026-07-31 | Initial check: confirmed EPL-2.0 for `ship:2.2.0` and `spine:4.0.1`, added Maven repository links for traceability. |
| 2026-07-31 | ADR-001: switched to embedding `ship`/`spine` and their transitive dependency graph. Checked licenses for all transitive dependencies; 2 exceptions flagged (BouncyCastle licence accepted, jaxb-plugins-runtime/jaxb-core provisionally accepted pending `mvn license:add-third-party` confirmation). |
| 2026-07-31 | ADR-001 correction: embedding mechanism is `maven-dependency-plugin` (`embed-dependencies`), not `bnd.bnd`/`Embed-Dependency`; updated "Scope" and "Transitive Runtime Dependencies" text accordingly. Added `jackson-module-jakarta-xmlbind-annotations:2.21.0` as an explicit direct dependency (`type=jar`) so it is embedded despite its own `packaging=bundle` POM metadata; no license change (already covered by the Jackson row above). |
| 2026-07-31 | ADR-001 correction: root cause was `excludeTransitive=true` on the reactor's embedding step, not packaging metadata — only directly-declared `pom.xml` dependencies are ever embedded. Ran `mvn dependency:tree -Dscope=runtime` and added the full transitive closure (24 further artifacts) as explicit direct dependencies. Newly discovered and license-checked: `error_prone_annotations:2.41.0` (Apache-2.0), `jaxb-core:4.0.6`/`txw2:4.0.6`/`angus-activation:2.0.3` (EDL-1.0, confirmed via POM headers), `istack-commons-runtime:4.1.2` (⚠️ provisionally assumed EDL-1.0, POM fetch returned no content), plus 3 additional Netty submodules (`netty-codec-compression`, `netty-resolver`, `netty-transport-native-unix-common`, all Apache-2.0). Corrected `jackson-annotations` version to `2.21` (not `2.21.0`) per actual resolved dependency tree. |
