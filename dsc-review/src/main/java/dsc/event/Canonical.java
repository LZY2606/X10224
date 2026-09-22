package dsc.event;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Canonical JSON serialization for content hashing: object keys are sorted, no
 * whitespace. Numbers are rendered without locale surprises. Lists keep order.
 */
public final class Canonical {

    private Canonical() {
    }

    public static String json(Object value) {
        StringBuilder out = new StringBuilder();
        write(out, value);
        return out.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof Boolean || value instanceof Number) {
            out.append(value.toString());
        } else if (value instanceof String) {
            writeString(out, (String) value);
        } else if (value instanceof Map) {
            writeMap(out, (Map<String, Object>) value);
        } else if (value instanceof Collection) {
            writeList(out, (Collection<?>) value);
        } else {
            writeString(out, value.toString());
        }
    }

    private static void writeMap(StringBuilder out, Map<String, Object> map) {
        TreeMap<String, Object> sorted = new TreeMap<>(map);
        out.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : sorted.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            writeString(out, e.getKey());
            out.append(':');
            write(out, e.getValue());
        }
        out.append('}');
    }

    private static void writeList(StringBuilder out, Collection<?> list) {
        out.append('[');
        boolean first = true;
        for (Object item : list) {
            if (!first) {
                out.append(',');
            }
            first = false;
            write(out, item);
        }
        out.append(']');
    }

    private static void writeString(StringBuilder out, String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    public static List<Object> list(Object... items) {
        return new java.util.ArrayList<>(List.of(items));
    }
}
