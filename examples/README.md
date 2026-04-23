# Examples

In diesem Verzeichnis liegen ausgefuellte Beispielinstanzen des
Rechnungsmodells unter `xr:`.

- `ubl-invoice-core-example.yaml`
  Kleine Kerninstanz passend zur Core-Sicht.
- `ubl-invoice-full-example.yaml`
  Vollere Instanz passend zur Full-Sicht.

Die dazugehoerigen null-initialisierten Vorlagen und das JSON Schema liegen
nicht hier, sondern in [`semantic-model/`](../semantic-model/).

Die Beispiele lassen sich mit der Haskell-CLI direkt in `UBL Invoice XML`
umwandeln:

```bash
cd prototypes/haskell-verifier
stack run xrechnung-haskell-verifier -- to-xml ../../examples/ubl-invoice-core-example.yaml
```

Fuer Editor- und Fruehvalidierung verweisen die Beispiel-YAMLs direkt auf
[`../semantic-model/xrechnung.schema.json`](../semantic-model/xrechnung.schema.json).
