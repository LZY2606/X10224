package dsc;

import java.util.*;

/** Minimal JSON parse/stringify for maps, lists, strings, numbers, booleans, null. */
public final class Json {
    private Json() {}

    public static String write(Object v) {
        StringBuilder sb = new StringBuilder();
        write(v, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(Object v, StringBuilder sb) {
        if (v == null) { sb.append("null"); return; }
        if (v instanceof String s) { quote(s, sb); return; }
        if (v instanceof Number || v instanceof Boolean) { sb.append(v.toString()); return; }
        if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (var e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                quote(String.valueOf(e.getKey()), sb);
                sb.append(':');
                write(e.getValue(), sb);
            }
            sb.append('}');
            return;
        }
        if (v instanceof Iterable<?> it) {
            sb.append('[');
            boolean first = true;
            for (Object o : it) {
                if (!first) sb.append(',');
                first = false;
                write(o, sb);
            }
            sb.append(']');
            return;
        }
        if (v.getClass().isArray()) {
            sb.append('[');
            int n = java.lang.reflect.Array.getLength(v);
            for (int i = 0; i < n; i++) {
                if (i > 0) sb.append(',');
                write(java.lang.reflect.Array.get(v, i), sb);
            }
            sb.append(']');
            return;
        }
        quote(v.toString(), sb);
    }

    private static void quote(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    public static Object read(String s) {
        return new P(s).value();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(String s) { return (Map<String, Object>) read(s); }

    @SuppressWarnings("unchecked")
    public static List<Object> arr(String s) { return (List<Object>) read(s); }

    public static String str(Map<String, Object> m, String k) { Object v = m.get(k); return v == null ? null : String.valueOf(v); }
    public static String str(Map<String, Object> m, String k, String dflt) { String v = str(m, k); return v == null ? dflt : v; }
    public static double num(Map<String, Object> m, String k) { Object v = m.get(k); return v instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(v)); }
    public static long lng(Map<String, Object> m, String k) { Object v = m.get(k); return v instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(v)); }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Map<String, Object> m, String k) { Object v = m.get(k); return v instanceof List<?> l ? (List<Object>) l : List.of(); }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Map<String, Object> m, String k) { Object v = m.get(k); return v instanceof Map<?, ?> mm ? (Map<String, Object>) mm : Map.of(); }

    private static final class P {
        final String s; int i;
        P(String s) { this.s = s; }
        Object value() {
            ws();
            char c = peek();
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (c == 't') { expect("true"); return Boolean.TRUE; }
            if (c == 'f') { expect("false"); return Boolean.FALSE; }
            if (c == 'n') { expect("null"); return null; }
            return number();
        }
        char peek() { return s.charAt(i); }
        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        void expect(String w) { if (!s.startsWith(w, i)) throw new IllegalArgumentException("bad json at " + i); i += w.length(); }
        Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; ws();
            if (peek() == '}') { i++; return m; }
            while (true) {
                ws();
                String k = string();
                ws();
                if (peek() != ':') throw new IllegalArgumentException("expected : at " + i);
                i++;
                m.put(k, value());
                ws();
                char c = s.charAt(i++);
                if (c == '}') return m;
                if (c != ',') throw new IllegalArgumentException("expected , at " + i);
            }
        }
        List<Object> array() {
            List<Object> l = new ArrayList<>();
            i++; ws();
            if (peek() == ']') { i++; return l; }
            while (true) {
                l.add(value());
                ws();
                char c = s.charAt(i++);
                if (c == ']') return l;
                if (c != ',') throw new IllegalArgumentException("expected , at " + i);
            }
        }
        String string() {
            if (s.charAt(i) != '"') throw new IllegalArgumentException("expected string at " + i);
            i++;
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> { sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                        default -> sb.append(e);
                    }
                } else sb.append(c);
            }
        }
        Number number() {
            int start = i;
            while (i < s.length() && "-+0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            String t = s.substring(start, i);
            if (t.contains(".") || t.contains("e") || t.contains("E")) return Double.parseDouble(t);
            long l = Long.parseLong(t);
            return l;
        }
    }
}
