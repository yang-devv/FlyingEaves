package com.flyingeaves.gen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON as Python's {@code json} module reads and writes it: objects are insertion-ordered maps ({@code dict}),
 * arrays lists, integers {@link Long} (Python int), numbers with a fraction or exponent {@link Double}, plus
 * String, Boolean and null (None). {@link #write} matches {@code json.dumps(x, ensure_ascii=False)} (separators
 * {@code ", "} and {@code ": "}) for the values the generator writes.
 */
public final class Json {
    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    public static Object parse(String text) {
        Json j = new Json(text);
        j.ws();
        Object v = j.value();
        j.ws();
        if (j.i != j.s.length()) throw j.err("trailing data");
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        return (Map<String, Object>) parse(text);
    }

    private RuntimeException err(String m) {
        return new IllegalArgumentException("JSON: " + m + " at " + i);
    }

    private void ws() {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') i++;
            else break;
        }
    }

    private Object value() {
        if (i >= s.length()) throw err("end of input");
        char c = s.charAt(i);
        switch (c) {
            case '{': {
                i++;
                Map<String, Object> m = new LinkedHashMap<>();
                ws();
                if (s.charAt(i) == '}') {
                    i++;
                    return m;
                }
                while (true) {
                    ws();
                    String k = string();
                    ws();
                    if (s.charAt(i) != ':') throw err("':' expected");
                    i++;
                    ws();
                    m.put(k, value());
                    ws();
                    char d = s.charAt(i++);
                    if (d == '}') return m;
                    if (d != ',') throw err("',' or '}' expected");
                }
            }
            case '[': {
                i++;
                List<Object> l = new ArrayList<>();
                ws();
                if (s.charAt(i) == ']') {
                    i++;
                    return l;
                }
                while (true) {
                    ws();
                    l.add(value());
                    ws();
                    char d = s.charAt(i++);
                    if (d == ']') return l;
                    if (d != ',') throw err("',' or ']' expected");
                }
            }
            case '"':
                return string();
            case 't':
                expect("true");
                return Boolean.TRUE;
            case 'f':
                expect("false");
                return Boolean.FALSE;
            case 'n':
                expect("null");
                return null;
            default:
                return number();
        }
    }

    private void expect(String w) {
        if (!s.startsWith(w, i)) throw err(w + " expected");
        i += w.length();
    }

    private Object number() {
        int st = i;
        if (s.charAt(i) == '-') i++;
        boolean frac = false;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') i++;
            else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                frac = true;
                i++;
            } else break;
        }
        String t = s.substring(st, i);
        if (t.isEmpty() || t.equals("-")) throw err("value expected");
        if (frac) return Double.parseDouble(t);
        try {
            return Long.parseLong(t);
        } catch (NumberFormatException e) {
            return new java.math.BigInteger(t);
        }
    }

    private String string() {
        if (s.charAt(i) != '"') throw err("string expected");
        i++;
        StringBuilder b = new StringBuilder();
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c == '\\') {
                char e = s.charAt(i++);
                switch (e) {
                    case 'n' -> b.append('\n');
                    case 't' -> b.append('\t');
                    case 'r' -> b.append('\r');
                    case 'b' -> b.append('\b');
                    case 'f' -> b.append('\f');
                    case 'u' -> {
                        b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> b.append(e);
                }
            } else b.append(c);
        }
    }

    // ------------------------------------------------------------------------------------------ writing

    /** {@code json.dumps(v, ensure_ascii=False)}; sortKeys: {@code sort_keys=True}. */
    public static String write(Object v, boolean sortKeys) {
        StringBuilder b = new StringBuilder();
        write(b, v, sortKeys);
        return b.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(StringBuilder b, Object v, boolean sortKeys) {
        if (v == null) b.append("null");
        else if (v instanceof Boolean x) b.append(x ? "true" : "false");
        else if (v instanceof Double d) b.append(com.flyingeaves.gen.py.Py.repr(d));
        else if (v instanceof Float d) b.append(com.flyingeaves.gen.py.Py.repr(d.doubleValue()));
        else if (v instanceof Number n) b.append(n.toString());
        else if (v instanceof String x) b.append(quote(x));
        else if (v instanceof Map<?, ?> m) {
            b.append('{');
            List<Object> keys = new ArrayList<>(m.keySet());
            if (sortKeys) keys.sort((a, c) -> String.valueOf(a).compareTo(String.valueOf(c)));
            boolean first = true;
            for (Object k : keys) {
                if (!first) b.append(", ");
                first = false;
                b.append(quote(String.valueOf(k)));
                b.append(": ");
                write(b, m.get(k), sortKeys);
            }
            b.append('}');
        } else if (v instanceof Iterable<?> l) {
            b.append('[');
            boolean first = true;
            for (Object o : l) {
                if (!first) b.append(", ");
                first = false;
                write(b, o, sortKeys);
            }
            b.append(']');
        } else if (v instanceof int[] a) {
            List<Object> l = new ArrayList<>();
            for (int x : a) l.add(x);
            write(b, l, sortKeys);
        } else throw new IllegalArgumentException("not JSON: " + v.getClass());
    }

    /** A JSON string literal as {@code json.dumps(s, ensure_ascii=False)} writes it. */
    public static String quote(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                case '\b' -> b.append("\\b");
                case '\f' -> b.append("\\f");
                default -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.append('"').toString();
    }
}
