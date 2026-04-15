# XRechnung UBL Invoice Velocity Reference

Dieses Repository sammelt eine oeffentliche Arbeitsbasis fuer das Rendern von
`XRechnung 3.0.2` als `UBL Invoice` mit `Apache Velocity 1.6.4`.

## Prinzip

- internes `DTO -> $xr` bleibt privat
- das oeffentliche Zwischenmodell `$xr` ist hier dokumentiert
- die Velocity-Templates rendern tolerant und lassen fehlende Werte weg
- die fachliche Validierung folgt nach dem Rendern

## Verzeichnisstruktur

- `bundle-docs/`
  Kuratierte oeffentliche Referenzquellen aus dem offiziellen
  XRechnung-Bundle, die wir hier lokal nachlesen.
- `templates/`
  Uploadbare Single-File-Velocity-Templates fuer `UBL Invoice`.
- `public-model/`
  Oeffentliches `$xr`-Modell, Vertragsbeschreibung und Kern-Mapping.
- `mapping-matrix/`
  Neutrale Vorlage fuer den internen Schritt `DTO -> $xr`.
- `examples/`
  Ausgefuellte Beispielinstanzen fuer `core` und `full`.
- `notes/`
  Arbeitsnotizen, Strukturuebersichten und Helper-Vertrag.
- `velocity-runner/`
  Kleines lokales Java-Harness, um die Templates mit `Velocity 1.6.4`
  wirklich zu rendern.

## Kern-Dateien

- `templates/ubl-invoice-core.vm`
  Kleine Kernsicht fuer den Einstieg.
- `templates/ubl-invoice-full.vm`
  Vollere Referenz fuer `XRechnung 3.0.2 / UBL Invoice`.
- `public-model/ubl-invoice-core-stub.yaml`
  Null-initialisierte Kernvorlage fuer `$xr`.
- `public-model/ubl-invoice-full-stub.yaml`
  Null-initialisierte Vollvorlage fuer `$xr`.
- `public-model/ubl-invoice-core-mapping.yaml`
  Mapping- und Konventionsbeschreibung fuer den Core-Schnitt.
- `examples/ubl-invoice-core-example.yaml`
  Kleine ausgefuellte Beispielinstanz.
- `examples/ubl-invoice-full-example.yaml`
  Vollere ausgefuellte Beispielinstanz.
- `notes/xml-helper-contract.md`
  Vertrag fuer die Helper-Funktionen unter `$xml`.
- `velocity-runner/src/main/java/local/xrechnung/velocityrunner/VelocitySmokeRenderer.java`
  Minimales Testprogramm fuer echte Renderlaeufe.

## Lokaler Testlauf

```bash
mvn -f velocity-runner/pom.xml package
java -jar velocity-runner/target/velocity-runner.jar \
  --template templates/ubl-invoice-full.vm \
  --out /tmp/invoice-full.xml
```

Mit Validatorlauf:

```bash
java -jar velocity-runner/target/velocity-runner.jar \
  --template templates/ubl-invoice-full.vm \
  --out /tmp/invoice-full.xml \
  --validate
```

Der Runner setzt insbesondere:

- `runtime.references.strict = false`
- Inline-Makros erlaubt
- fuer lokale Validierung den KoSIT-Validator `1.6.0` aus dem Bundle unter
  `bundle-docs/`

## Scope

Diese Arbeitsbasis deckt aktuell `XRechnung 3.0.2` fuer `UBL Invoice` ab.
`CII` und `CreditNote` sind nicht Teil dieses Repos.

## License

Der eigene Quell- und Dokumentationsanteil dieses Repos steht unter
[0BSD](./LICENSE.md).

Die unter `bundle-docs/` abgelegten Referenzartefakte stammen aus externen
offiziellen Bundles und behalten ihre jeweiligen Original-Lizenz- und
Nutzungsbedingungen.
