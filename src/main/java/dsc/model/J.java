package dsc.model;

import com.google.gson.*;
import java.util.*;

/** JSON helpers: Gson with deterministic (sorted) canonical form for hashing. */
public final class J {
    private static final Gson G = new GsonBuilder().serializeNulls().create();
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    private J() {}

    public static JsonObject obj() { return new JsonObject(); }
    public static JsonArray arr() { return new JsonArray(); }

    public static JsonObject obj(String k, Object v) {
        JsonObject o = obj();
        put(o, k, v);
        return o;
    }

    public static void put(JsonObject o, String k, Object v) {
        if (v == null) o.add(k, JsonNull.INSTANCE);
        else if (v instanceof JsonElement e) o.add(k, e);
        else if (v instanceof Boolean b) o.addProperty(k, b);
        else if (v instanceof Number n) o.addProperty(k, n);
        else if (v instanceof Character c) o.addProperty(k, c);
        else o.addProperty(k, v.toString());
    }

    public static JsonObject parse(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    public static String string(JsonElement e) { return G.toJson(e); }
    public static String pretty(JsonElement e) { return PRETTY.toJson(e); }

    public static JsonObject copy(JsonObject o) { return parse(string(o)); }

    /** Canonical JSON: recursively sort object keys, no whitespace. Basis of content hashes. */
    public static String canonical(JsonElement e) {
        StringBuilder sb = new StringBuilder();
        writeCanonical(e, sb);
        return sb.toString();
    }

    private static void writeCanonical(JsonElement e, StringBuilder sb) {
        if (e == null || e.isJsonNull()) sb.append("null");
        else if (e.isJsonPrimitive()) {
            JsonPrimitive p = e.getAsJsonPrimitive();
            if (p.isNumber()) {
                double d = p.getAsDouble();
                if (d == Math.rint(d) && !Double.isInfinite(d)) sb.append(Long.toString((long) d));
                else sb.append(Double.toString(d));
            } else if (p.isBoolean()) sb.append(p.getAsBoolean());
            else sb.append(quote(p.getAsString()));
        } else if (e.isJsonArray()) {
            sb.append('[');
            boolean first = true;
            for (JsonElement x : e.getAsJsonArray()) {
                if (!first) sb.append(',');
                writeCanonical(x, sb);
                first = false;
            }
            sb.append(']');
        } else {
            TreeMap<String, JsonElement> sorted = new TreeMap<>();
            for (Map.Entry<String, JsonElement> en : e.getAsJsonObject().entrySet()) {
                sorted.put(en.getKey(), en.getValue());
            }
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, JsonElement> en : sorted.entrySet()) {
                if (!first) sb.append(',');
                sb.append(quote(en.getKey())).append(':');
                writeCanonical(en.getValue(), sb);
                first = false;
            }
            sb.append('}');
        }
    }

    public static String str(JsonObject o, String key, String def) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
    }

    public static double num(JsonObject o, String key, double def) {
        return o.has(key) && o.getAsJsonPrimitive(key).isNumber() ? o.get(key).getAsDouble() : def;
    }

    public static JsonArray arrOrEmpty(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : arr();
    }
}

    /** Minimal RFC-8259 style string quoting for canonical form. */
    static String quote(String raw) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('\"').toString();
    }
