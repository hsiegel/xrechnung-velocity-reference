package local.xrechnung.velocityrenderer;

import org.apache.commons.text.StringEscapeUtils;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

/**
 * XML-oriented helper object for the semantic-model-driven Velocity templates.
 *
 * <p>The templates treat this class as a small rendering API that covers three
 * concerns: presence checks for optional values, XML-safe escaping for element
 * and attribute content, and stable formatting for dates and decimal numbers.
 *
 * <p>The helper is intentionally generic. It does not know XRechnung business
 * rules, does not calculate totals, and does not enforce required fields. Those
 * decisions belong in the preparation step before rendering or in the later
 * validator pass.
 */
public final class XmlHelper {

  public static final XmlHelper INSTANCE = new XmlHelper();

  private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

  /**
   * Decides whether a value should be treated as present by the templates.
   *
   * <p>This method drives all optional rendering. It returns {@code false} for
   * {@code null}, blank strings, empty collections, empty arrays, and maps that
   * recursively contain no renderable content. Numeric zero remains a present
   * value. This allows the templates to omit empty structures while still
   * rendering valid zero amounts, rates, or quantities.
   *
   * @param value candidate value from the semantic {@code $xr} model
   * @return {@code true} if the value should render, otherwise {@code false}
   */
  public boolean has(Object value) {
    return hasContent(value, Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>()));
  }

  /**
   * Escapes a value for use as XML element content.
   *
   * <p>The result is safe for text nodes and does not add business defaults.
   * Missing values are converted to the empty string, although the templates
   * normally guard such calls with {@link #has(Object)}. Invalid XML 1.0
   * characters are stripped instead of aborting the render pass.
   *
   * @param value raw value from the semantic model
   * @return XML-safe element text
   */
  public String text(Object value) {
    return escapeXml(stringValue(value), false);
  }

  /**
   * Escapes a value for use as an XML attribute value.
   *
   * <p>This is used for attributes such as {@code schemeID},
   * {@code currencyID}, {@code unitCode}, {@code name}, or attachment
   * metadata. The escaping rules are stricter than for element content because
   * quotes must also be encoded. Invalid XML 1.0 characters are stripped
   * instead of aborting the render pass.
   *
   * @param value raw value from the semantic model
   * @return XML-safe attribute text
   */
  public String attr(Object value) {
    return escapeXml(stringValue(value), true);
  }

  /**
   * Formats a value as an XML invoice date in ISO local date form.
   *
   * <p>The accepted output format is always {@code yyyy-MM-dd}. The helper
   * accepts already normalized strings as well as common Java date/time types
   * and collapses them to a plain local date without locale-specific output.
   *
   * @param value date-like value from the semantic model
   * @return formatted date or the empty string for {@code null}
   * @throws IllegalArgumentException if the value cannot be interpreted as a date
   */
  public String date(Object value) {
    Object unwrapped = unwrap(value);
    if (unwrapped == null) {
      return "";
    }
    if (unwrapped instanceof LocalDate) {
      return ISO_DATE.format((LocalDate) unwrapped);
    }
    if (unwrapped instanceof LocalDateTime) {
      return ISO_DATE.format(((LocalDateTime) unwrapped).toLocalDate());
    }
    if (unwrapped instanceof OffsetDateTime) {
      return ISO_DATE.format(((OffsetDateTime) unwrapped).toLocalDate());
    }
    if (unwrapped instanceof ZonedDateTime) {
      return ISO_DATE.format(((ZonedDateTime) unwrapped).toLocalDate());
    }
    if (unwrapped instanceof CharSequence) {
      String normalized = unwrapped.toString().trim();
      if (normalized.isEmpty()) {
        return "";
      }
      try {
        return ISO_DATE.format(LocalDate.parse(normalized, ISO_DATE));
      } catch (DateTimeParseException ex) {
        throw new IllegalArgumentException("Unsupported date value: " + normalized, ex);
      }
    }
    throw new IllegalArgumentException("Unsupported date type: " + unwrapped.getClass().getName());
  }

  /**
   * Formats a monetary amount for XML output.
   *
   * <p>The method emits a plain decimal representation with {@code .} as the
   * decimal separator, without grouping separators and without scientific
   * notation. Rounding is intentionally not performed here; amounts should be
   * prepared before rendering.
   *
   * @param value amount-like value from the semantic model
   * @return plain decimal text or the empty string for {@code null}
   * @throws IllegalArgumentException if the value cannot be represented as a decimal
   */
  public String amount(Object value) {
    return decimal(value);
  }

  /**
   * Formats a non-monetary decimal number for XML output.
   *
   * <p>This is used for quantities, percentages, and similar numeric values.
   * The formatting rules intentionally mirror {@link #amount(Object)} so the
   * templates get consistent decimal output regardless of the business meaning
   * of the number.
   *
   * @param value numeric value from the semantic model
   * @return plain decimal text or the empty string for {@code null}
   * @throws IllegalArgumentException if the value cannot be represented as a decimal
   */
  public String number(Object value) {
    return decimal(value);
  }

  private String decimal(Object value) {
    Object unwrapped = unwrap(value);
    if (unwrapped == null) {
      return "";
    }
    return toBigDecimal(unwrapped).toPlainString();
  }

  private static Object unwrap(Object value) {
    Object current = value;
    while (current instanceof Optional<?>) {
      current = ((Optional<?>) current).orElse(null);
    }
    if (current instanceof OptionalInt) {
      OptionalInt optional = (OptionalInt) current;
      return optional.isPresent() ? Integer.valueOf(optional.getAsInt()) : null;
    }
    if (current instanceof OptionalLong) {
      OptionalLong optional = (OptionalLong) current;
      return optional.isPresent() ? Long.valueOf(optional.getAsLong()) : null;
    }
    if (current instanceof OptionalDouble) {
      OptionalDouble optional = (OptionalDouble) current;
      return optional.isPresent() ? Double.valueOf(optional.getAsDouble()) : null;
    }
    return current;
  }

  private static boolean hasContent(Object value, Set<Object> seen) {
    Object unwrapped = unwrap(value);
    if (unwrapped == null) {
      return false;
    }
    if (unwrapped instanceof CharSequence) {
      return !unwrapped.toString().trim().isEmpty();
    }
    if (unwrapped instanceof Number || unwrapped instanceof Boolean || unwrapped instanceof Character) {
      return true;
    }
    if (shouldTrackIdentity(unwrapped) && !seen.add(unwrapped)) {
      return false;
    }
    if (unwrapped instanceof Collection<?>) {
      for (Object item : (Collection<?>) unwrapped) {
        if (hasContent(item, seen)) {
          return true;
        }
      }
      return false;
    }
    if (unwrapped instanceof Map<?, ?>) {
      for (Object item : ((Map<?, ?>) unwrapped).values()) {
        if (hasContent(item, seen)) {
          return true;
        }
      }
      return false;
    }
    if (unwrapped.getClass().isArray()) {
      int length = Array.getLength(unwrapped);
      for (int i = 0; i < length; i++) {
        if (hasContent(Array.get(unwrapped, i), seen)) {
          return true;
        }
      }
      return false;
    }
    return true;
  }

  private static boolean shouldTrackIdentity(Object value) {
    return !(value instanceof Number)
        && !(value instanceof Boolean)
        && !(value instanceof Character)
        && !(value instanceof CharSequence)
        && !value.getClass().isEnum();
  }

  private static String stringValue(Object value) {
    Object unwrapped = unwrap(value);
    return unwrapped == null ? "" : String.valueOf(unwrapped);
  }

  private static BigDecimal toBigDecimal(Object value) {
    if (value instanceof BigDecimal) {
      return (BigDecimal) value;
    }
    if (value instanceof BigInteger) {
      return new BigDecimal((BigInteger) value);
    }
    if (value instanceof Byte) {
      return BigDecimal.valueOf(((Byte) value).longValue());
    }
    if (value instanceof Short) {
      return BigDecimal.valueOf(((Short) value).longValue());
    }
    if (value instanceof Integer) {
      return BigDecimal.valueOf(((Integer) value).longValue());
    }
    if (value instanceof Long) {
      return BigDecimal.valueOf(((Long) value).longValue());
    }
    if (value instanceof Float) {
      float number = ((Float) value).floatValue();
      if (!Float.isFinite(number)) {
        throw new IllegalArgumentException("Non-finite float value: " + number);
      }
      return BigDecimal.valueOf(number);
    }
    if (value instanceof Double) {
      double number = ((Double) value).doubleValue();
      if (!Double.isFinite(number)) {
        throw new IllegalArgumentException("Non-finite double value: " + number);
      }
      return BigDecimal.valueOf(number);
    }
    if (value instanceof CharSequence) {
      String normalized = value.toString().trim();
      if (normalized.isEmpty()) {
        throw new IllegalArgumentException("Blank string is not a valid decimal value");
      }
      try {
        return new BigDecimal(normalized);
      } catch (NumberFormatException ex) {
        throw new IllegalArgumentException("Unsupported decimal value: " + normalized, ex);
      }
    }
    throw new IllegalArgumentException("Unsupported numeric type: " + value.getClass().getName());
  }

  private static String escapeXml(String input, boolean attributeContext) {
    String escaped = StringEscapeUtils.escapeXml10(input);
    if (attributeContext) {
      return escaped;
    }
    return escaped.replace("&quot;", "\"").replace("&apos;", "'");
  }
}
