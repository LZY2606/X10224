package dsc.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON parser/serializer used for archive payloads.
 * Preserves member insertion order and keeps raw representations as strings
 * supplied by callers; this class never guesses number formats.
 */
public final class Json {
    private Json() {
    }

    public static final Object NIL = null;

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object v) {
        return (Map<String, Object>) v;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> arr(Object v) {
        return (List<Object>) v;
    }

    public static String str(Object v, String key) {
        Object x = obj(v).get(key);
        return x == null ? null : x.toString();
    }

    public static double num(Object v, String key) {
        Object x = obj(v).get(key);
        return x instanceof Number n ? n.doubleValue() : Double.NaN;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> objs(Object v) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (v instanceof List<?> l) {
            for (Object o : l) {
                out.add((Map<String, Object>) o);
            }
        }
        return out;
    }

    public static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    public static List<Object> list(Object... vs) {
        List<Object> l = new ArrayList<>();
        for (Object v : vs) {
            l.add(v);
        }
        return l;
    }

    public static String write(Object v) {
        StringBuilder sb = new StringBuilder();
        writeTo(sb, v);
        return sb.toString();
    }

    private static void writeTo(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            writeString(sb, s);
        } else if (v instanceof Boolean b) {
            sb.append(b.booleanValue());
        } else if (v instanceof Number n) {
            writeNumber(sb, n);
        } else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeString(sb, e.getKey().toString());
                sb.append(':');
                writeTo(sb, e.getValue());
            }
            sb.append('}');
        } else if (v instanceof List<?> l) {
            sb.append('[');
            boolean first = true;
            for (Object o : l) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeTo(sb, o);
            }
            sb.append(']');
        } else {
            writeString(sb, v.toString());
        }
    }

    private static void writeNumber(StringBuilder sb, Number n) {
        double d = n.doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            writeString(sb, String.valueOf(n));
            return;
        }
        if (n instanceof Double || n instanceof Float) {
            if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                sb.append((long) d);
            } else {
                sb.append(n.toString());
            }
        } else {
            sb.append(n.toString());
        }
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
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
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

    public static Object parse(String text) {
        Parser p = new Parser(text);
        Object v = p.readValue();
        p.skipWs();
        if (!p.eof()) {
            throw new IllegalArgumentException("trailing characters at " + p.pos);
        }
        return v;
    }

    private static final class Parser {
        final String s;
        int pos;

        Parser(String s) {
            this.s = s;
        }

        boolean eof() {
            return pos >= s.length();
        }

        void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        Object readValue() {
            skipWs();
            if (eof()) {
                throw new IllegalArgumentException("unexpected end");
            }
            char c = s.charAt(pos);
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
            if (peek() == '}') {
                pos++;
                return m;
            }
            while (true) {
                skipWs();
                String key = readString();
                skipWs();
                expect(':');
                Object val = readValue();
                m.put(key, val);
                skipWs();
                char c = next();
                if (c == '}') {
                    return m;
                }
                if (c != ',') {
                    throw new IllegalArgumentException("expected , or } at " + pos);
                }
            }
        }

        List<Object> readArray() {
            List<Object> l = new ArrayList<>();
            expect('[');
            skipWs();
            if (peek() == ']') {
                pos++;
                return l;
            }
            while (true) {
                l.add(readValue());
                skipWs();
                char c = next();
                if (c == ']') {
                    return l;
                }
                if (c != ',') {
                    throw new IllegalArgumentException("expected , or ] at " + pos);
                }
            }
        }

        String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    char e = next();
                    switch (e) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            String hex = s.substring(pos, pos + 4);
                            pos += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                        }
                        default -> throw new IllegalArgumentException("bad escape \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        Boolean readBool() {
            if (s.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (s.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("bad literal at " + pos);
        }

        Object readNull() {
            if (s.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IllegalArgumentException("bad literal at " + pos);
        }

        Number readNumber() {
            int start = pos;
            if (peek() == '-') {
                pos++;
            }
            boolean dot = false;
            boolean exp = false;
            while (!eof()) {
                char c = peek();
                if (c >= '0' && c <= '9') {
                    pos++;
                } else if (c == '.' && !dot && !exp) {
                    dot = true;
                    pos++;
                } else if ((c == 'e' || c == 'E') && !exp) {
                    exp = true;
                    pos++;
                    if (peek() == '+' || peek() == '-') {
                        pos++;
                    }
                } else {
                    break;
                }
            }
            String t = s.substring(start, pos);
            if (t.isEmpty() || "-".equals(t)) {
                throw new IllegalArgumentException("bad number at " + start);
            }
            return dot || exp ? Double.parseDouble(t) : Long.parseLong(t);
        }

        char peek() {
            if (eof()) {
                throw new IllegalArgumentException("unexpected end");
            }
            return s.charAt(pos);
        }

        char next() {
            char c = peek();
            pos++;
            return c;
        }

        void expect(char c) {
            skipWs();
            char a = next();
            if (a != c) {
                throw new IllegalArgumentException("expected '" + c + "' at " + (pos - 1));
            }
        }
    }
}
