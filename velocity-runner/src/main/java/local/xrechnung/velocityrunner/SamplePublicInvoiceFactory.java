package local.xrechnung.velocityrunner;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a public, XRechnung-shaped sample model for local template runs.
 */
public final class SamplePublicInvoiceFactory {

  private SamplePublicInvoiceFactory() {
  }

  public static Map<String, Object> fullInvoice() {
    Map<String, Object> xr = map();

    xr.put("process", map(
        "customizationId", "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0",
        "profileId", "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0"
    ));

    xr.put("invoice", map(
        "id", "RE-2026-00042",
        "issueDate", "2026-04-15",
        "dueDate", "2026-05-15",
        "typeCode", "380",
        "documentCurrencyCode", "EUR",
        "taxCurrencyCode", null,
        "taxPointDate", null,
        "buyerReference", "REF-4711",
        "accountingCost", "Kostenstelle 1000"
    ));

    xr.put("notes", list(
        map("subjectCode", "AAK", "text", "Abnahme erfolgt am 14.04.2026"),
        map("subjectCode", null, "text", "Vielen Dank fuer Ihren Auftrag.")
    ));

    xr.put("invoicePeriod", map(
        "descriptionCode", null,
        "startDate", "2026-04-01",
        "endDate", "2026-04-30"
    ));

    xr.put("projectReferenceId", "PRJ-2026-07");
    xr.put("contractReferenceId", "VERTRAG-99");
    xr.put("receiptReferenceId", "WE-2026-042");
    xr.put("despatchReferenceId", "LFS-2026-042");
    xr.put("originatorReferenceId", "ANF-2026-0007");
    xr.put("orderReference", map("id", "BEST-2026-123", "salesOrderId", "SO-9911"));
    xr.put("precedingInvoices", list(
        map("id", "RE-2026-00017", "issueDate", "2026-03-15")
    ));
    xr.put("invoiceObjectReference", null);

    xr.put("seller", party(
        "Musterlieferant GmbH",
        "Musterlieferant",
        "seller@example.org",
        "https://seller.example.org/endpoint",
        "EM",
        "DE123456789",
        "201/999/88888",
        "HRB-12345",
        "Handelsregister B",
        "DE",
        "Musterstadt",
        "10115"
    ));
    Map<String, Object> buyer = party(
        "Beispielkunde AG",
        "Beispielkunde",
        "buyer@example.org",
        "buyer-routing-id",
        "0204",
        "DE987654321",
        null,
        "HRB-54321",
        null,
        "DE",
        "Berlin",
        "10243"
    );
    buyer.remove("identifiers");
    buyer.put("identifier", identifier("123456789", "0088"));
    xr.put("buyer", buyer);

    xr.put("payee", map(
        "name", "Bezahlservice GmbH",
        "identifier", identifier("PAYEE-42", "0088"),
        "sepaCreditorId", "DE12ZZZ00000000001",
        "legalRegistrationId", "HRB-778899",
        "legalRegistrationIdSchemeId", "0204"
    ));

    xr.put("taxRepresentative", map(
        "name", "Steuerkanzlei Nord",
        "vatIdentifier", "DE112233445",
        "address", address("Steuerweg 5", null, "3. Etage", "Hamburg", "20095", null, "DE")
    ));

    xr.put("delivery", map(
        "partyName", "Wareneingang Lager A",
        "actualDate", "2026-04-14",
        "location", map("id", "LOC-77", "schemeId", "0088"),
        "address", address("Lagerstrasse 8", null, "Tor 3", "Berlin", "12435", null, "DE")
    ));

    xr.put("payment", map(
        "meansCode", "58",
        "meansText", "SEPA credit transfer",
        "paymentId", "PMT-2026-042",
        "payeeAccounts", list(
            map("id", "DE02120300000000202051", "name", "Musterlieferant GmbH", "bic", "BYLADEM1001")
        )
    ));

    xr.put("paymentTerms", map(
        "note", "Zahlbar innerhalb von 30 Tagen ohne Abzug."
    ));

    xr.put("documentAllowances", list(
        map(
            "amount", money("5.00"),
            "baseAmount", money("100.00"),
            "percent", decimal("5"),
            "reason", "Fruehbucherrabatt",
            "reasonCode", "95",
            "tax", tax("S", "19", null, null)
        )
    ));

    xr.put("documentCharges", list(
        map(
            "amount", money("2.50"),
            "baseAmount", money("100.00"),
            "percent", decimal("2.5"),
            "reason", "Verpackung",
            "reasonCode", "FC",
            "tax", tax("S", "19", null, null)
        )
    ));

    xr.put("totals", map(
        "lineExtensionAmount", money("97.60"),
        "allowanceTotalAmount", money("5.00"),
        "chargeTotalAmount", money("2.50"),
        "taxExclusiveAmount", money("95.10"),
        "taxAmountInDocumentCurrency", money("18.07"),
        "taxAmountInTaxCurrency", null,
        "taxInclusiveAmount", money("113.17"),
        "prepaidAmount", money("0.00"),
        "payableRoundingAmount", money("0.00"),
        "payableAmount", money("113.17")
    ));

    xr.put("vatBreakdowns", list(
        taxBreakdown("95.10", "18.07", "S", "19", null, null)
    ));

    xr.put("supportingDocuments", list(
        map(
            "id", "ANLAGE-1",
            "description", "Leistungsnachweis April 2026",
            "embedded", map(
                "externalUri", "https://example.org/docs/leistungsnachweis-april-2026.pdf",
                "content", null,
                "mimeCode", null,
                "filename", null
            )
        ),
        map(
            "id", "ANLAGE-2",
            "description", "Stundenzettel",
            "embedded", map(
                "externalUri", "https://example.org/docs/stundenzettel-april-2026.pdf",
                "content", null,
                "mimeCode", null,
                "filename", null
            )
        )
    ));

    xr.put("lines", list(
        lineOne(),
        lineTwo()
    ));

    return xr;
  }

  public static Map<String, Object> coreInvoice() {
    Map<String, Object> xr = fullInvoice();
    xr.put("documentAllowances", new ArrayList<Object>());
    xr.put("documentCharges", new ArrayList<Object>());

    Map<String, Object> totals = castMap(xr.get("totals"));
    totals.put("allowanceTotalAmount", null);
    totals.put("chargeTotalAmount", null);
    totals.put("taxExclusiveAmount", money("97.60"));
    totals.put("taxAmountInDocumentCurrency", money("18.54"));
    totals.put("taxAmountInTaxCurrency", null);
    totals.put("taxInclusiveAmount", money("116.14"));
    totals.put("payableAmount", money("116.14"));

    xr.put("vatBreakdowns", list(
        taxBreakdown("97.60", "18.54", "S", "19", null, null)
    ));

    return xr;
  }

  private static Map<String, Object> lineOne() {
    return map(
        "id", "1",
        "note", "Leistungsposition 1",
        "objectReference", null,
        "quantity", decimal("10"),
        "quantityUnitCode", "HUR",
        "lineExtensionAmount", money("77.60"),
        "accountingCost", "PSP-Element 0815",
        "orderLineReference", "10",
        "period", map("startDate", "2026-04-01", "endDate", "2026-04-10", "descriptionCode", null),
        "allowances", list(
            map("amount", money("4.00"), "baseAmount", money("80.00"), "percent", decimal("5"), "reason", "Rabatt", "reasonCode", "95")
        ),
        "charges", list(
            map("amount", money("1.60"), "baseAmount", money("80.00"), "percent", decimal("2"), "reason", "Servicepauschale", "reasonCode", "FC")
        ),
        "price", map(
            "netAmount", money("8.00"),
            "discount", map("amount", money("0.40"), "baseAmount", money("8.40")),
            "baseQuantity", decimal("1"),
            "baseQuantityUnitCode", "HUR"
        ),
        "vat", map("categoryCode", "S", "rate", decimal("19")),
        "item", map(
            "name", "Beratungsleistung",
            "description", "Fachberatung April",
            "sellersItemId", "ART-1000",
            "buyersItemId", "K-ART-44",
            "standardId", "0401234512345",
            "standardIdSchemeId", "0160",
            "classifications", new ArrayList<Object>(),
            "originCountryCode", "DE",
            "attributes", list(
                map("name", "Team", "value", "Consulting")
            )
        )
    );
  }

  private static Map<String, Object> lineTwo() {
    return map(
        "id", "2",
        "note", null,
        "objectReference", null,
        "quantity", decimal("1"),
        "quantityUnitCode", "C62",
        "lineExtensionAmount", money("20.00"),
        "accountingCost", null,
        "orderLineReference", null,
        "period", map("startDate", null, "endDate", null, "descriptionCode", null),
        "allowances", new ArrayList<Object>(),
        "charges", new ArrayList<Object>(),
        "price", map(
            "netAmount", money("20.00"),
            "discount", map("amount", null, "baseAmount", null),
            "baseQuantity", decimal("1"),
            "baseQuantityUnitCode", "C62"
        ),
        "vat", map("categoryCode", "S", "rate", decimal("19")),
        "item", map(
            "name", "Pauschale",
            "description", "Projektpauschale",
            "sellersItemId", "ART-2000",
            "buyersItemId", null,
            "standardId", null,
            "standardIdSchemeId", null,
            "classifications", new ArrayList<Object>(),
            "originCountryCode", null,
            "attributes", new ArrayList<Object>()
        )
    );
  }

  private static Map<String, Object> party(
      String name,
      String tradeName,
      String email,
      String endpointValue,
      String endpointSchemeId,
      String vatIdentifier,
      String taxIdentifier,
      String legalRegistrationId,
      String legalForm,
      String countryCode,
      String city,
      String postalCode) {

    return map(
        "name", name,
        "tradeName", tradeName,
        "endpoint", map("value", endpointValue, "schemeId", endpointSchemeId),
        "identifiers", list(identifier("123456789", "0088")),
        "vatIdentifier", vatIdentifier,
        "taxIdentifier", taxIdentifier,
        "legalRegistrationId", legalRegistrationId,
        "legalRegistrationIdSchemeId", "0204",
        "legalForm", legalForm,
        "address", address("Musterstrasse 1", "Gebaeude A", "2. OG", city, postalCode, null, countryCode),
        "contact", map("name", name + " Kontakt", "phone", "+49-30-123456", "email", email)
    );
  }

  private static Map<String, Object> address(
      String street,
      String additionalStreet,
      String addressLine,
      String city,
      String postalCode,
      String countrySubdivision,
      String countryCode) {
    return map(
        "street", street,
        "additionalStreet", additionalStreet,
        "addressLine", addressLine,
        "city", city,
        "postalCode", postalCode,
        "countrySubdivision", countrySubdivision,
        "countryCode", countryCode
    );
  }

  private static Map<String, Object> identifier(String value, String schemeId) {
    return map("value", value, "schemeId", schemeId);
  }

  private static Map<String, Object> tax(String categoryCode, String rate, String exemptionReason, String exemptionReasonCode) {
    return map(
        "categoryCode", categoryCode,
        "rate", decimal(rate),
        "exemptionReason", exemptionReason,
        "exemptionReasonCode", exemptionReasonCode
    );
  }

  private static Map<String, Object> taxBreakdown(
      String taxableAmount,
      String taxAmount,
      String categoryCode,
      String rate,
      String exemptionReason,
      String exemptionReasonCode) {
    return map(
        "taxableAmount", money(taxableAmount),
        "taxAmount", money(taxAmount),
        "categoryCode", categoryCode,
        "rate", decimal(rate),
        "exemptionReason", exemptionReason,
        "exemptionReasonCode", exemptionReasonCode
    );
  }

  private static BigDecimal money(String value) {
    return new BigDecimal(value);
  }

  private static BigDecimal decimal(String value) {
    return new BigDecimal(value);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> castMap(Object value) {
    return (Map<String, Object>) value;
  }

  private static List<Object> list(Object... values) {
    return new ArrayList<Object>(Arrays.asList(values));
  }

  private static Map<String, Object> map(Object... entries) {
    if (entries.length % 2 != 0) {
      throw new IllegalArgumentException("Expected an even number of map entries");
    }
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    for (int i = 0; i < entries.length; i += 2) {
      map.put((String) entries[i], entries[i + 1]);
    }
    return map;
  }
}
