# Konzept: EEBus-Binding auf Basis von jEEBus.SHIP / jEEBus.SPINE

Status: Entwurf
Autor: Bernd Weymann
Betrifft: `org.openhab.binding.eebus`, `org.openhab.transport.eebus`, `jeebus.ship`, `jeebus.spine`

## 1. Ziel

Ein openHAB-Binding für EEBus (SHIP-Transport + SPINE-Anwendungsschicht), das vier
Kernanforderungen abdeckt:

1. Schlüsselerzeugung (Geräte-Identität, TLS-Zertifikat, SKI)
1. Pairing (Vertrauensaufbau mit Peer-Geräten)
1. Discovery mit Namen (mDNS-Sichtbarkeit mit Hersteller-/Modell-/Gerätename statt nur SKI)
1. Use-Case-Identifizierung, in **zwei Richtungen** (siehe §5.4/§5.5, nachträglich präzisiert):
   - **Konsument:** Erkennen, welche Use-Cases ein gepairtes Gerät anbietet.
   - **Anbieter:** openHAB muss selbst Use-Cases anbieten können (z. B. ein Messwert-Use-Case
     aus einem Wechselrichter-Thing), damit die Kopplung auch in die andere Richtung
     funktioniert — ohne das hat Pairing für diesen Fall keinen Nutzen.

## 2. Ist-Zustand

Im Projektumfeld existieren drei unabhängige EEBus-Stacks:

| Stack | Ort | Zustand |
|---|---|---|
| `io.eebus.*` (Port von eebus-go/ship-go) | `EEBus/eebus-java`, `EEBus/ship-java` | SHIP-Transport (`ShipAdapterImpl`) funktionsfähig; SPINE-Anbindung nicht verdrahtet (`onSpineData()` ist ein Platzhalter, `StubSpineAdapter` liefert überall `null`) |
| `org.openmuc.jeebus.*` (Fraunhofer ISE) | `jeebus.ship`, `jeebus.spine` | SHIP und SPINE beide vollständig implementiert, inkl. automatischer Use-Case-Erkennung; SHIP-Anbindung über `org.openmuc.jeebus.shipspine.ShipCommunication` bereits eingebaut |
| `org.openhab.transport.eebus` | `EEBus/org.openhab.transport.eebus` | OSGi-Wrapper-Bundle mit stabiler `EEBusService`-API, aktuell verdrahtet gegen den `io.eebus`-Stack |
| `org.openhab.binding.eebus` | dieses Bundle | reines Maven-Archetype-Gerüst, keine EEBus-Logik, keine Abhängigkeit zu `org.openhab.transport.eebus` |

**Entscheidung:** `jeebus.ship` + `jeebus.spine` ersetzen den kompletten `io.eebus`-Stack als
Backend von `org.openhab.transport.eebus`, statt nur die SPINE-Lücke zu füllen. Begründung:
`jeebus.spine` bringt die automatische Use-Case-Erkennung bereits fertig mit und integriert
`jeebus.ship` nativ — das ist genau die fehlende Funktion, während die SHIP-Seite in beiden
Stacks funktionsfähig ist.

## 3. Schichtenmodell

**Revidiert:** Kein separates Transport-Bundle. `jeebus.ship` und `jeebus.spine` sind bereits
fertige, eigenständige OSGi-Bundles (bnd-Builder-Plugin, eigenes `Bundle-SymbolicName`) und
unter `org.openmuc.jeebus` auf Maven Central veröffentlicht (`spine:4.0.1`, zieht transitiv
`ship:2.2.0`). Es gibt daher nichts zu wrappen — `org.openhab.binding.eebus` deklariert
`spine` als normale Maven-Dependency und nutzt sie intern. Ein separates
`org.openhab.transport.eebus`-Bundle (wie im alten, jetzt obsoleten `io.eebus`-Prototyp)
wäre nur gerechtfertigt, wenn mehrere unabhängige Bindings sich eine SHIP/SPINE-Instanz
teilen müssten — dafür gibt es aktuell keinen Bedarf.

```text
org.openhab.binding.eebus                     (einziges Bundle)
  internal.EEBusHandler        ── 1 lokale SHIP/SPINE-Serviceinstanz (Bridge, eebus:service)
  internal.EEBusPeerHandler    ── je ein gepairtes entferntes Gerät (SKI, eebus:peer)
        │  nutzt direkt (keine OSGi-Service-Grenze dazwischen)
        ▼
jeebus.spine (Device / Entity / NodeManagement / UseCase*)   [Maven: org.openmuc.jeebus:spine:4.0.1]
jeebus.ship  (ShipNodeConfiguration / ShipCommunication / KeyManagement)     [transitiv: ship:2.2.0]
```

Kein eigener `internal.discovery`/`internal.handler`-Unterpaket-Schnitt — beide Handler liegen
flach in `internal`, siehe Naming-Hinweis in Abschnitt 4. Ein separater Discovery-Service ist
laut Abschnitt 4.1 mit der aktuellen API nicht sinnvoll umsetzbar und daher nicht Teil des
Schichtenmodells.

Die in Abschnitt 5 beschriebenen Erweiterungen (Use-Case-Event, Pairing-Approve/Reject) sind
damit kein API-Vertrag zwischen zwei Bundles mehr, sondern normale interne Methoden/Callbacks
innerhalb von `EEBusBridgeHandler`/`EEBusPeerHandler`.

## 4. Bridge/Peer-Modell

Begriffsklärung vorab: SPINE nennt das lokale Objekt, das eine Identität (Zertifikat/SKI)
sowie eigene Entities/Use-Cases bündelt, selbst `Device`
(`org.openmuc.jeebus.spine.api.Device`). Um das nicht mit dem openHAB-Thing für ein
entferntes Gerät zu verwechseln, heißt Letzteres hier `eebus:peer` (nicht `eebus:device`).

| Thing | ThingTypeUID (Vorschlag) | Repräsentiert | Kardinalität |
|---|---|---|---|
| Bridge | `eebus:service` | eine lokale SHIP/SPINE-Serviceinstanz (genau ein `Device` in jeebus.spine) | 1:1, exklusiv — **entschieden, kein Teilen einer Identität über mehrere Bridges** |
| Thing | `eebus:peer` | ein gepairtes entferntes Gerät, identifiziert über SKI | n pro Bridge |

**Bridge (`eebus:service`)** — _aktueller Stand, ersetzt die ursprüngliche Fassung dieses
Absatzes, die noch von einem separaten Transport-Bundle und einer `approvePairing`-Action
ausging (siehe §5.2, §4.1 für die Korrekturen):_

- Konfiguration: `vendorCode`, `deviceBrand`, `deviceModel`, `serialNumber`,
  `mdnsServiceInstance` (Anzeigename für Discovery), `port`, `autoAcceptEnabled`,
  `supportedUseCasesClient`, `supportedUseCasesServer` (Checkbox-Auswahl, siehe §4.3;
  noch nicht in `thing-types.xml` umgesetzt — siehe §7).
- Properties (read-only, nach Start gesetzt): `localSki`.
- Aufgabe: hält `ShipCommunication` + SPINE-`Device`, legt/lädt das Zertifikat, berechnet die
  Trusted-SKI-Menge aus den konfigurierten `eebus:peer`-Kind-Things (§5.2) — **kein**
  separater Approve/Reject-Mechanismus.
- Besitzt genau ein SPINE-`Device`-Objekt exklusiv — keine zwei Bridges teilen sich je ein
  Zertifikat/SKI (bestätigte Entscheidung, siehe Abschnitt 6).

**Peer (`eebus:peer`)**

- Konfiguration: `ski` (Pflichtfeld, aus Discovery übernommen), `shipId` (wird beim ersten
  Handshake gelernt und muss laut `EEBusEventHandler#onServiceShipIdUpdate` persistiert
  werden — landet in der Thing-Konfiguration).
- Kanäle: werden **nicht** statisch in `thing-types.xml` vordefiniert, sondern dynamisch aus
  den erkannten Use-Cases erzeugt (siehe 4.1), da ein Peer-Gerät je nach unterstütztem
  Use-Case unterschiedliche Feature-Sets hat (z. B. LPC-Leistungsgrenze vs.
  Messwert-Kanäle).

### 4.1 Discovery-Service — **Einschränkung, bei Implementierung entdeckt**

Beim Implementieren von `EEBusHandler` zeigte sich: `ShipCommunication` (jeebus.spine)
kapselt die rohe mDNS-Sicht vollständig — sie erzeugt intern ihren eigenen `ConnectionHandler`
und das zugrunde liegende `Ship`-Objekt bleibt privat (nur `getOwnSki()` ist öffentlich).
Es gibt in der gelesenen public API **keine** Methode, die "sichtbare, aber noch nicht
gepairte SKIs mit Namen" roh herausgibt, bevor eine Verbindung/SPINE-Discovery läuft.
`NodeManagement.addDeviceListener(...)` liefert Geräte erst nach erfolgreicher
SHIP-Verbindung, nicht davor.

**Update — konkreter Lösungsweg gefunden (hängt mit §5.4 TODO 2 zusammen):**
`org.openmuc.jeebus.ship.node.service` (`ServiceRegistry`, `TxtRecord`) ist laut
`ship/build.gradle` `Export-Package`-Direktive **nicht** exportiert
(`org.openmuc.jeebus.ship.api`, `.api.cert`, `.shipconnection` sind die einzigen exportierten
Pakete) — als OSGi-Dependency also nicht direkt nutzbar, wie vermutet.

Stattdessen: ein **eigener, kleiner mDNS-Browser** auf Basis der öffentlichen `JmDNS`-Bibliothek
(kein `jeebus.ship`-internes Paket, sondern deren eigene Transport-Abhängigkeit — kann parallel
selbst eingebunden werden), der `_ship._tcp.local.` durchsucht und die TXT-Records selbst
liest (Felder gemäß SHIP 7.3.2: id, ski, brand, type, model — siehe `TxtRecord`-Quelle als
Vorlage). Das liefert nicht nur Name+SKI für eine Discovery-Inbox (Anforderung 3 vollständig
erfüllt, nicht nur die eigene Sichtbarkeit), sondern **löst auch §5.4 TODO 2**: dieselbe
IP-Adresse, die unser eigener Browser auflöst, ist identisch mit dem `communicationAddress`,
das `Communication.addDevice(String communicationAddress)` intern verwendet (siehe
`Communication.java` — wird mit der IP aus `connectionDataExchangeEnabled(String ip)`
aufgerufen). Ein selbst geführtes IP↔SKI-Mapping erlaubt also, `UseCasePartner
.getCommunicationAddress()` auf die SKI eines `eebus:peer`-Things zurückzuführen.

**Umgesetzt** (§7 Punkt 9): `EEBusMdnsBrowser` (neue Klasse, `internal`-Package) implementiert
`javax.jmdns.ServiceListener`, durchsucht `_ship._tcp.local.`, liest `ski`/`brand`/`model`/`type`
aus dem TXT-Record und pflegt `communicationAddress -> DiscoveredService` sowie
`ski -> DiscoveredService`-Maps. `EEBusHandler` startet ihn in `startShipSpine()` und schließt ihn
in `dispose()`; erreichbar über den paketprivaten Accessor `EEBusHandler#getMdnsBrowser()`. Das
reine Discovery-Inbox-UI (openHAB `DiscoveryService`/Inbox) ist ein separater Folgeschritt (neuer
Punkt 10 in §7), noch nicht umgesetzt — der Browser selbst liefert aber bereits alle nötigen
Daten dafür.

**Revidiert (2026-07-30):** ursprünglich erzeugte `EEBusMdnsBrowser` per `JmDNS.create()` eine
eigene, private JmDNS-Instanz — auf Nachfrage korrigiert: openHAB Core bietet mit
`org.openhab.core.io.transport.mdns.MDNSClient` bereits einen geteilten mDNS-Service (eine pro
Netzwerkinterface verwaltete `JmDNS`-Instanz-Menge, `addServiceListener(type, listener)` und
`removeServiceListener(...)`), den alle Bindings gemeinsam nutzen sollen, statt jeweils eigene
mDNS-Responder zu starten. `EEBusMdnsBrowser` bekommt `MDNSClient` jetzt injiziert (über
`EEBusHandlerFactory` → `EEBusHandler`, OSGi `@Reference`) statt selbst eine `JmDNS`-Instanz zu
verwalten; `close()` meldet nur noch den Listener ab (`removeServiceListener`), schließt aber
nicht mehr die (geteilte, von openHAB Core lebenszyklus-verwaltete) JmDNS-Instanz selbst. Die
`org.jmdns`-Maven-Dependency bleibt nötig (für die `javax.jmdns.*`-Typen in den Methodensignaturen
von `EEBusMdnsBrowser`), ist aber auf `provided`-Scope gesetzt, damit sie nicht ein zweites Mal
ins eigene OSGi-Bundle eingebettet/exportiert wird — das noch offene, alte "Version-Konflikt"-TODO
im `pom.xml`-Kommentar ist damit hinfällig. **Noch nicht gegen eine echte Karaf-Laufzeit
verifiziert.**

### 4.2 Datenanbindung: Item-Metadaten statt dynamischer Channels — **umgesetzt (§7 Punkt 6)**

Die ursprüngliche Idee eines `DynamicChannelTypeProvider`, der pro erkanntem Use-Case
automatisch Channels anlegt, wird **verworfen** zugunsten eines Metadaten-Ansatzes
(Entscheidung im Gespräch): analog zu bekannten openHAB-Metadaten-Namespaces (`homekit`,
`alexa`, `channel`) bekommt dieses Binding einen eigenen Namespace `eebus`, mit dem
**beliebige** existierende Items (auch aus ganz anderen Bindings, z. B. ein
Wechselrichter-Leistungs-Item aus einem Modbus/SunSpec-Binding) an einen konkreten
Use-Case-Datenpunkt gekoppelt werden:

```java
Number:Power WR_Leistung "Wechselrichter Leistung" { eebus="MPC.power" [peer="eebus:peer:wechselrichter"] }
```

**Wert-Syntax:** `eebus="<UseCase>.<Datenpunkt>"`, Konfiguration (eckige Klammern) optional
`[peer="<eebus:peer-Thing-UID>"]`.

- Für **angebotene** (Server-)Use-Cases (§5.5) ist die Kopplung Bridge-weit (ein Server-Feature
  ist netzwerkweit sichtbar, nicht pro Peer) — kein `peer`-Attribut nötig.
- Für **konsumierte** (Client-)Use-Case-Datenpunkte (z. B. eine LPC-Grenze an einen
  bestimmten Peer schreiben, oder MPC-Werte eines bestimmten Peers lesen) ist die Kopplung
  pro Peer nötig — daher das `peer`-Attribut mit der Thing-UID.

**Datenpunkt-Vokabular (Startmenge, aus §5.4.2 abgeleitet — wird erweitert, sobald weitere
Use-Cases/Szenarien im Detail beschafft sind):**

| Datenpunkt | Use-Case | Richtung (aus Sicht des Items) | SPINE-Quelle |
|---|---|---|---|
| `MPC.power` | MPC (Server-Rolle, "ImSys"-Anwendungsfall des Nutzers) | lesend (Item-State → SPINE-Antwort) | `Measurement`-Server, `MeasurementListDataFunction` |
| `MGCP.power` | MGCP (Server-Rolle) | lesend | `Measurement`-Server, analog MPC |
| `LPC.consumptionLimit` | LPC (Server-Rolle, CS) | schreibend (SPINE-Write vom EG-Peer → Item-Command) | `LoadControl`-Server, `LimitListDataFunction` |
| `LPC.failsafeConsumptionLimit` | LPC (Server-Rolle, CS) | schreibend (Konfiguration, jederzeit vom EG änderbar) | `DeviceConfiguration`-Server, `KeyValueListDataFunction` (`FailsafeConsumptionActivePowerLimit`) |
| `LPC.failsafeDurationMinimum` | LPC (Server-Rolle, CS) | schreibend (Konfiguration) | `DeviceConfiguration`-Server, `KeyValueListDataFunction` (`FailsafeDurationMinimum`) — **neu entdeckt beim Umsetzen**: Szenario 2 hat zwei Datenpunkte, nicht nur den Grenzwert |
| `LPC.state` | LPC (Server-Rolle, CS) | lesend (Item-State ← Zustandsautomat) | kein SPINE-Feature direkt — Ausgabe von `EEBusLimitControlStateMachine` (init/limited/unlimitedControlled/unlimitedAutonomous/failsafe), gedacht für Rules (siehe §4.2.1) |
| `LPP.productionLimit` | LPP (Server-Rolle, CS) | schreibend | analog `LPC.consumptionLimit`, Richtung Einspeisung |
| `LPP.failsafeProductionLimit` | LPP (Server-Rolle, CS) | schreibend (Konfiguration) | analog `LPC.failsafeConsumptionLimit` (`FailsafeProductionActivePowerLimit`) |
| `LPP.failsafeDurationMinimum` | LPP (Server-Rolle, CS) | schreibend (Konfiguration) | analog `LPC.failsafeDurationMinimum` |
| `LPP.state` | LPP (Server-Rolle, CS) | lesend | analog `LPC.state` |

**Java-seitige Architektur** (neue Klasse `EEBusMetadataService`, OSGi-Component in
`internal`, `@Reference`s auf `MetadataRegistry`, `ItemRegistry`, `EventPublisher` — alle drei
sind Standard-`org.openhab.core`-APIs, keine neue `pom.xml`-Dependency nötig, verifiziert
anhand der openHAB-Core-Javadocs, siehe Quellen):

- `Optional<Metadata> find(String useCase, String dataPoint, @Nullable String peerThingUid)`:
  iteriert `metadataRegistry.getAll()`, filtert auf `key.getNamespace().equals("eebus")` und
  `metadata.getValue().equals(useCase + "." + dataPoint)`, sowie – falls `peerThingUid`
  angegeben – auf `metadata.getConfiguration().get("peer")`.
- Lese-Pfad (**korrigiert nach Implementierung** — kein Abfrage-Callback, siehe §7 Punkt 6):
  SPINE hält Server-Feature-Daten in einem lokalen Cache (`ReadListFeatureFunction`/
  `DataListHolder`) und beantwortet Leseanfragen sowie Subscriptions automatisch daraus. Das
  Binding registriert sich stattdessen per `EEBusMetadataService
  .registerItemStateListener(itemName, ...)` für Item-Änderungen und schreibt bei jeder
  Änderung proaktiv per `updateData(idx, ...)` in diesen Cache.
- Schreib-Pfad (SPINE-Write kommt vom Peer an, siehe `ReadAndWriteListFeatureFunction
  .addUseCaseWriteDataListener(...)`, verifiziert anhand jeebus.spine-Quellcode):
  `eventPublisher.post(ItemEventFactory.createCommandEvent(itemName, command))` — für v1 nur
  als Architektur festgelegt, noch nicht an einem konkreten Server-Use-Case (z. B. LPC)
  implementiert (siehe §7 Punkt 8).
- Caching/Aktualisierung bei Metadaten-Änderungen (`RegistryChangeListener<Metadata>`) ist für
  v1 **nicht** vorgesehen — Zuordnung wird beim Bridge-/Peer-Start einmalig aufgelöst,
  Änderungen erfordern einen Thing-Neustart (gleiche Einschränkung wie bei der
  Trusted-SKI-Neuberechnung in §5.2, akzeptabel für v1).

### 4.2.1 Rule-Tag-Mechanismus — **entschieden: kein eigener Mechanismus nötig (§7 Punkt 7)**

Beim Entwerfen zeigte sich: ein separater "Rule-Tag"-Mechanismus wäre **redundant** zum
Item-Metadaten-Ansatz. Ein Datenpunkt, der nicht 1:1 auf ein reales Item abbildbar ist (z. B.
ein berechneter/abgeleiteter Wert), lässt sich genauso gut über ein gewöhnliches **Proxy-Item**
lösen, dessen Zustand eine normale openHAB-Rule pflegt (`postUpdate` bei Bedarf) bzw. das eine
Rule per "Item empfing Befehl"-Trigger auswertet:

```java
Number:Power WR_Leistung_Berechnet "Wechselrichter Leistung (berechnet)" { eebus="MPC.power" }
```

- eine ganz normale Rule (Tag `eebus` nur zur Auffindbarkeit/Dokumentation empfohlen, aber
**funktional nicht erforderlich**), die `WR_Leistung_Berechnet` aktuell hält.

Das erspart: einen neuen openHAB-`ModuleType`/`TriggerHandlerFactory` (nötig, um SPINE-Requests
als Rule-Trigger verfügbar zu machen), eine bespoke "synchron Rule ausführen und Ergebnis
zurückholen"-API (openHAB-Rules sind grundsätzlich asynchron/fire-and-forget,
`RuleManager.runNow(...)` liefert keinen Rückgabewert), und eine zweite Parser-Implementierung
für dieselbe Wert-Syntax. **Konsequenz:** §7 Punkt 7 ist damit durch Vereinfachung gelöst,
nicht durch Implementierung — analog zur Pairing-Entscheidung in §5.2 (kein separates
Approve/Reject-API nötig).

Vorteil des Item-Metadaten-Ansatzes gegenüber dynamischen Channels insgesamt: funktioniert
sofort mit Items aus beliebigen anderen Bindings, keine Notwendigkeit für einen eigenen
`ChannelTypeProvider`, und die Bridge-Config (§4, Checkboxen in 4.3) legt bereits fest, welche
Use-Cases überhaupt aktiv sind — die Metadaten binden nur noch die konkreten Datenpunkte an
Items.

### 4.3 Bridge-Konfiguration: Use-Case-Auswahl per Checkbox

Die Bridge (`eebus:service`) bekommt zwei Multi-Select-Konfigurationsparameter (in
`thing-types.xml` als `type="text" multiple="true"` mit `<options>` aus dem Katalog in
§5.4.1 — openHAB rendert das als Checkbox-Liste):

- `supportedUseCasesClient` — welche Use-Cases openHAB bei Peers **erkennen/konsumieren**
  soll (Konsumenten-Rolle, §5.4).
- `supportedUseCasesServer` — welche Use-Cases openHAB selbst **anbieten** soll
  (Anbieter-Rolle, §5.5).

Beide Listen steuern, welche `UseCase`-Implementierungen beim Bauen der lokalen Entity in
`EEBusHandler` registriert bzw. welche `addUseCaseListener(...)`-Aufrufe abgesetzt werden.

## 5. Die vier Anforderungen im Detail

### 5.1 Schlüsselerzeugung

_Hinweis: `CertificateStorage`/`ConfigBuilder` (unten referenziert) gehören zu jeebus.ship
3.0.0. Per Entscheidung (§6.1) bleiben wir bei 2.2.0 (`ShipNodeConfiguration`) — die
`CertificateStorage`-Abstraktion selbst existiert dort nicht. Das ursprünglich hier
vorgeschlagene "Neu zu implementieren: eigene CertificateStorage" ist damit obsolet; siehe
stattdessen §6.1 und §7 Punkt 1 für den aktuellen, noch zu verifizierenden Stand._

- `jeebus.ship` 3.0.0 (nicht verwendet): `KeyManagement` lädt bestehendes Zertifikat via
  `CertificateStorage#readCertificate()`; fehlt es, wird ein neues ECDSA-P256-Zertifikat
  erzeugt und über `saveCertificate()` persistiert. Diente als Referenz für das erwartete
  Verhalten von 2.2.0.
- `EEBusHandler` übergibt `ShipNodeConfiguration` einen Keystore-Pfad unter
  `<userdata>/eebus/<bridgeUID>.jks` sowie ein X.500-DN (`CN=<deviceModel>-<serialNumber>`).
  Ob 2.2.0 bei fehlender Datei automatisch ein Zertifikat anlegt (wie 3.0.0 es tut), ist
  **offen** — siehe §7 Punkt 1.

### 5.2 Pairing — **Modell bei Implementierung präzisiert**

- `ConfigBuilder.withAutoAcceptEnabled(...)`/`ShipNodeConfiguration`s Auto-Accept-Flag und
  `ShipCommunication.withConnectClientsTo(ALL|TRUSTED|NONE)` decken automatisches und
  vorvertrautes Pairing ab.
- **Kein separates Approve/Reject-API nötig** (Revision gegenüber der ersten Fassung dieses
  Abschnitts): `ShipCommunication.withTrustedSkis(...)` **ersetzt** die komplette Trust-Menge
  bei jedem Aufruf (kein inkrementelles Hinzufügen einer einzelnen SKI über die öffentliche
  API). Das passt exakt zum openHAB-Bridge/Thing-Modell: `EEBusHandler.recomputeTrustedSkis()`
  liest bei jedem Kind-Thing-Lifecycle-Event (`childHandlerInitialized`/`childHandlerDisposed`)
  die SKIs aller konfigurierten `eebus:peer`-Things neu ein und ruft `withTrustedSkis(...)`
  erneut auf. **Ein `eebus:peer`-Thing anzulegen ist der Pairing-Vorgang**, es entfernen
  entzieht das Vertrauen. Keine bespoke Action-Methode, kein Channel dafür nötig.
- Die SHIP-Spezifikation kennt zusätzlich PIN-basiertes Pairing (`smepin`-Zustandsmaschine in
  `jeebus.ship`). **Entschieden: nicht in v1** — v1 deckt nur Accept/Reject/Pre-Trusted-SKIs
  ab (siehe Abschnitt 6).

### 5.3 Discovery mit Namen

- `ConfigBuilder.withMDnsServiceInstance(...)`, `withBrand/withType/withModel(...)` werden per
  mDNS-TXT-Record verteilt (`ServiceRegistry`, SHIP-Spec §7.3.2, max. 400 Byte TXT-Record).
- Bestehendes `EEBusRemoteService`-Record (`name`, `brand`, `model`, `deviceType`,
  `deviceCategories`) passt strukturell bereits zu dem, was `ShipService`/`MdnsEntry`
  liefern — hier ist nur die Anbindung an den neuen Adapter nötig, keine API-Änderung.

### 5.4 Use-Case-Identifizierung

- `Device.getBuilder().withDiscoverDevices(true)` aktiviert automatische
  DetailedDiscovery + UseCaseDiscovery gegenüber jedem verbundenen Peer.
- `NodeManagement.addUseCaseListener(listener, useCase, actor, scenarioRequirements,
  featureRequirements)` liefert erkannte Partner als `List<UseCasePartner>`
  (Geräte-/Entity-/Feature-Infos inkl. `getCompleteFeatureAddress(...)`).
- Alternativ: `Device.getUseCases(): Map<UseCase, Entity>` für eine Gesamtübersicht aktiver
  Use-Cases.

#### 5.4.1 Use-Case-Katalog — **ersetzt durch Primärquelle**

**Quelle geändert:** der Katalog unten stammt jetzt direkt aus Annex A ("Classification of Use
Case Actors as Client or Server Roles", Table 1) von `EEBus_UC_IG_GeneralGuidelines_V1.0.0.pdf`
— einer offiziellen EEBUS-Primärspezifikation, bereitgestellt vom Nutzer unter
`C:\Projects\eebus` (Ordner mit direkt von eebus.org heruntergeladenen Dokumenten, siehe
Quellenangaben am Ende dieses Dokuments). Er **ersetzt vollständig** die vorherige Fassung
dieses Abschnitts, die auf Web-Recherche und einer nicht abschließend verifizierten
"erweiterte/neuere Use-Cases"-Liste beruhte — diese Unsicherheit ist damit aufgelöst: die
folgende Tabelle ist die vollständige, offizielle Liste aller EEBUS-Use-Cases (Stand des
Dokuments), nicht nur ein Kern-Subset.

| Kürzel | Name | Client Actor(s) | Server Actor(s) |
|---|---|---|---|
| CDSF | Configuration of DHW System Function | Configuration Appliance | DHW Circuit |
| CDT | Configuration of DHW Temperature | Configuration Appliance | DHW Circuit |
| CRCSF | Configuration of Room Cooling System Function | Configuration Appliance | HVAC Room |
| CRCT | Configuration of Room Cooling Temperature | Configuration Appliance | HVAC Room |
| CRHSF | Configuration of Room Heating System Function | Configuration Appliance | HVAC Room |
| CRHT | Configuration of Room Heating Temperature | Configuration Appliance | HVAC Room |
| COB | Control of Battery | CEM | Inverter |
| CEVC | Coordinated EV Charging | Energy Guard, Energy Broker | EV |
| DBEVC | Dynamic Bidirectional EV Charging | CEM | EV |
| EVCEM | EV Charging Electricity Measurement | CEM | EV |
| EVCS | EV Charging Summary | Energy Broker | EVSE |
| EVCC | EV Commissioning and Configuration | CEM | EV |
| EVPS | EV Peak Shaving | Energy Manager | Charging Station Manager |
| EVSoC | EV State of Charge | Monitoring Appliance | EV |
| EVSECC | EVSE Commissioning and Configuration | CEM | EVSE |
| EPRQ | Extra Power Request | Transmission Broker | CEM |
| FLOA | Flexible Load | CEM | Energy Consumer |
| FSWG | Flexible Start for White Goods | CEM | Smart Appliance |
| ITPCM | Incentive-Table based power consumption management | CEM | Energy Consumer |
| LPC | Limitation of Power Consumption | Energy Guard | Controllable System |
| LPP | Limitation of Power Production | Energy Guard | Controllable System |
| MCSGRC | Monitoring and Control of Smart Grid Ready Conditions | CEM | Heat Pump |
| MOB | Monitoring of Battery | Monitoring Appliance | Battery |
| MDSF | Monitoring of DHW System Function | Monitoring Appliance | DHW Circuit |
| MDT | Monitoring of DHW Temperature | Monitoring Appliance | DHW Circuit |
| MGCP | Monitoring of Grid Connection Point | Monitoring Appliance | Grid Connection Point |
| MOI | Monitoring of Inverter | Monitoring Appliance | Inverter |
| MOT | Monitoring of Outdoor Temperature | Monitoring Appliance | Outdoor Temperature Sensor |
| MPC | Monitoring of Power Consumption | Monitoring Appliance | Monitored Unit |
| MPS | Monitoring of PV String | Monitoring Appliance | PV String |
| MRCSF | Monitoring of Room Cooling System Function | Monitoring Appliance | HVAC Room |
| MRHSF | Monitoring of Room Heating System Function | Monitoring Appliance | HVAC Room |
| MRT | Monitoring of Room Temperature | Monitoring Appliance | HVAC Room |
| NID | Node Identification | Visualization Appliance | Identifiable Node |
| OHPCF | Optimization of Self Consumption by Heat Pump Compressor Flexibility | CEM | Compressor |
| OSCEV | Optimization of Self-Consumption During EV Charging | CEM | EV |
| OPEV | Overload Protection by EV Charging Current Curtailment | Energy Guard | EV |
| PODF | Power Demand Forecast | Energy Broker, Transmission Broker | CEM |
| POEN | Power Envelope | Transmission Broker | CEM |
| TOUT | Time of Use Tariff | Energy Broker, Transmission Broker | CEM |
| VABD | Visualization of Aggregated Battery Data | Visualization Appliance | Battery System |
| VAPD | Visualization of Aggregated Photovoltaic Data | Visualization Appliance | PV System |
| VHAN | Visualization of Heating Area Name | Visualization Appliance | Heating Circuit, Heating Zone, HVAC Room |
| VPCHPC | Visualization of Electrical Power Consumption of the Heat Pump Compressor | CEM | Heat Pump Compressor |

**Korrekturen gegenüber der alten, unverifizierten Liste:** "OPEV"/"OSCEV" waren vertauscht
benannt (OSCEV ist tatsächlich "Optimization of Self-Consumption During EV Charging", nicht
"Overload Protection by Setpoint Curtailment" — dieses Use Case existiert laut Primärquelle so
nicht); "FSWG_IOT" heißt korrekt "FSWG"; "FLOA" ist tatsächlich "Flexible Load" (nicht wie
vermutet "Flexible Load/Heating Rod"); "SBEVC" (Scheduled/Smart Battery EV Charging) taucht in
der offiziellen Liste **nicht** auf und wird gestrichen.

**Entschieden (2026-07-30):** `thing-types.xml`s `supportedUseCasesClient`/
`supportedUseCasesServer`-Checkboxen (§4.3) listeten bislang nur die alten 13
"Kern-Use-Cases" als Optionen. Auf Anweisung ("Alles rein") wurde die Options-Liste auf den
vollen, oben verifizierten Katalog (43 Einträge) erweitert — inklusive Korrektur der zuvor
falschen OSCEV-Beschreibung. Umgesetzt, siehe §7 Punkt 11.

- **Bei Implementierung zurückgestellt, zwei offene Voraussetzungen:**
  1. `NodeManagement.addUseCaseListener(...)` braucht die exakten `scenarioRequirements`
     ("Scenario implementation requirements for Actors") und
     `communicationPartnerFeatureRequirements` ("Feature Types and Functions used within
     this Use Case") aus den jeweiligen EEBUS-Use-Case-Spezifikationen (LPC, LPP,
     Measurement) — **teilweise beschafft, siehe §5.4.2 und §7 Punkt 3.**
  1. `UseCasePartner.getCommunicationAddress()` muss auf die SKI eines `eebus:peer`-Things
     zurückgeführt werden können. **Bestätigt und gelöst:** `communicationAddress` ist ein
     `"ip:port"`- bzw. `"[ipv6]:port"`-String (verifiziert anhand
     `ServiceRegistry#getIpAndPort(ServiceInfo)`, `ship:2.2.0`-Quellcode), nicht die SKI.
     `EEBusMdnsBrowser` (§4.1, §7 Punkt 9, **umgesetzt**) baut denselben String selbst aus den
     mDNS-`ServiceInfo`-Daten auf und liefert damit
     `skiForCommunicationAddress(String): Optional<String>` — reicht aus, um einen
     `UseCasePartner` auf ein `eebus:peer`-Thing zurückzuführen, sobald Punkt (3) die
     Listener-Registrierung selbst ermöglicht.
  Bis beide Punkte geklärt sind, bleibt `EEBusPeerHandler` ohne Kanäle (Platzhalter mit
  Bridge-Status-Weiterleitung), siehe Code-Kommentar in `EEBusPeerHandler`.

#### 5.4.2 Scenario-/Feature-Tabellen — LPC/LPP jetzt primärquellen-verifiziert (§7 Punkt 3)

**Quellenlage aktualisiert:** der Nutzer hat unter `C:\Projects\eebus` echte, direkt von
eebus.org heruntergeladene Primärspezifikationen bereitgestellt (`pdftotext -layout` erfolgreich
extrahiert, anders als frühere PDF-Abrufversuche in dieser Session). Für **LPC und LPP** liegen
damit `EEBus_UC_TS_LimitationOfPowerConsumption_V1.0.0_public.pdf` und
`EEBus_UC_TS_LimitationOfPowerProduction_V1.0.0_public.pdf` als Primärquelle vor — die Tabellen
unten sind direkt daraus übernommen (Table 2, Table 21 des jeweiligen Dokuments), nicht mehr
aus der `eebus-go`-Sekundärquelle abgeleitet. Für MPC und MGCP liegt **keine** TS-PDF in diesem
Ordner vor (nur SPINE/SHIP-Kernspezifikationen sowie LPC/LPP-spezifische Dokumente) — deren
Tabellen bleiben auf dem bisherigen `eebus-go`-Stand (siehe unten).

**LPC** (`EEBus_UC_TS_LimitationOfPowerConsumption_V1.0.0_public.pdf`, Actors: Energy Guard =
Client, Controllable System = Server):

Table 2 (Scenario implementation requirements for Actors):

| Szenario | Name | Energy Guard | Controllable System |
|---|---|---|---|
| 1 | Control active power consumption limit | M | M |
| 2 | Failsafe values | M | M |
| 3 | Heartbeat | M | M |
| 4 | Constraints | M | R |

Table 21 (Feature Types and Functions used within this Use Case by the Actor Controllable
System — das ist die für `EEBusMpcServerUseCase`-artige Server-Implementierungen relevante
Richtung, siehe §5.5):

| Feature Type | Szenario:Function | Possible operations |
|---|---|---|
| LoadControl | 1:M `loadControlLimitDescriptionListData` | read (M), partial (R) |
| LoadControl | 1:M `loadControlLimitListData` | read (M), partial (R), write (M), partial (M) |
| DeviceConfiguration | 2:M `deviceConfigurationKeyValueDescriptionListData` | read (M), partial (R) |
| DeviceConfiguration | 2:M `deviceConfigurationKeyValueListData` | read (M), partial (R), write (M), partial (M) |
| DeviceDiagnosis | 3:M `deviceDiagnosisHeartbeatData` | read (M) |
| ElectricalConnection | 4:M `electricalConnectionCharacteristicListData` | read (M), partial (R) |

Permitted `entityType`s for Actor Controllable System: CEM, Compressor, EVSE,
HeatPumpAppliance, Inverter, SmartEnergyAppliance, SubMeterElectricity (deckt sich mit dem
"Wechselrichter"-Anwendungsfall — Inverter ist explizit erlaubt).

Auf der Energy-Guard-Seite (Table 12, falls openHAB die EG/Client-Rolle spielt) ist nur
`DeviceDiagnosis` (Szenario 3, `deviceDiagnosisHeartbeatData`, read) als Server-Feature nötig —
der eigentliche Datenfluss (Limit schreiben) läuft dort als Client gegen die CS-Seite.

**LPP** (`EEBus_UC_TS_LimitationOfPowerProduction_V1.0.0_public.pdf`) ist strukturell
**identisch** zu LPC (gleiche Szenario-Tabelle, gleiche Feature-/Funktionstabelle, nur
Vorzeichen/Naming gespiegelt: `FailsafeProductionActivePowerLimit` statt
`FailsafeConsumptionActivePowerLimit`, `ActivePowerProductionLimit` statt
`ActivePowerConsumptionLimit`). Einzige inhaltliche Abweichung: die Liste der erlaubten
`entityType`s für den Controllable System ist enger — CEM, EVSE, Inverter,
SmartEnergyAppliance, SubMeterElectricity (kein Compressor, kein HeatPumpAppliance).

**Konsequenz:** die zuvor bestehende Unsicherheit auf Funktions-Ebene ("nicht anhand von
Primärtext verifiziert") ist für LPC/LPP jetzt vollständig aufgelöst — die Tabellen oben wurden
1:1 in `FeatureRequirement`s der `AbstractEEBusLimitControllableSystemUseCase`-Implementierung
(Server-Rolle) übersetzt, siehe §7 Punkt 13. Die Client-Rolle (openHAB als Energy Guard, die
einen fremden Controllable System-Peer steuert) bleibt weiterhin offen.

**MPC und MGCP bleiben auf Sekundärquellen-Stand** (`eebus-go`, siehe unten) — keine TS-PDF für
diese beiden in `C:\Projects\eebus` vorhanden, nur für LPC/LPP.

**MPC** (`usecases/ma/mpc`) — im Referenzcode die **Client-/Konsumentenseite** (trotz Package-
Name "ma" registriert `AddFeatures()` nur Client-Features — wir lesen also von einem Peer, der
selbst als "MonitoredUnit" auftritt). Gültige Partner-Entity-Typen (bestätigt u. a.
`EntityTypeTypeInverter`, `EntityTypeTypeEVSE`, `EntityTypeTypeSubMeterElectricity`,
`EntityTypeTypeHeatPumpAppliance` — deckt den ursprünglichen "Wechselrichter"-Anwendungsfall
des Nutzers ab):

| Szenario | Mandatory | Server-Features (Typ, beim Peer) |
|---|---|---|
| 1 | ja | ElectricalConnection, Measurement |
| 2 | nein | ElectricalConnection, Measurement |
| 3 | nein | ElectricalConnection, Measurement |
| 4 | nein | ElectricalConnection, Measurement |
| 5 | nein | ElectricalConnection, Measurement |

Client-Features (unsere Bridge, Konsumentenrolle): `ElectricalConnection`, `Measurement`
(jeweils Client). **Offen:** welche der fünf Szenarien welchem der von jeebus.spine bereits
bereitgestellten Measurement-Funktionsbausteine entspricht (`MeasurementListDataFunction` =
Gesamtwert vermutlich Szenario 1, `MeasurementSeriesListDataFunction` = Zeitreihe, je-Phase
via `ElectricalConnectionCharacteristicListData`/`ParameterDescriptionListData` — nicht anhand
des Referenzcodes, nur anhand von jeebus.spine-Klassennamen vermutet, **nicht verifiziert**).

**MGCP** (`usecases/ma/mgcp`) — ebenfalls Client-/Konsumentenseite im Referenzcode, Partner-
Entity-Typen `CEM`/`GridConnectionPointOfPremises`:

| Szenario | Mandatory | Server-Features (Typ, beim Peer) |
|---|---|---|
| 1 | nein | DeviceConfiguration |
| 2 | ja | Measurement, ElectricalConnection |
| 3 | ja | Measurement, ElectricalConnection |
| 4 | ja | Measurement, ElectricalConnection |
| 5 | nein | Measurement, ElectricalConnection |
| 6 | nein | Measurement, ElectricalConnection |
| 7 | nein | Measurement, ElectricalConnection |

Client-Features (unsere Bridge): `DeviceConfiguration`, `ElectricalConnection`, `Measurement`
(jeweils Client).

**Für alle übrigen Use-Cases des vollständigen Katalogs (§5.4.1, ~36 insgesamt) weiterhin
offen** — für einige davon (EVSECC, EVCC, EVCEM, EVSoC, OPEV, OSCEV, CEVC, VABD, VAPD) existiert
vermutlich ebenfalls Quellcode unter `eebus-go` (`usecases/cem/*`/`usecases/ev/*`), aber noch
nicht recherchiert; für die übrigen, neu bekannt gewordenen Use-Cases (CDSF, CDT, CRCSF, CRCT,
CRHSF, CRHT, EVCS, EVPS, ITPCM, MCSGRC, MOB, MDSF, MDT, MOI, MOT, MPS, MRCSF, MRHSF, MRT, NID,
TOUT, VHAN, VPCHPC) wurde noch gar nicht recherchiert, ob und wo TS-PDFs oder Referenzcode
vorliegen.

**Konsequenz für die Umsetzung:** Die Feature-**Typ**-Ebene (was jeebus.spine als
`FeatureRequirement`/`CommunicationPartnerFeatureRequirement` mindestens braucht) ist für
LPC/LPP/MPC/MGCP jetzt verifiziert belegt. Die Funktions-Ebene (`FunctionEnumType` je
Szenario) wird beim eigentlichen Implementieren (§7 Punkt 8) pragmatisch anhand der
jeebus.spine-`FeatureFunction`-Klassen (`utils.features.*`, die bereits genau diese
Funktionsbausteine kapseln) abgeleitet und mit einem echten Peer getestet, statt weiter auf
den PDF-Text zu warten — Risiko: falsch abgeleitete `PresenceIndication`-Werte führen bestenfalls
zu einer zu strengen/zu laxen Validierung, nicht zu Datenkorruption (siehe
`MeasurementFeature#setStrictMode`, das genau für diesen Fall existiert).

### 5.5 Use-Case-Bereitstellung (Server-Rolle) — **neu, durch Rückfrage aufgedeckt**

Bisher baut `EEBusHandler` die lokale SPINE-`Entity` ohne `.withUseCases(...)` — openHAB tritt
also aktuell nur als Konsument auf (erkennt fremde Use-Cases), bietet aber selbst keinen an.
Für Szenarien wie "openHAB bietet einen Messwert-Use-Case aus einem Wechselrichter-Thing für
andere EEBUS-Teilnehmer an" reicht das nicht — das Pairing wäre für diese Richtung wirkungslos.

Mechanismus (verifiziert anhand `org.openmuc.jeebus.spine.spi.UseCase` und
`spine-demo/ExampleUseCase.java`):

- Eine Java-Klasse implementiert `UseCase` (`getActor()`, `getName()`, `getVersion()`,
  `getScenarioSupport()`, `getAddress()`, `setup()`, `close()`).
- `getFeatureRequirements(EntityTypeEnumType)` gibt ein Set von `FeatureRequirement`
  zurück, jeweils mit `FeatureTypeEnumType` + `RoleType` (`CLIENT` oder **`SERVER`**) +
  den zu implementierenden `FeatureFunction`-Klassen (`utils.features.*`-Pakete, z. B.
  `measurement`, `loadcontrol`, `devicediagnosis`). **`RoleType.SERVER`** ist die Rolle, die
  wir für "openHAB bietet etwas an" brauchen — SPINE hängt dann automatisch ein
  Server-Feature mit diesen Functions an die Entity.
- Die Instanz wird beim Bauen der Entity registriert:
  `.addEntity().setType(...).withUseCases(new MeinUseCase()).applyToDevice()`.
- **Geklärt (Grundmechanismus):** echte Werte fließen über `eebus`-Item-/Rule-Metadaten
  (§4.2) in das Server-Feature — der Nutzer koppelt ein beliebiges Item (z. B. aus dem
  Wechselrichter-Binding) an den Datenpunkt, den die `UseCase`-Implementierung erwartet.
  ("imSys" war kein spezifischer Use-Case, sondern der Sammelbegriff des Nutzers für
  "intelligentes Messsystem"/Monitoring-Datenquelle — betrifft am ehesten MPC/MGCP, siehe
  Katalog in §5.4.1.)
- **Entschieden:** Server- und Client-Rolle werden beide für den aktuellen Umsetzungsschritt
  eingeplant (nicht zurückgestellt) — siehe §4.3 (Checkbox-Konfiguration) und §7.
- **Weiterhin offen:** die konkrete `UseCase`-Implementierung pro Server-Use-Case (welche
  `FeatureFunction`-Klassen aus `utils.features.*` zu verwenden sind) hängt an §7 Punkt 3
  (Scenario-/Feature-Tabellen aus der Spezifikation) — ändert sich durch die Metadaten-Klärung
  nicht.

## 6. Entscheidungen

Alle sechs Punkte wurden durchgesprochen und sind getroffen bzw. geklärt:

1. **Versions-Strategie — bestätigt (2026-07-30, nach Fehldiagnose korrigiert):**
   `spine:4.0.1` (zieht `ship:2.2.0` transitiv) **ist** die offizielle, auf Maven Central
   veröffentlichte Kombination — direkt gegen `repo1.maven.org` verifiziert (beide POMs
   abgerufen: `spine:4.0.1` deklariert `ship:2.2.0` als Dependency, beide unter EPL-2.0). Eine
   frühere Zwischen-Diagnose ("`4.0.1`/`2.2.0` seien nicht offiziell veröffentlicht, nur
   `3.0.0`/`3.1.0` mit `ship:2.1.1` unter AGPL") beruhte auf einer unvollständigen
   `search.maven.org`-Abfrage und war falsch — inzwischen per direktem POM-Abruf richtiggestellt
   und die versehentliche Herabstufung auf `spine:3.1.0` in `pom.xml` zurückgenommen.
   **Tatsächliche Ursache des `ShipNodeConfiguration cannot be resolved`-Fehlers:** eine lokal
   verunreinigte Maven-Repository-Cache-Eintragung — vermutlich ein lokal per
   `mvn install`/Gradle-Publish erzeugter `ship`-Build unter der Versionsbezeichnung `2.2.0`,
   der aber tatsächlich aus einem neueren Checkout-Stand stammt (`jeebus.ship` lokal bei
   `v2.3.0` + 7 Commits, `ShipNodeConfiguration` dort bereits durch `ConfigBuilder`/`ShipConfig`
   ersetzt) und damit den echten, von Maven Central herunterladbaren `ship:2.2.0`-Jar unter
   derselben Koordinate verdeckt. Lokales Cache-Löschen
   (`~/.m2/repository/org/openmuc/jeebus/`) und `mvn -U` behoben den Fehler jedoch **nicht** —
   derselbe Fehler blieb bestehen, was die reine Cache-Theorie widerlegt. **Wahrscheinlichere
   Ursache:** ein `<dependencyManagement>` irgendwo in der Eltern-POM-Kette des
   `openhab-addons`-Reactors (oder ein anderes Bundle im Reactor) pinnt `org.openmuc.jeebus:ship`
   auf eine andere Version, die die transitive `2.2.0` von `spine:4.0.1` überschreibt. **Fix:**
   `ship:2.2.0` zusätzlich als explizite direkte Dependency in `pom.xml` ergänzt — eine direkte
   Dependency mit fester Versionsnummer gewinnt in Maven immer gegen eine geerbte
   `dependencyManagement`-Version, unabhängig davon, was von `spine` transitiv kommt. Noch nicht
   durch einen erneuten Build bestätigt.
1. **Lizenz — bestätigt: EPL-2.0.** Sowohl `spine:4.0.1` als auch `ship:2.2.0` sind laut ihren
   auf Maven Central veröffentlichten POMs unter EPL-2.0 lizenziert (direkt verifiziert, siehe
   oben) — kein Blocker für die Einbindung in ein openHAB-Add-on. Die zwischenzeitliche
   AGPL-Einschätzung (s. o.) war eine Fehldiagnose und ist damit hinfällig.
1. **Bridge-Modell — entschieden: 1 Bridge = 1 SPINE-`Device`, exklusiv.** Keine Bridge teilt
   sich ein Zertifikat/eine SKI mit einer anderen. Braucht ein Anwendungsfall zwei Rollen
   (z. B. CEM + Monitoring), führt das zu zwei unabhängigen Bridges mit je eigenem
   `Device`-Objekt, nicht zu geteilter Identität.
1. **PIN-Pairing — entschieden: nicht in v1.** v1 deckt Accept/Reject/Pre-Trusted-SKIs ab.
   Die SHIP-PIN-Zustandsmaschine (`smepin`) bleibt vorerst ungenutzt; kann als Erweiterung
   nachgezogen werden, falls ein Gerät PIN-Pairing erzwingt.
1. **Channel-Mapping-Tiefe — entschieden: feste Tabelle zuerst.** Nur LPC/LPP/Measurement
   bekommen vordefinierte Channel-Sets für v1. Generisches Feature→Channel-Mapping für
   beliebige Use-Cases ist ein späterer Ausbauschritt.
1. **Persistenz der SKI-Zuordnung — technische Festlegung, keine offene Diskussion:**
   `shipId` wird beim Handshake gelernt (`EEBusEventHandler#onServiceShipIdUpdate`) und über
   `updateConfiguration()` in der Peer-Thing-Konfiguration gespeichert, nicht über einen
   Channel.

## 7. Umsetzungsstatus / Nächste Schritte

Erledigt:

- `org.openmuc.jeebus:spine:4.0.1` als Dependency in `pom.xml` ergänzt.
- `EEBusHandler` (Bridge, `eebus:service`): Zertifikatsablage unter
  `<userdata>/eebus/<bridgeUID>.jks`, `ShipCommunication`/`Device`-Aufbau,
  Trusted-SKI-Berechnung aus Kind-Things (siehe §5.2).
- `EEBusPeerHandler` (`eebus:peer`): Konfiguration, Bridge-Status-Weiterleitung.
- `thing-types.xml` und `EEBusHandlerFactory` auf Bridge/Peer umgestellt.
- `supportedUseCasesClient`/`supportedUseCasesServer` Checkbox-Parameter in `thing-types.xml`
  (voller Use-Case-Katalog aus §5.4.1, siehe §7 Punkt 11) und `EEBusConfiguration` ergänzt;
  `EEBusHandler` verdrahtet MPC in beiden Rollen (§7 Punkte 8, 12), alle anderen Einträge
  werden weiterhin nur geloggt (siehe §7 Punkt 3 für den jeweils nötigen Tabellen-Nachschub).
- `EEBusMdnsBrowser` (eigener JmDNS-Browser für `_ship._tcp.local.`, `org.jmdns:jmdns:3.6.3`
  als neue Dependency) implementiert und in `EEBusHandler` verdrahtet (§7 Punkt 9) — löst
  Discovery-Namen und `communicationAddress`↔SKI-Korrelation.
- `EEBusMetadataService` (`eebus`-Item-Metadaten, einziger OSGi-`EventSubscriber` des Bundles),
  `EEBusMpcServerUseCase` (Server-Rolle) und `EEBusMpcClientUseCase` (Client-Rolle, Gegenstück)
  für MPC umgesetzt und in `EEBusHandlerFactory`/`EEBusHandler` verdrahtet (§7 Punkte 6, 8, 12).
- `EEBusDiscoveryService` (Bridge-scoped Discovery-Inbox auf Basis von `EEBusMdnsBrowser`)
  umgesetzt und über `EEBusHandler#getServices()` registriert (§7 Punkt 10).

Offen, mit klar benannter Blockade — auch als `// TODO(CONCEPT §7.N)` im Code markiert, siehe
Dateiverweise:

- [x] **(1) Verifiziert:** `ShipNodeConfiguration` (ship 2.2.0) legt den Keystore automatisch
      an, wenn die Datei unter dem konfigurierten Pfad fehlt (§6 Entscheidung 1, Quelle unten).
      Dabei nebenbei einen Bug in `EEBusHandler#startShipSpine` gefunden und behoben: 4.
      Konstruktor-Parameter ist `keepAlive`, nicht `autoAccept` — jetzt korrekt über
      `ShipCommunication#withAutoAcceptMode(...)` verdrahtet.
- [x] **(2) Verifiziert:** `UseCasePartner#getCommunicationAddress()` ist die IP, nicht die
      SKI — Mapping-Schritt nötig, Lösungsweg gefunden (§4.1), Umsetzung siehe Punkt (9).
      → `EEBusPeerHandler.java`, Klassenkommentar + `initialize()`.
- [x] **(3) LPC/LPP primärquellen-verifiziert, MPC/MGCP weiterhin sekundär, Katalog
      vollständig:** Nutzer hat echte EEBUS-Primärspezifikationen unter `C:\Projects\eebus`
      bereitgestellt (`pdftotext -layout` erfolgreich, anders als frühere Abrufversuche). Für
      LPC/LPP damit Szenario- UND Funktions-Ebene vollständig aus der TS-PDF verifiziert (siehe
      §5.4.2, Table 2 + Table 21 je Dokument) — die zuvor offene Funktions-Granularität ist für
      diese beiden gelöst. MPC/MGCP bleiben auf `eebus-go`-Sekundärquellen-Stand (keine TS-PDF
      dafür vorhanden). **Zusätzlich:** der komplette Use-Case-Katalog (§5.4.1) wurde durch
      Annex A der `EEBus_UC_IG_GeneralGuidelines_V1.0.0.pdf` ersetzt — ~36 Use-Cases statt der
      alten, teils unverifizierten 13+9-Liste, inkl. Korrektur mehrerer falscher/unsicherer
      Namen (OSCEV, FSWG, FLOA; SBEVC existiert laut Primärquelle nicht).
      → `EEBusPeerHandler.java`, Klassenkommentar + `initialize()`.
- [x] **(4) Entschieden:** kein Erweiterungswunsch an jeebus.ship/jeebus.spine nötig —
      eigener JmDNS-Browser (§4.1) ist machbar und löst Discovery-Inbox + Punkt (2)
      gemeinsam. Umsetzung selbst ist Punkt (9), weiterhin offen.
      → `EEBusPeerConfiguration.java`, Feld `ski`.

Diese Liste ist die Referenz — Häkchen hier setzen, wenn ein Punkt geklärt ist, und den
zugehörigen `// TODO(CONCEPT §7.N)`-Kommentar im Code dann entfernen oder auflösen.

Neu hinzugekommen durch die Anbieter-Rolle-Rückfrage (§5.5, §4.2, §4.3):

- [x] **(5) Umgesetzt:** `supportedUseCasesClient`/`supportedUseCasesServer`
      Checkbox-Konfigurationsparameter in `thing-types.xml` (Optionsliste aus §5.4.1) — seit
      Punkt (11) der volle, primärquellen-verifizierte Katalog von 43 Use-Cases als Optionen.
- [x] **(6) Umgesetzt:** `EEBusMetadataService` implementiert — `find(useCase, dataPoint,
      peer)`, `readState(itemName)`, `sendCommand(itemName, command)`, sowie
      `register/unregisterItemStateListener(...)`. Läuft als einziger OSGi-`EventSubscriber`
      dieses Bundles (`AbstractItemEventSubscriber`, verifiziert anhand openHAB-Core-Javadoc);
      einzelne `UseCase`-Implementierungen abonnieren Item-Änderungen darüber statt sich selbst
      am Event-Bus zu registrieren. **Wichtiger Korrektur gegenüber der ursprünglichen
      Entwurfsbeschreibung:** kein "Server-Feature fragt Item-State ab"-Callback — verifiziert
      anhand `ReadListFeatureFunction`/`DataListHolder` (jeebus.spine) ist das Modell
      pull-aus-Cache: `addData(...)`/`updateData(...)` schreiben in einen lokalen Cache, aus dem
      SPINE Lese-Anfragen und Subscription-Benachrichtigungen automatisch bedient — das Binding
      muss also bei Item-Änderungen proaktiv in diesen Cache schreiben, nicht auf eine
      Leseanfrage warten.
      → `EEBusMetadataService.java` (neu).
- [x] **(7) Entschieden — kein eigener Mechanismus nötig:** ein separater Rule-Tag-Mechanismus
      wäre redundant zum Item-Metadaten-Ansatz; Proxy-Items + normale Rules decken denselben
      Bedarf ab, siehe §4.2.1 (Begründung: kein synchroner Rückgabewert aus
      `RuleManager.runNow(...)` verfügbar, kein neuer `ModuleType` nötig).
- [x] **(8) Erster Use-Case umgesetzt:** `EEBusMpcServerUseCase` — MPC in Server-Rolle,
      genau der vom Nutzer ursprünglich genannte "Wechselrichter/ImSys"-Anwendungsfall.
      Bewusst eng begrenzter Umfang: nur Szenario 1 (Gesamt-Wirkleistung,
      `ScopeTypeEnumType.AC_POWER_TOTAL`), nur `Measurement`-Server-Feature (kein
      `ElectricalConnection` — dafür fehlt eine Server-seitige Referenz in `eebus-go`, siehe
      Klassenkommentar). In `EEBusHandler` verdrahtet: wird nur registriert, wenn `"MPC"` in
      `supportedUseCasesServer` enthalten ist. Bedient sich bei `EEBusMetadataService` für die
      `eebus="MPC.power"`-Zuordnung. **Für die übrigen 12 Kern-Use-Cases (inkl. LPC/LPP, deren
      Server-Rolle den Schreib-Pfad testen würde) weiterhin offen** — abhängig von Punkt (3)
      für die jeweils anderen Use-Cases.
      → `EEBusMpcServerUseCase.java` (neu), `EEBusHandler.java` (`startShipSpine()`).
- [x] **(9) Umgesetzt:** eigener JmDNS-Browser für `_ship._tcp.local.` (TXT-Record-Parsing
      gemäß SHIP 7.3.2), der eine `communicationAddress`↔SKI-Zuordnung pflegt — löst gemeinsam
      Discovery-Namen (Anforderung 3) und Use-Case-Partner-Korrelation (2). Neue Dependency
      `org.jmdns:jmdns:3.6.3` in `pom.xml` ergänzt (nicht auf `jeebus.ship`-interne Pakete, die
      sind laut Export-Package nicht nutzbar). Format `"ip:port"`/`"[ipv6]:port"` verifiziert
      anhand `ServiceRegistry.java` (Quelle unten).
      → `EEBusMdnsBrowser.java` (neu); `EEBusHandler.java` (Start/Close-Lifecycle,
      `getMdnsBrowser()`); `EEBusPeerHandler.java` (Klassenkommentar aktualisiert).
- [x] **(10) Umgesetzt:** `EEBusDiscoveryService extends AbstractThingHandlerDiscoveryService
      <EEBusHandler>` (`PROTOTYPE`-Scope, verifiziert anhand des offiziellen openHAB-
      Binding-Entwickler-Leitfadens, Abschnitt "Discovery that is bound to a Bridge") —
      abonniert `EEBusMdnsBrowser` als `Listener`, meldet unpaired Services (SKI nicht in
      `EEBusHandler#pairedSkis()`) über `thingDiscovered(...)` als `eebus:peer`-Vorschlag mit
      SKI und Marke/Modell als Label. `EEBusHandler#getServices()` registriert sie.
      → `EEBusDiscoveryService.java` (neu), `EEBusHandler.java` (`getServices()`,
      `pairedSkis()`).
- [x] **(11) Entschieden ("Alles rein") und umgesetzt:** Checkbox-Optionsliste in
      `thing-types.xml` (`supportedUseCasesClient`/`supportedUseCasesServer`, §4.3) auf den
      vollen, primärquellen-verifizierten Katalog von 43 Use-Cases (§5.4.1) erweitert —
      identische Optionsliste für beide Parameter. Dabei die zuvor falsche OSCEV-Beschreibung
      ("Overload Protection by Setpoint Curtailment of EV Charging") auf die verifizierte
      "Optimization of Self-Consumption During EV Charging" korrigiert.
      → `thing-types.xml` (`supportedUseCasesClient`, `supportedUseCasesServer`).
- [x] **(12) Erste Client-Rolle-UseCase umgesetzt:** `EEBusMpcClientUseCase` — Gegenstück zu
      `EEBusMpcServerUseCase` (Punkt 8): erkennt Peers, die MPC als `"CEM"`-Server-Actor
      anbieten (`NodeManagement#addUseCaseListener`, verifiziert anhand jeebus.spine's Demo
      `ExampleUseCase`), löst die `measurementId` über `MeasurementDescriptionListData`
      auf (Fallback-Kette: `scopeType==ACPowerTotal` → `measurementType==Power` → einziger
      Eintrag) und schreibt den Wert per `eebus="MPC.power" [peer="..."]`-Metadaten-Item via
      neuer `EEBusMetadataService#updateState(...)` (Gegenstück zu `sendCommand`, postet ein
      `ItemStateEvent` statt eines Commands — verifiziert anhand
      `ItemEventFactory#createStateEvent` Javadoc). Architektur-Klarstellung dabei gefunden:
      `addUseCaseListener` ist am lokalen `CEM`-Entity registriert (ein Aufruf für alle Peers),
      nicht pro-Peer — Routing zum passenden `eebus:peer`-Thing läuft daher zentral in
      `EEBusHandler#startShipSpine` über einen neuen `peerThingUidForCommunicationAddress`-
      Resolver (kombiniert `EEBusMdnsBrowser#skiForCommunicationAddress` +
      `EEBusHandler#peerThingUidForSki`). `EEBusPeerHandler`s Klassenkommentar, der noch von
      Pro-Peer-Wiring ausging, wurde entsprechend korrigiert.
      **Nebenbei-Bugfix:** `EEBusMpcServerUseCase#getActor()` gab fälschlich den Client-Actor
      `"MonitoringAppliance"` zurück statt des Server-Actors `"CEM"` (jetzt eindeutig dank
      Punkt 11's primärverifiziertem Katalog).
      **Bewusst eng begrenzt** (wie Punkt 8): nur Szenario 1, nur `Measurement`-Feature, nur
      MPC von den ~43 Use-Cases — die übrigen bleiben in `supportedUseCasesClient` weiterhin
      nur geloggt, siehe Punkt (3)/§5.4.2 für den jeweils nötigen Scenario/Feature-Tabellen-
      Nachschub (nur LPC/LPP sind bislang primärquellen-verifiziert).
      → `EEBusMpcClientUseCase.java` (neu), `EEBusMetadataService.java` (`updateState`),
      `EEBusHandler.java` (`startShipSpine()`, `peerThingUidForSki`,
      `peerThingUidForCommunicationAddress`), `EEBusMpcServerUseCase.java` (`getActor()`-Fix),
      `EEBusPeerHandler.java` (Klassenkommentar).
- [x] **(13) LPC/LPP Server-Rolle ("Controllable System") umgesetzt, alle 3 Pflicht-Szenarien**
      (Nutzerentscheidung: volle Zustandsmaschine statt Minimal-Scope). Beim genaueren
      Quellenstudium zwei wichtige, zuvor nicht bekannte Details gefunden:
      1. Szenario 2 ("Failsafe values") hat **zwei** Datenpunkte, nicht einen —
         `FailsafeConsumptionActivePowerLimit`/`FailsafeProductionActivePowerLimit` UND
         `FailsafeDurationMinimum` (§4.2 Datenpunkt-Tabelle korrigiert, siehe Punkt oben).
      2. Die drei Pflicht-Szenarien bilden zusammen eine gekoppelte 5-Zustands-Automat
         (init/limited/unlimited-controlled/unlimited-autonomous/failsafe, 12 Übergänge,
         Heartbeat-Timeout 120s), keine drei unabhängigen Features — implementiert in
         `EEBusLimitControlStateMachine` (siehe deren Klassenkommentar für eine bewusste,
         offengelegte Vereinfachung: ein einzelner Heartbeat-Watchdog statt zweier separater
         120s-Fenster, da die sicherheitsrelevante Eigenschaft — "einem stillen Energy Guard
         nicht mehr vertrauen" — davon unberührt bleibt).
      **Primärquellen-verifiziert:** Wire-Format-Actor-Strings `"EnergyGuard"`/
      `"ControllableSystem"` (nicht die Katalog-Klarnamen mit Leerzeichen) direkt aus
      `EEBus_UC_IG_GeneralGuidelines_V1.0.0.pdf` zitiert; dieselbe Stelle bestätigt auch das
      **mutuelle** Heartbeat-Design (beide Actors bieten ihren eigenen
      `DeviceDiagnosis`-Heartbeat an — CS über `HeartbeatDataFunction#startHeartbeat()`
      (selbst-perpetuierend, kein eigener Polling-Code nötig), EG-Heartbeat wird über
      `NodeManagement#addUseCaseListener` + Subscription auf die gefundene Partner-Adresse
      beobachtet, exakt das Muster aus jeebus.spine's Demo `ExampleUseCase`).
      **Architektur-Fund:** `DeviceConfiguration`-Key/Values dürfen nicht direkt über
      `addData`/`updateData` verwaltet werden — die vorgesehene API ist
      `KeyValueInitialData`/`RunningKeyValue` (verifiziert im jeebus.spine-Quellcode), die
      Key-IDs und Schreibrechte selbst verwaltet.
      **Bewusst offen gelassen** (nicht Teil dieses Schritts): Szenario 4 ("Constraints", nur
      "Recommended" für CS); Persistenz der Failsafe-Werte über Neustarts hinweg (Spec: SHOULD,
      nicht SHALL); mehr als ein gleichzeitiger Energy-Guard-Peer.
      → `EEBusLimitControlState.java` (neu), `EEBusLimitControlStateMachine.java` (neu),
      `AbstractEEBusLimitControllableSystemUseCase.java` (neu), `EEBusLpcServerUseCase.java`
      (neu), `EEBusLppServerUseCase.java` (neu), `EEBusHandler.java` (`startShipSpine()`).

## Quellen

Für den Use-Case-Katalog in §5.4.1 **ursprünglich** herangezogen (Web-Recherche, inzwischen
durch die Primärquelle unten ersetzt — hier nur noch zur Nachvollziehbarkeit der Historie):

- [EEBUS Overview Use Cases v1.7 (eebus.org)](https://www.eebus.org/wp-content/uploads/2023/04/20221222-EEBUS-Overview-Use-Cases-v1.7.pdf)
- [enbility/eebus-go – Overview (DeepWiki)](https://deepwiki.com/enbility/eebus-go)
- [enbility/eebus-go – Energy Guard (EG) Use Cases (DeepWiki)](https://deepwiki.com/enbility/eebus-go/3.2-energy-guard-(eg)-use-cases)

Für §5.4.1 (vollständiger Katalog) und §5.4.2 (LPC/LPP Scenario-/Feature-Tabellen)
**maßgeblich** herangezogen — echte EEBUS-Primärspezifikationen, vom Nutzer bereitgestellt
unter `C:\Projects\eebus` (siehe `README.rtf` dort: von eebus.org nach kostenfreier
Registrierung heruntergeladen, Ordner enthält nicht alle dort verfügbaren Dokumente, u. a.
fehlen Test Specs und E-Auto-Use-Case-Spezifikationen):

- `EEBus_UC_IG_GeneralGuidelines_V1.0.0.pdf` — Annex A, Table 1 (vollständiger Use-Case-Katalog)
- `EEBus_UC_TS_LimitationOfPowerConsumption_V1.0.0_public.pdf` — Table 2, Table 12, Table 21
- `EEBus_UC_TS_LimitationOfPowerProduction_V1.0.0_public.pdf` — Table 2, Table 21
- `EEBus_UC_IG_LimitationOfPowerConsumption_V1.1.0.pdf`, `EEBus_UC_IG_LimitationOfPowerProduction_V1.0.0.pdf` (Implementation Guidelines, in dieser Runde noch nicht ausgewertet)
- `EEBus_SPINE_V1.3.0_Final_hp/Documentation/*.pdf`, `EEBus_SPINE_IG_ProtocolAndResourceGuidelines_V1.0.0.pdf` (SPINE-Kernspezifikation, bisher nicht benötigt — bereits über jeebus.spine-Quellcode verifiziert)
- `EEBus_SHIP_TS_Specification_v1.1.0_public/EEBus_SHIP_TS_Specification_v1.1.0.pdf`, `EEBus_SHIP_IG_TransportAndConnectivity_V1.0.0-1.pdf`, `EEBus_SHIP_Pairing_Service_TS_Specification_V1.0.0.pdf` (SHIP-Kernspezifikation, bisher nicht benötigt — bereits über jeebus.ship-Quellcode verifiziert)

Für §7 Punkt 1 (Keystore-Auto-Erzeugung, `keepAlive`-vs-`autoAccept`-Bugfix) herangezogen:

- [github.com/openmuc/jeebus.ship, Tag v2.2.0 – ShipNodeConfiguration.java](https://raw.githubusercontent.com/openmuc/jeebus.ship/v2.2.0/projects/ship/src/main/java/org/openmuc/jeebus/ship/api/ShipNodeConfiguration.java)

Für §7 Punkt 9 (`communicationAddress`-Format, TXT-Record-Felder) herangezogen:

- [github.com/openmuc/jeebus.ship, Tag v2.2.0 – ConnectionHandler.java](https://raw.githubusercontent.com/openmuc/jeebus.ship/v2.2.0/projects/ship/src/main/java/org/openmuc/jeebus/ship/api/ConnectionHandler.java)
- [github.com/openmuc/jeebus.ship, Tag v2.2.0 – ServiceRegistry.java](https://raw.githubusercontent.com/openmuc/jeebus.ship/v2.2.0/projects/ship/src/main/java/org/openmuc/jeebus/ship/node/service/ServiceRegistry.java)

Für §5.4.2/§7 Punkt 3 (Scenario-/Feature-Typ-Tabellen MPC/MGCP) herangezogen — Sekundärquelle,
für diese beiden Use-Cases liegt keine TS-PDF unter `C:\Projects\eebus` vor (LPC/LPP siehe
Primärquelle oben):

- [github.com/enbility/eebus-go – usecases/cs/lpc/usecase.go](https://raw.githubusercontent.com/enbility/eebus-go/main/usecases/cs/lpc/usecase.go)
- [github.com/enbility/eebus-go – usecases/cs/lpp/usecase.go](https://raw.githubusercontent.com/enbility/eebus-go/main/usecases/cs/lpp/usecase.go)
- [github.com/enbility/eebus-go – usecases/ma/mpc/usecase.go](https://raw.githubusercontent.com/enbility/eebus-go/main/usecases/ma/mpc/usecase.go)
- [github.com/enbility/eebus-go – usecases/ma/mgcp/usecase.go](https://raw.githubusercontent.com/enbility/eebus-go/main/usecases/ma/mgcp/usecase.go)

Für §4.2/§7 Punkt 6 (Item-Metadaten-API) herangezogen:

- [openHAB Core Javadoc – Metadata](https://www.openhab.org/javadoc/latest/org/openhab/core/items/metadata)
- [openHAB Core Javadoc – MetadataKey](https://www.openhab.org/javadoc/latest/org/openhab/core/items/metadatakey)
- [openHAB Core Javadoc – MetadataRegistry](https://www.openhab.org/javadoc/latest/org/openhab/core/items/metadataregistry)
- [openHAB Core Javadoc – AbstractItemEventSubscriber](https://www.openhab.org/javadoc/latest/org/openhab/core/items/events/abstractitemeventsubscriber)
- [openHAB Core Javadoc – QuantityType](https://www.openhab.org/javadoc/latest/org/openhab/core/library/types/quantitytype)
- [openHAB Developer Docs – OSGi Declarative Services (constructor injection)](https://www.openhab.org/docs/developer/osgi/osgids.html)

Für §7 Punkt 10 (Bridge-scoped Discovery) herangezogen:

- [openHAB Developer Docs – Developing a Binding (Abschnitt "Discovery that is bound to a Bridge")](https://www.openhab.org/docs/developer/bindings/)
- [openHAB Core Javadoc – AbstractThingHandlerDiscoveryService](https://www.openhab.org/javadoc/latest/org/openhab/core/config/discovery/abstractthinghandlerdiscoveryservice)
- [openHAB Core Javadoc – DiscoveryResultBuilder](https://www.openhab.org/javadoc/latest/org/openhab/core/config/discovery/discoveryresultbuilder)
