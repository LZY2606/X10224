package com.dsc.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal JSON reader/writer: Map, List, String, Double, Boolean, null. */
public final class Json {
    private Json() {}

    public static Map<String, Object> obj() { return new LinkedHashMap<>(); }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObj(String s) { return (Map<String, Object>) parse(s); }

    @SuppressWarnings("unchecked")
    public static List<Object> asList(String s) { return (List<Object>) parse(s); }

    public static Object parse(String s) {
        return new Parser(s).parseValue();
    }

    public static String write(Object v) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, v);
        return sb.toString();
    }

    public static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? null : v.toString();
    }

    public static double num(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v instanceof Number n) return n.doubleValue();
        if (v == null) return Double.NaN;
        return Double.parseDouble(v.toString());
    }

    public static long lng(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(v.toString());
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v instanceof List ? (List<Object>) v : new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v instanceof Map ? (Map<String, Object>) v : null;
    }

    private static void writeValue(StringBuilder sb, Object v) {
        if (v == null) { sb.append("null"); return; }
        if (v instanceof String s) { writeString(sb, s); return; }
        if (v instanceof Boolean b) { sb.append(b); return; }
        if (v instanceof Double d) {
            if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) sb.append(d.longValue());
            else sb.append(d);
            return;
        }
        if (v instanceof Number n) { sb.append(n); return; }
        if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                writeString(sb, e.getKey().toString());
                sb.append(':');
                writeValue(sb, e.getValue());
            }
            sb.append('}');
            return;
        }
        if (v instanceof List<?> l) {
            sb.append('[');
            boolean first = true;
            for (Object o : l) {
                if (!first) sb.append(',');
                first = false;
                writeValue(sb, o);
            }
            sb.append(']');
            return;
        }
        if (v instanceof double[] arr) {
            sb.append('[');
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(arr[i]);
            }
            sb.append(']');
            return;
        }
        writeString(sb, v.toString());
    }

    private static void writeString(StringBuilder sb, String s) {
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

    private static final class Parser {
        private final String s;
        private int pos;
        Parser(String s) { this.s = s; }

        Object parseValue() {
            ws();
            char c = peek();
            if (c == '{') return parseObj();
            if (c == '[') return parseArr();
            if (c == '"') return parseStr();
            if (c == 't') { expect("true"); return Boolean.TRUE; }
            if (c == 'f') { expect("false"); return Boolean.FALSE; }
            if (c == 'n') { expect("null"); return null; }
            return parseNum();
        }

        private Map<String, Object> parseObj() {
            Map<String, Object> m = new LinkedHashMap<>();
            pos++;
            ws();
            if (peek() == '}') { pos++; return m; }
            while (true) {
                ws();
                String k = parseStr();
                ws();
                if (s.charAt(pos) != ':') throw new IllegalArgumentException("expected ':' at " + pos);
                pos++;
                m.put(k, parseValue());
                ws();
                char c = s.charAt(pos++);
                if (c == '}') return m;
                if (c != ',') throw new IllegalArgumentException("expected ',' at " + pos);
            }
        }

        private List<Object> parseArr() {
            List<Object> l = new ArrayList<>();
            pos++;
            ws();
            if (peek() == ']') { pos++; return l; }
            while (true) {
                l.add(parseValue());
                ws();
                char c = s.charAt(pos++);
                if (c == ']') return l;
                if (c != ',') throw new IllegalArgumentException("expected ',' at " + pos);
            }
        }

        private String parseStr() {
            if (s.charAt(pos) != '"') throw new IllegalArgumentException("expected string at " + pos);
            pos++;
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = s.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    char e = s.charAt(pos++);
                    switch (e) {
                        case 'n' -> sb.append('\n');
                        case 't' -> sb.append('\t');
                        case 'r' -> sb.append('\r');
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'u' -> {
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                            pos += 4;
                        }
                        default -> sb.append(e);
                    }
                } else sb.append(c);
            }
        }

        private Double parseNum() {
            int start = pos;
            while (pos < s.length() && "-+0123456789.eE".indexOf(s.charAt(pos)) >= 0) pos++;
            return Double.parseDouble(s.substring(start, pos));
        }

        private void expect(String lit) {
            if (!s.startsWith(lit, pos)) throw new IllegalArgumentException("expected " + lit + " at " + pos);
            pos += lit.length();
        }

        private char peek() { return s.charAt(pos); }

        private void ws() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }
    }
}
