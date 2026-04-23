package local.xrechnung.kositisolated;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

final class JsonSupport {

  private JsonSupport() {
  }

  static String toJson(Object value) {
    StringBuilder builder = new StringBuilder();
    appendJson(builder, value);
    return builder.toString();
  }

  private static void appendJson(StringBuilder builder, Object value) {
    if (value == null) {
      builder.append("null");
    } else if (value instanceof String) {
      appendString(builder, (String) value);
    } else if (value instanceof Number || value instanceof Boolean) {
      builder.append(value);
    } else if (value instanceof Map<?, ?>) {
      appendMap(builder, (Map<?, ?>) value);
    } else if (value instanceof Collection<?>) {
      appendCollection(builder, (Collection<?>) value);
    } else {
      appendString(builder, String.valueOf(value));
    }
  }

  private static void appendMap(StringBuilder builder, Map<?, ?> map) {
    builder.append('{');
    Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<?, ?> entry = iterator.next();
      appendString(builder, String.valueOf(entry.getKey()));
      builder.append(':');
      appendJson(builder, entry.getValue());
      if (iterator.hasNext()) {
        builder.append(',');
      }
    }
    builder.append('}');
  }

  private static void appendCollection(StringBuilder builder, Collection<?> values) {
    builder.append('[');
    Iterator<?> iterator = values.iterator();
    while (iterator.hasNext()) {
      appendJson(builder, iterator.next());
      if (iterator.hasNext()) {
        builder.append(',');
      }
    }
    builder.append(']');
  }

  private static void appendString(StringBuilder builder, String value) {
    builder.append('"');
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      switch (character) {
        case '"':
          builder.append("\\\"");
          break;
        case '\\':
          builder.append("\\\\");
          break;
        case '\b':
          builder.append("\\b");
          break;
        case '\f':
          builder.append("\\f");
          break;
        case '\n':
          builder.append("\\n");
          break;
        case '\r':
          builder.append("\\r");
          break;
        case '\t':
          builder.append("\\t");
          break;
        default:
          if (character < 0x20) {
            builder.append(String.format("\\u%04x", (int) character));
          } else {
            builder.append(character);
          }
          break;
      }
    }
    builder.append('"');
  }
}
