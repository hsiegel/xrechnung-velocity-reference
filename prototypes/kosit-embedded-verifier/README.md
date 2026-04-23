# KoSIT Embedded Verifier

Kleiner, bewusst additiver Prototyp fuer XRechnung-Validierung ueber die
embedded API des KoSIT-Validators.

## Zweck

- vorhandene XML-Dateien direkt im selben JVM-Prozess validieren
- den bestehenden `prototypes/velocity-renderer/` als Referenz unangetastet
  lassen
- dieselbe versionierte XRechnung-Konfiguration aus `bundle-docs/` weiter
  nutzen, ohne Artefakte in dieses Modul zu kopieren

Der aktuelle Scope bleibt bewusst klein:

- `XRechnung 3.0.2`
- `UBL Invoice`
- keine `CII`
- keine `CreditNote`
- keine `Extension`- oder `CVD`-Sonderfaelle

## Build

Vom Projektwurzelverzeichnis aus:

```bash
mvn -f prototypes/kosit-embedded-verifier/pom.xml package
```

Das erzeugt ein lauffaehiges Fat-JAR:

```bash
prototypes/kosit-embedded-verifier/target/kosit-embedded-verifier.jar
```

## Aufruf

Pflichtargument:

- `--xml <pfad-zur-xml>`

Optionale Argumente:

- `--report-dir <zielordner>`
- `--work-dir <arbeitsordner>`

Beispiel:

```bash
java -jar prototypes/kosit-embedded-verifier/target/kosit-embedded-verifier.jar \
  --xml /tmp/invoice-full.xml
```

Mit expliziten Zielordnern:

```bash
java -jar prototypes/kosit-embedded-verifier/target/kosit-embedded-verifier.jar \
  --xml /tmp/invoice-full.xml \
  --report-dir /tmp/embedded-reports \
  --work-dir /tmp/kosit-embedded-work
```

Ohne `--report-dir` landet der XML-Report neben der Eingabe-XML.
Ohne `--work-dir` arbeitet der Prototyp unter
`prototypes/kosit-embedded-verifier/target/validator-work/`.

## Nutzung von bundle-docs

Der Verifier sucht die bestehende versionierte Konfiguration direkt unter
`bundle-docs/xrechnung-3.0.2-validator-configuration-*.zip`.

Dabei gilt:

- es wird nichts aus `bundle-docs/` in dieses Modul kopiert
- das gefundene ZIP wird bei Bedarf nach
  `prototypes/kosit-embedded-verifier/target/validator-work/` entpackt
- gearbeitet wird direkt mit `scenarios.xml` und den darin referenzierten
  Ressourcen aus diesem entpackten Arbeitsordner

Damit teilt sich der neue Prototyp dieselbe gepflegte Konfigurationsquelle mit
der optionalen Validierung im `prototypes/velocity-renderer/`, ohne dessen
Infrastruktur umzubauen.

## Unterschiede zur Velocity-Renderer-Validierung

- dieser Prototyp nutzt die embedded API des KoSIT-Validators statt `java -jar`
- der bestehende `prototypes/velocity-renderer/` bleibt vollstaendig getrennt
- der Prototyp schreibt bewusst nur den XML-Report des Validators
- ein HTML-Report wird hier nicht nachgebaut oder vorgetaeuscht

## Laufzeitabhaengigkeiten

Fuer die embedded Nutzung werden die notwendigen Bibliotheken ueber Maven in
diesem Modul eingebunden, insbesondere:

- `org.kosit:validator:1.6.2`
- `net.sf.saxon:Saxon-HE:12.9`
- `org.apache.commons:commons-lang3:3.20.0`
- `commons-io:commons-io:2.21.0`
- `org.glassfish.jaxb:jaxb-runtime:4.0.6`
- `org.slf4j:slf4j-simple:2.0.17`
