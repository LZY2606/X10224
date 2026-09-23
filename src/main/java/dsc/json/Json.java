package dsc.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON parser / writer used by the whole application.
 * Deliberately dependency-free; preserves insertion order via LinkedHashMap.
 */
public final class Json {

    private Json() {}

    // ------------------------------------------------------------------ parse

    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWs();
        Object v = p.readValue();
        p.skipWs();
        if (p.pos != p.src.length()) {
            throw new IllegalArgumentException("Trailing characters at " + p.pos);
        }
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object o = parse(text);
        if (!(o instanceof Map)) {
            throw new IllegalArgumentException("Expected JSON object");
        }
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> parseArray(String text) {
        Object o = parse(text);
        if (!(o instanceof List)) {
            throw new IllegalArgumentException("Expected JSON array");
        }
        return (List<Object>) o;
    }

    // ------------------------------------------------------------------ write

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeTo(sb, value, 0);
        return sb.toString();
    }

    public static String writePretty(Object value) {
        StringBuilder sb = new StringBuilder();
        writePretty(sb, value, 0);
        sb.append('\n');
        return sb.toString();
    }

    private static void writePretty(StringBuilder sb, Object value, int indent) {
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append("{\n");
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) sb.append(",\n");
                first = false;
                indent(sb, indent + 1);
                writeStr(sb, String.valueOf(e.getKey()));
                sb.append(": ");
                writePretty(sb, e.getValue(), indent + 1);
            }
            sb.append('\n');
            indent(sb, indent);
            sb.append('}');
        } else if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append("[\n");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",\n");
                indent(sb, indent + 1);
                writePretty(sb, list.get(i), indent + 1);
            }
            sb.append('\n');
            indent(sb, indent);
            sb.append(']');
        } else {
            writeTo(sb, value, 0);
        }
    }

    private static void indent(StringBuilder sb, int n) {
        sb.append("  ".repeat(n));
    }

    private static void writeTo(StringBuilder sb, Object value, int depth) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Boolean b) {
            sb.append(b.booleanValue());
        } else if (value instanceof Number n) {
            double d = n.doubleValue();
            if (!Double.isFinite(d)) {
                sb.append("null");
            } else if (d == Math.rint(d) && Math.abs(d) < 1e15) {
                sb.append(Long.toString((long) d));
            } else {
                sb.append(Double.toString(d));
            }
        } else if (value instanceof String s) {
            writeStr(sb, s);
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                writeStr(sb, String.valueOf(e.getKey()));
                sb.append(':');
                writeTo(sb, e.getValue(), depth + 1);
            }
            sb.append('}');
        } else if (value instanceof List<?> list) {
            sb.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(',');
                writeTo(sb, list.get(i), depth + 1);
            }
            sb.append(']');
        } else {
            writeStr(sb, value.toString());
        }
    }

    private static void writeStr(StringBuilder sb, String s) {
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
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------- accessors

    public static String str(Map<String, Object> m, String key) {
        Object o = m.get(key);
        return o == null ? null : o.toString();
    }

    public static double num(Map<String, Object> m, String key, double fallback) {
        Object o = m.get(key);
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof String s && !s.isBlank()) {
            try { return Double.parseDouble(s.trim()); } catch (NumberFormatException ignored) {}
        }
        return fallback;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Map<String, Object> m, String key) {
        Object o = m.get(key);
        return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> arr(Object o) {
        return o instanceof List ? (List<Object>) o : new ArrayList<>();
    }

    // ---------------------------------------------------------------- parser

    private static final class Parser {
        final String src;
        int pos;

        Parser(String src) {
            this.src = src;
        }

        void skipWs() {
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else if (c == '/' && pos + 1 < src.length() && src.charAt(pos + 1) == '/') {
                    while (pos < src.length() && src.charAt(pos) != '\n') pos++;
                } else {
                    break;
                }
            }
        }

        Object readValue() {
            skipWs();
            if (pos >= src.length()) throw error("Unexpected end");
            char c = src.charAt(pos);
            return switch (c) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't', 'f' -> readBool();
                case 'n' -> readNull();
                default -> readNumber();
            };
        }

        Map<String, Object> readObject() {
            Map<String, Object> m = new LinkedHashMap<>();
            expect('{');
            skipWs();
            if (peek() == '}') { pos++; return m; }
            while (true) {
                skipWs();
                String key = readString();
                skipWs();
                expect(':');
                Object val = readValue();
                m.put(key, val);
                skipWs();
                char c = next();
                if (c == '}') return m;
                if (c != ',') throw error("Expected , or }");
            }
        }

        List<Object> readArray() {
            List<Object> list = new ArrayList<>();
            expect('[');
            skipWs();
            if (peek() == ']') { pos++; return list; }
            while (true) {
                list.add(readValue());
                skipWs();
                char c = next();
                if (c == ']') return list;
                if (c != ',') throw error("Expected , or ]");
            }
        }

        String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    char e = next();
                    switch (e) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'n' -> sb.append('\n');
                        case 't' -> sb.append('\t');
                        case 'r' -> sb.append('\r');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            String hex = src.substring(pos, pos + 4);
                            pos += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                        }
                        default -> throw error("Bad escape \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        Boolean readBool() {
            if (src.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
            if (src.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
            throw error("Bad literal");
        }

        Object readNull() {
            if (src.startsWith("null", pos)) { pos += 4; return null; }
            throw error("Bad literal");
        }

        Number readNumber() {
            int start = pos;
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if ("0123456789+-eE.".indexOf(c) < 0) break;
                pos++;
            }
            String token = src.substring(start, pos);
            if (token.isEmpty() || "+-".contains(token)) throw error("Bad number");
            if (token.contains(".") || token.contains("e") || token.contains("E")) {
                return Double.parseDouble(token);
            }
            try {
                return Long.parseLong(token);
            } catch (NumberFormatException ex) {
                return Double.parseDouble(token);
            }
        }

        char peek() {
            return pos < src.length() ? src.charAt(pos) : '\0';
        }

        char next() {
            if (pos >= src.length()) throw error("Unexpected end");
            return src.charAt(pos++);
        }

        void expect(char c) {
            char a = next();
            if (a != c) throw error("Expected '" + c + "' got '" + a + "'");
        }

        IllegalArgumentException error(String msg) {
            return new IllegalArgumentException(msg + " at position " + pos + " in: "
                    + src.substring(Math.max(0, pos - 20), Math.min(src.length(), pos + 20)));
        }
    }
}
