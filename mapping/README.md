# DTO-to-Semantic-Model Mapping Guide

Die Mapping-Vorlage beschreibt den neutralen Schritt

`interne DTOs -> semantisches $xr-Modell`

und nicht das spaetere UBL-Rendering.

Vorlage: [dto-to-semantic-model-template.tsv](./dto-to-semantic-model-template.tsv).

## Zweck

Das Mapping trennt sauber:

- interne Herkunft eines Wertes
- semantischen Zielpfad im `$xr`-Modell
- notwendige Voraufbereitung vor Velocity

Damit bleiben internes Mapping und Template-Entscheidungen voneinander getrennt.

## Verwendung

1. Die TSV-Datei in einen privaten Arbeitsbereich kopieren.
2. Die Spalte `source_internal` nur dort mit internen DTO-Pfaden oder
   Erzeugungsregeln fuellen.
3. In `mapping_work` bei Bedarf die vorgesehene Aufbereitung ergaenzen.
4. In `status` markieren, was nur roh gemappt, was fachlich vorbereitet und was
   schon verifiziert ist.

## Spalten

| Spalte | Bedeutung |
|---|---|
| `model_path` | Zielpfad im semantischen `$xr`-Modell |
| `term_hint` | XRechnung-Referenz oder Hinweis auf den Block |
| `mapping_work` | kompakte Arbeitsangabe wie `value / map`, `object / compose`, `list / repeat`; bei Bedarf mit weiterer Voraufbereitung ergaenzen |
| `source_internal` | privat zu fuellender DTO-Pfad, Query oder Berechnungsquelle |
| `status` | z. B. `todo`, `mapped`, `verified`, `blocked` |

## Werte in `mapping_work`

- `value / map`
- `object / compose`
- `list / repeat`
- `value / map / normalize_blank_to_null`
- `value / map / format_date`
- `value / map / normalize_decimal`
- `value / map / constant`
- `value / map / choose_one`
- `value / map / lookup_code`
- `value / map / split_identifier`
- `value / map / join_text`
- `object / compose / aggregate_totals`
- `list / repeat / build_vat_breakdown`
- `list / repeat / group_by_business_key`

Sinnvoll ist eine einheitliche, kurze Schreibweise ueber alle Zeilen hinweg.

## Fehlende Werte

Beim Aufbau des semantischen Modells gilt:

- fehlende oder leere Quellwerte werden im semantischen Modell weggelassen oder zu
  `null` normalisiert
- leere Listen bleiben leer
- Listeneintraege ohne echten Inhalt werden beim Aufbau des semantischen Modells
  verworfen

## Container-Zeilen

- `object / compose` beschreibt einen zusammengesetzten Block.
- `list / repeat` beschreibt eine interne Liste oder Gruppierung, aus der
  mehrere Modellobjekte entstehen.

Beispiele:

- `xr.vatBreakdowns[]`:
  `mapping_work = list / repeat / build_vat_breakdown`
- `xr.lines[]`:
  `mapping_work = list / repeat`
- `xr.totals`:
  `mapping_work = object / compose / aggregate_totals`

## Bearbeitungsreihenfolge

1. Kopfwerte und einfache Referenzen
2. Parteien und Adressen
3. Zahlungsdaten und Lieferdaten
4. Linienlisten
5. Abgeleitete Summen und Steueraufschluesselung
6. Sonderfaelle wie `BT-18`, `BT-82`, `BT-90`, `BT-111`

## Sonderfaelle

- `BT-8` liegt im semantischen Modell unter `xr.invoicePeriod.descriptionCode`.
- `BT-18` und `BT-128` sind im semantischen Modell Objektpfade mit `id` und
  `schemeId`, obwohl sie spaeter als UBL-Referenzcontainer gerendert werden.
- `BT-82` ist modellseitig `payment.meansText`, auch wenn es in UBL als
  Attribut rendert.
- `BT-90` wird im semantischen Modell bei Seller oder Payee als `sepaCreditorId`
  gefuehrt.
- `BT-111` ist getrennt als `xr.totals.taxAmountInTaxCurrency`.

## Ablage

Die semantische Vorlage bleibt in diesem Repo.
Eine befuellte Mapping-Datei mit echten `source_internal`-Eintraegen gehoert in den
privaten Bereich oder in ein internes Repo.
