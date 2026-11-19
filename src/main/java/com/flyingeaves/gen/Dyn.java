package com.flyingeaves.gen;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Python's dynamic values as the generator's dicts hold them (JSON requests, spec dicts): truthiness, int(), get. */
public final class Dyn {
    private Dyn() {
    }

    /** Python {@code bool(v)}. */
    public static boolean truthy(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof Integer i) return i != 0;
        if (v instanceof Long l) return l != 0;
        if (v instanceof Double d) return d != 0.0;
        if (v instanceof Number n) return n.doubleValue() != 0;
        if (v instanceof String s) return !s.isEmpty();
        if (v instanceof Collection<?> c) return !c.isEmpty();
        if (v instanceof Map<?, ?> m) return !m.isEmpty();
        if (v instanceof int[] a) return a.length > 0;
        if (v instanceof Object[] a) return a.length > 0;
        return true;
    }

    /** Python {@code int(v)} for an int, float (truncated), bool or str. */
    public static int toInt(Object v) {
        if (v instanceof Integer i) return i;
        if (v instanceof Long l) return Math.toIntExact(l);
        if (v instanceof Double d) return (int) com.flyingeaves.gen.py.Py.intOf(d);
        if (v instanceof Boolean b) return b ? 1 : 0;
        if (v instanceof String s) return Integer.parseInt(s.strip());
        if (v instanceof Number n) return n.intValue();
        throw new IllegalArgumentException("int() argument must be a string or a number, not " + (v == null ? "NoneType" : v.getClass().getSimpleName()));
    }

    /** {@code int(m.get(k, dflt) or 0)}-style helper: the value as an int, dflt when missing or None. */
    public static int i(Map<String, ?> m, String k, int dflt) {
        Object v = m.get(k);
        return v == null ? dflt : toInt(v);
    }

    /** {@code int(m.get(k, 0) or 0)}. */
    public static int iOr0(Map<String, ?> m, String k) {
        Object v = m.get(k);
        return truthy(v) ? toInt(v) : 0;
    }

    public static boolean b(Map<String, ?> m, String k) {
        return truthy(m.get(k));
    }

    public static String s(Map<String, ?> m, String k) {
        Object v = m.get(k);
        return v == null ? null : v instanceof String s ? s : String.valueOf(v);
    }

    /** {@code m.get(k, dflt)} as a string (dflt when the key is missing; None stays None). */
    public static String s(Map<String, ?> m, String k, String dflt) {
        if (!m.containsKey(k)) return dflt;
        Object v = m.get(k);
        return v == null ? null : v instanceof String s ? s : String.valueOf(v);
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Object v) {
        return (List<Object>) v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object v) {
        return (Map<String, Object>) v;
    }

    /** Python {@code a == b} for the values a request holds (1 == 1.0 == True). */
    public static boolean eq(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a instanceof Number x && b instanceof Number y) return x.doubleValue() == y.doubleValue();
        if (a instanceof Boolean x && b instanceof Number y) return (x ? 1 : 0) == y.doubleValue();
        if (b instanceof Boolean y && a instanceof Number x) return (y ? 1 : 0) == x.doubleValue();
        return a.equals(b);
    }

    /** Python {@code str(v)} for simple values. */
    public static String str(Object v) {
        if (v == null) return "None";
        if (v instanceof Boolean b) return b ? "True" : "False";
        if (v instanceof Double d) return com.flyingeaves.gen.py.Py.repr(d);
        return String.valueOf(v);
    }
}
