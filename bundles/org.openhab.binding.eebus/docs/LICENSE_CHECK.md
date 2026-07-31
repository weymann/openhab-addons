# License Check: EEBus Binding Dependencies

## Purpose

This document tracks the license compliance check for the external libraries the
`org.openhab.binding.eebus` add-on depends on, so the declared license of each dependency
can be verified against its authoritative Maven repository record at any time.

## Scope

Every dependency declared in `org.openhab.binding.eebus/pom.xml` (32 entries as of this
check), cross-referenced against `bnd.bnd`. `bnd.bnd` in this bundle does **not** declare or
embed any additional third-party artifact — it only overrides bnd's auto-generated
`Import-Package`/`Export-Package` headers for the OSGi manifest (see the comment block at the
top of that file). So the full dependency surface requiring a license check is exactly the set
declared in `pom.xml`.

Of those 32 entries:

1. `spine`, `ship`, and the three `org.openmuc.jeebus.usecase.powerlimitation` artifacts
   (`lpp-controllablesystem`, `lpc-controllablesystem`, `abstract-controllablesystem`) are used
   directly by binding code.
1. The remaining 27 are transitive runtime dependencies of `spine`/`ship`, pinned as explicit
   direct dependencies purely so the reactor's `maven-dependency-plugin` `embed-dependencies`
   execution (`includeScope=runtime`, `excludeTransitive=true`) picks them up and embeds them in
   the built bundle — see ADR-001. Two of those 27 (`slf4j-api`, `jmdns`) are excluded from
   embedding via the `dep.noembedding` property and are **not** declared directly in `pom.xml`
   at all; they are resolved against whatever copy openHAB core/Karaf provides at runtime (see
   "Notes / Follow-ups" below).

Not in scope: the `org.lastnpe.eea:*` artifacts (`eea-all`, `gson-eea`, `guava-eea`, etc.) that
appear under `target/dependency/` after a build. These come from the reactor parent POM as a
compile-time-only tool dependency for Eclipse External Annotations (`@NonNullByDefault`
checking) — they are never embedded or shipped in the bundle, so they carry no distribution
license obligation for this binding.

## All Dependencies (pom.xml, full resolution)

| # | Component | Maven Coordinates | Declared License | License Found At | Embedded? |
|---|-----------|--------------------|--------------------|--------------------|-----------|
| 1 | jEEBus.SPINE | `org.openmuc.jeebus:spine:4.0.1` | Eclipse Public License 2.0 | [spine-4.0.1.pom](https://repo1.maven.org/maven2/org/openmuc/jeebus/spine/4.0.1/spine-4.0.1.pom) | Yes |
| 2 | jEEBus.SHIP | `org.openmuc.jeebus:ship:2.3.0` | Eclipse Public License 2.0 | [ship-2.3.0.pom](https://repo1.maven.org/maven2/org/openmuc/jeebus/ship/2.3.0/ship-2.3.0.pom) | Yes |
| 3 | jEEBus.PowerLimitation.LPP-ControllableSystem | `org.openmuc.jeebus.usecase.powerlimitation:lpp-controllablesystem:1.0.0` | Eclipse Public License 2.0 | [lpp-controllablesystem-1.0.0.pom](https://repo1.maven.org/maven2/org/openmuc/jeebus/usecase/powerlimitation/lpp-controllablesystem/1.0.0/lpp-controllablesystem-1.0.0.pom) | Yes |
| 4 | jEEBus.PowerLimitation.LPC-ControllableSystem | `org.openmuc.jeebus.usecase.powerlimitation:lpc-controllablesystem:1.0.0` | Eclipse Public License 2.0 | [lpc-controllablesystem-1.0.0.pom](https://repo1.maven.org/maven2/org/openmuc/jeebus/usecase/powerlimitation/lpc-controllablesystem/1.0.0/lpc-controllablesystem-1.0.0.pom) | Yes |
| 5 | jEEBus.PowerLimitation.Abstract-ControllableSystem | `org.openmuc.jeebus.usecase.powerlimitation:abstract-controllablesystem:1.0.0` | Eclipse Public License 2.0 | [abstract-controllablesystem-1.0.0.pom](https://repo1.maven.org/maven2/org/openmuc/jeebus/usecase/powerlimitation/abstract-controllablesystem/1.0.0/abstract-controllablesystem-1.0.0.pom) | Yes |
| 6 | Jackson Databind | `com.fasterxml.jackson.core:jackson-databind:2.21.0` | Apache License, Version 2.0 | [jackson-bom-2.21.0.pom](https://repo1.maven.org/maven2/com/fasterxml/jackson/jackson-bom/2.21.0/jackson-bom-2.21.0.pom) (via `jackson-parent`) | Yes |
| 7 | Jackson Annotations | `com.fasterxml.jackson.core:jackson-annotations:2.21` | Apache License, Version 2.0 | [jackson-bom-2.21.0.pom](https://repo1.maven.org/maven2/com/fasterxml/jackson/jackson-bom/2.21.0/jackson-bom-2.21.0.pom) (via `jackson-parent`) | Yes |
| 8 | Jackson Core | `com.fasterxml.jackson.core:jackson-core:2.21.0` | Apache License, Version 2.0 | [jackson-bom-2.21.0.pom](https://repo1.maven.org/maven2/com/fasterxml/jackson/jackson-bom/2.21.0/jackson-bom-2.21.0.pom) (via `jackson-parent`) | Yes |
| 9 | Jackson Module: Jakarta XmlBind Annotations | `com.fasterxml.jackson.module:jackson-module-jakarta-xmlbind-annotations:2.21.0` | Apache License, Version 2.0 | [jackson-module-jakarta-xmlbind-annotations-2.21.0.pom](https://repo1.maven.org/maven2/com/fasterxml/jackson/module/jackson-module-jakarta-xmlbind-annotations/2.21.0/jackson-module-jakarta-xmlbind-annotations-2.21.0.pom) | Yes |
| 10 | Jakarta XML Binding API | `jakarta.xml.bind:jakarta.xml.bind-api:4.0.5` | Eclipse Distribution License 1.0 (SPDX: BSD-3-Clause) | [jakarta.xml.bind-api-4.0.5.pom](https://repo1.maven.org/maven2/jakarta/xml/bind/jakarta.xml.bind-api/4.0.5/jakarta.xml.bind-api-4.0.5.pom) (copyright header) | Yes |
| 11 | Jakarta Activation API | `jakarta.activation:jakarta.activation-api:2.1.0` | EDL 1.0 (SPDX: BSD-3-Clause) | [jakarta.activation-api-2.1.0.pom](https://repo1.maven.org/maven2/jakarta/activation/jakarta.activation-api/2.1.0/jakarta.activation-api-2.1.0.pom) (`<licenses>` block) | Yes |
| 12 | JAXB Tools :: JAXB Plugins :: Runtime | `org.jvnet.jaxb:jaxb-plugins-runtime:4.0.12` | BSD-Style License (3-clause BSD) | [jaxb-tools-project-4.0.12.pom](https://repo1.maven.org/maven2/org/jvnet/jaxb/jaxb-tools-project/4.0.12/jaxb-tools-project-4.0.12.pom) (`<licenses>` block, inherited via `jaxb-plugins-project` parent) | Yes |
| 13 | Glassfish JAXB Core | `org.glassfish.jaxb:jaxb-core:4.0.6` | Eclipse Distribution License 1.0 (SPDX: BSD-3-Clause) | [jaxb-core-4.0.6.pom](https://repo1.maven.org/maven2/org/glassfish/jaxb/jaxb-core/4.0.6/jaxb-core-4.0.6.pom) (copyright header) | Yes |
| 14 | TXW2 Runtime | `org.glassfish.jaxb:txw2:4.0.6` | Eclipse Distribution License 1.0 (SPDX: BSD-3-Clause) | [txw2-4.0.6.pom](https://repo1.maven.org/maven2/org/glassfish/jaxb/txw2/4.0.6/txw2-4.0.6.pom) (copyright header) | Yes |
| 15 | Angus Activation | `org.eclipse.angus:angus-activation:2.0.3` | Eclipse Distribution License 1.0 (SPDX: BSD-3-Clause) | [angus-activation-2.0.3.pom](https://repo1.maven.org/maven2/org/eclipse/angus/angus-activation/2.0.3/angus-activation-2.0.3.pom) (copyright header) | Yes |
| 16 | iStack Commons Runtime | `com.sun.istack:istack-commons-runtime:4.1.2` | Eclipse Distribution License - v 1.0 (SPDX: BSD-3-Clause) | [istack-commons-4.1.2.pom](https://repo1.maven.org/maven2/com/sun/istack/istack-commons/4.1.2/istack-commons-4.1.2.pom) (`<licenses>` block, inherited via `istack-commons` parent) | Yes |
| 17 | Gson | `com.google.code.gson:gson:2.13.2` | Apache-2.0 | [gson-2.13.2.pom](https://repo1.maven.org/maven2/com/google/code/gson/gson/2.13.2/gson-2.13.2.pom) (`<licenses>` block) | Yes |
| 18 | Error Prone Annotations | `com.google.errorprone:error_prone_annotations:2.41.0` | Apache 2.0 | [error_prone_annotations-2.41.0.pom](https://repo1.maven.org/maven2/com/google/errorprone/error_prone_annotations/2.41.0/error_prone_annotations-2.41.0.pom) (`<licenses>` block) | Yes |
| 19 | JmDNS | `org.jmdns:jmdns:3.6.3` | Apache License, Version 2.0 | [jmdns-3.6.3.pom](https://repo1.maven.org/maven2/org/jmdns/jmdns/3.6.3/jmdns-3.6.3.pom) (`<licenses>` block) | **No** — excluded via `dep.noembedding`, resolved against openHAB core's own jmDNS copy at runtime (ADR-001) |
| 20 | Bouncy Castle Provider | `org.bouncycastle:bcprov-jdk18on:1.83` | Bouncy Castle Licence | [bcprov-jdk18on-1.83.pom](https://repo1.maven.org/maven2/org/bouncycastle/bcprov-jdk18on/1.83/bcprov-jdk18on-1.83.pom) (`<licenses>` block) | Yes ⚠️ see Notes |
| 21 | Bouncy Castle PKIX/CMS/EAC/TSP/PKCS/OCSP/CMP/CRMF | `org.bouncycastle:bcpkix-jdk18on:1.83` | Bouncy Castle Licence | [bcpkix-jdk18on-1.83.pom](https://repo1.maven.org/maven2/org/bouncycastle/bcpkix-jdk18on/1.83/bcpkix-jdk18on-1.83.pom) (`<licenses>` block) | Yes ⚠️ see Notes |
| 22 | Bouncy Castle ASN.1 Extension/Utility | `org.bouncycastle:bcutil-jdk18on:1.83` | Bouncy Castle Licence | [bcutil-jdk18on-1.83.pom](https://repo1.maven.org/maven2/org/bouncycastle/bcutil-jdk18on/1.83/bcutil-jdk18on-1.83.pom) (`<licenses>` block) | Yes ⚠️ see Notes |
| 23 | FindBugs jsr305 | `com.google.code.findbugs:jsr305:3.0.2` | The Apache Software License, Version 2.0 | [jsr305-3.0.2.pom](https://repo1.maven.org/maven2/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.pom) (`<licenses>` block) | Yes |
| 24 | Netty/Common | `io.netty:netty-common:4.2.10.Final` | Apache License, Version 2.0 | [netty-parent-4.2.10.Final.pom](https://repo1.maven.org/maven2/io/netty/netty-parent/4.2.10.Final/netty-parent-4.2.10.Final.pom) (`<licenses>` block) | Yes |
| 25 | Netty/Buffer | `io.netty:netty-buffer:4.2.10.Final` | Apache License, Version 2.0 | [netty-parent-4.2.10.Final.pom](https://repo1.maven.org/maven2/io/netty/netty-parent/4.2.10.Final/netty-parent-4.2.10.Final.pom) (`<licenses>` block) | Yes |
| 26 | Netty/Codec/Base | `io.netty:netty-codec-base:4.2.10.Final` | Apache License, Version 2.0 | [netty-parent-4.2.10.Final.pom](https://repo1.maven.org/maven2/io/netty/netty-parent/4.2.10.Final/netty-parent-4.2.10.Final.pom) (`<licenses>` block) | Yes |
| 27 | Netty/Codec/HTTP | `io.netty:netty-codec-http:4.2.10.Final` | Apache License, Version 2.0 | [netty-parent-4.2.10.Final.pom](https://repo1.maven.org/maven2/io/netty/netty-parent/4.2.10.Final/netty-parent-4.2.10.Final.pom) (`<licenses>` block) | Yes |
| 28 | Netty/Codec/Compression | `io.netty:netty-codec-compression:4.2.10.Final` | Apache License, Version 2.0 | [netty-parent-4.2.10.Final.pom](https://repo1.maven.org/maven2/io/netty/netty-parent/4.2.10.Final/netty-parent-4.2.10.Final.pom) (`<licenses>` block) | Yes |
| 29 | Netty/Resolver | `io.netty:netty-resolver:4.2.10.Final` | Apache License, Version 2.0 | [netty-parent-4.2.10.Final.pom](https://repo1.maven.org/maven2/io/netty/netty-parent/4.2.10.Final/netty-parent-4.2.10.Final.pom) (`<licenses>` block) | Yes |
| 30 | Netty/Transport | `io.netty:netty-transport:4.2.10.Final` | Apache License, Version 2.0 | [netty-parent-4.2.10.Final.pom](https://repo1.maven.org/maven2/io/netty/netty-parent/4.2.10.Final/netty-parent-4.2.10.Final.pom) (`<licenses>` block) | Yes |
| 31 | Netty/Transport/Native/Unix/Common | `io.netty:netty-transport-native-unix-common:4.2.10.Final` | Apache License, Version 2.0 | [netty-parent-4.2.10.Final.pom](https://repo1.maven.org/maven2/io/netty/netty-parent/4.2.10.Final/netty-parent-4.2.10.Final.pom) (`<licenses>` block) | Yes |
| 32 | Netty/Handler | `io.netty:netty-handler:4.2.10.Final` | Apache License, Version 2.0 | [netty-parent-4.2.10.Final.pom](https://repo1.maven.org/maven2/io/netty/netty-parent/4.2.10.Final/netty-parent-4.2.10.Final.pom) (`<licenses>` block) | Yes |

Result for all 32: **OK**. All declared licenses are either EPL-2.0 (matching this project's own
license), Apache-2.0, or an EDL-1.0/BSD-3-Clause/BSD-3-clause-style license — all compatible
with distribution through openHAB add-ons. The one accepted exception is BouncyCastle's own
license (see Notes below, carried forward unchanged from the previous check).

Not embedded but resolved at runtime against openHAB core (not in the table above because they
are not declared in `pom.xml` at all):

| Component | Coordinates last seen | Declared License | License Found At |
|-----------|-------------------------|--------------------|--------------------|
| SLF4J API | `org.slf4j:slf4j-api` (version varies by consumer: `2.0.17` per `spine`, `2.0.18` per `ship:2.3.0`, `2.0.16` per the `powerlimitation` usecase libs, `2.0.7` per `jmdns`, `2.0.13` per `netty-common` — not resolved by this build since it is intentionally absent from `pom.xml`) | MIT License | [slf4j.org license page](https://www.slf4j.org/license.html) |

## Verification Method

1. Read `org.openhab.binding.eebus/pom.xml` in full and enumerated every `<dependency>` entry
   (32 total).
1. Read `org.openhab.binding.eebus/bnd.bnd` in full and confirmed it declares no additional
   embedded artifact — it only rewrites `Import-Package`/`Export-Package` for the generated OSGi
   manifest (see its in-file comments, dated corrections up to 2026-08-01).
1. For each `pom.xml` dependency, fetched the published POM for the exact resolved version
   directly from Maven Central (`repo1.maven.org`) — the artifact actually resolved by a
   Maven/Tycho build, not just a source-repository declaration.
1. Where the artifact's own POM does not carry a `<licenses>` block directly (common for modules
   that inherit it from a multi-level parent POM, e.g. `jaxb-core`, `txw2`, `angus-activation`,
   `istack-commons-runtime`, `jaxb-plugins-runtime`, all Netty submodules), walked up the
   `<parent>` chain until the `<licenses>` block (or, for the Eclipse EE4J/JAXB-RI family, the
   equivalent EDL-1.0 copyright header present verbatim at the top of every module POM) was found.
1. Cross-checked against Maven Central's directory listing to confirm every consumed version is
   an actually published release (not a snapshot).

This resolves two items that were left as provisional/unconfirmed in the previous check:
`org.jvnet.jaxb:jaxb-plugins-runtime` (now confirmed BSD-3-Clause via the `jaxb-tools-project`
parent POM) and `com.sun.istack:istack-commons-runtime` (now confirmed EDL-1.0 via the
`istack-commons` parent POM's explicit `<licenses>` block).

## Notes / Follow-ups

- **`ship` version bump**: `pom.xml` now pins `ship:2.3.0` (previously `2.2.0`); re-verified
  license is still EPL-2.0. `ship:2.3.0`'s own POM manages its Netty/BouncyCastle BOM imports at
  newer versions (`netty-bom:4.2.15.Final`, `bc-jdk18on-bom:1.84`) than what `pom.xml` currently
  pins directly (`4.2.10.Final` / `1.83`) — this is a version-alignment question for `$Architect`,
  not a license question, but worth flagging since it means this binding is not actually building
  against the exact transitive versions `ship:2.3.0` itself declares.
- **Three new direct dependencies** (`lpp-controllablesystem`, `lpc-controllablesystem`,
  `abstract-controllablesystem`, all `1.0.0`) were not present in the previous license check at
  all. All three are EPL-2.0, from the same `openmuc/jeebus.usecase.powerlimitation.controllablesystem`
  GitHub repository and Maven Central namespace as `spine`/`ship`.
- BouncyCastle's own license (`https://www.bouncycastle.org/licence.html`, an MIT-derivative)
  is OSI-approved and permissive but not literally on some organizations' pre-approved license
  lists — carried forward as an accepted exception per ADR-001, unchanged by this re-check.
- `slf4j-api` is deliberately **not** declared in `pom.xml` (only referenced by name in the
  `dep.noembedding` property's explanatory comment, alongside `jmdns`, for documentation
  purposes) and is therefore not part of this build's resolved dependency graph — the binding
  relies on whatever SLF4J copy openHAB core/Karaf provides at runtime via `Import-Package`.
  License checked directly against slf4j.org for completeness; not part of the embedded bundle.
- `jeebus.spine` and `jeebus.ship` also expose an internal Fraunhofer GitLab package registry
  (`https://gitlab.cc-asp.fraunhofer.de/api/v4/groups/18477/-/packages/maven`) for
  development/snapshot builds. The binding itself only ever resolves the public Maven Central
  releases referenced above.
- `jeebus.ship` and `jeebus.spine` repositories are change-protected in this workspace (require
  prior human approval) — this check was performed read-only against those repos and against
  public Maven Central metadata; no files were modified in either repository, nor in
  `org.openhab.binding.eebus/pom.xml` or `bnd.bnd`.

## Change Log

| Date       | Change                                                                 |
|------------|-------------------------------------------------------------------------|
| 2026-07-31 | Initial check: confirmed EPL-2.0 for `ship:2.2.0` and `spine:4.0.1`, added Maven repository links for traceability. |
| 2026-07-31 | ADR-001: switched to embedding `ship`/`spine` and their transitive dependency graph. Checked licenses for all transitive dependencies; 2 exceptions flagged (BouncyCastle licence accepted, jaxb-plugins-runtime/jaxb-core provisionally accepted pending `mvn license:add-third-party` confirmation). |
| 2026-07-31 | ADR-001 correction: embedding mechanism is `maven-dependency-plugin` (`embed-dependencies`), not `bnd.bnd`/`Embed-Dependency`; updated "Scope" and "Transitive Runtime Dependencies" text accordingly. Added `jackson-module-jakarta-xmlbind-annotations:2.21.0` as an explicit direct dependency (`type=jar`) so it is embedded despite its own `packaging=bundle` POM metadata; no license change (already covered by the Jackson row above). |
| 2026-07-31 | ADR-001 correction: root cause was `excludeTransitive=true` on the reactor's embedding step, not packaging metadata — only directly-declared `pom.xml` dependencies are ever embedded. Ran `mvn dependency:tree -Dscope=runtime` and added the full transitive closure (24 further artifacts) as explicit direct dependencies. Newly discovered and license-checked: `error_prone_annotations:2.41.0` (Apache-2.0), `jaxb-core:4.0.6`/`txw2:4.0.6`/`angus-activation:2.0.3` (EDL-1.0, confirmed via POM headers), `istack-commons-runtime:4.1.2` (⚠️ provisionally assumed EDL-1.0, POM fetch returned no content), plus 3 additional Netty submodules (`netty-codec-compression`, `netty-resolver`, `netty-transport-native-unix-common`, all Apache-2.0). Corrected `jackson-annotations` version to `2.21` (not `2.21.0`) per actual resolved dependency tree. |
| 2026-08-12 | Full re-check requested (post-hoc, after the dependency set had already changed): re-read `pom.xml`/`bnd.bnd` end to end and re-verified all 32 declared dependencies individually against Maven Central. Picked up `ship` version bump `2.2.0` → `2.3.0` (license unchanged, EPL-2.0) and 3 new direct dependencies not previously checked (`lpp-controllablesystem`, `lpc-controllablesystem`, `abstract-controllablesystem`, all EPL-2.0). Resolved the two previously provisional entries: `jaxb-plugins-runtime` confirmed BSD-3-Clause (via `jaxb-tools-project` parent POM), `istack-commons-runtime` confirmed EDL-1.0 (via `istack-commons` parent POM). Confirmed `bnd.bnd` declares no additional embedded artifact beyond `pom.xml`. Added `slf4j-api` as a non-embedded, non-declared runtime dependency for completeness (MIT, sourced from openHAB core). |
