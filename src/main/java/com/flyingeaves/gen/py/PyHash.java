package com.flyingeaves.gen.py;

import java.math.BigInteger;
import java.util.List;

/**
 * CPython 3.13 {@code hash()} for the key types the generator puts in sets and dicts, and Python equality for them.
 * <ul>
 * <li>int: {@code n mod (2^61 - 1)}, negatives as {@code -(|n| mod (2^61-1))}, and -1 maps to -2. bool hashes as 0/1.
 *     numpy integer scalars hash the same as Python ints.</li>
 * <li>tuple: the xxHash-style {@code tuplehash} of {@code Objects/tupleobject.c}.</li>
 * </ul>
 * Supported Java representations: {@link Integer}, {@link Long}, {@link Short}, {@link Byte}, {@link Boolean},
 * {@link BigInteger} (ints), {@link PyHashable} (tuples such as {@link IntPair}, {@link IntTriple}, {@link PyTuple}),
 * and {@link String} (see {@link #ofStr}).
 * <p>
 * Strings: CPython salts {@code str} hashes per process unless {@code PYTHONHASHSEED} is fixed. {@link #ofStr} is the
 * str hash with {@code PYTHONHASHSEED=0} (no salt), for restoring sets of strings with their CPython order. (The
 * building generator itself does not depend on string hashes.)
 */
public final class PyHash {
    private PyHash() {
    }

    /** 2^61 - 1, the modulus of CPython's numeric hash. */
    public static final long MODULUS = (1L << 61) - 1;
    private static final long P1 = 0x9E3779B185EBCA87L; // 11400714785074694791
    private static final long P2 = 0xC2B2AE3D27D4EB4FL; // 14029467366897019727
    private static final long P5 = 0x27D4EB2F165667C5L; // 2870177450012600261

    /** {@code hash(n)} for a Python int. */
    public static long ofInt(long n) {
        if (n >= 0) return n % MODULUS;
        long h = -Long.remainderUnsigned(-n, MODULUS); // -n as unsigned also covers Long.MIN_VALUE
        return h == -1 ? -2 : h;
    }

    /** {@code hash(n)} for an arbitrary-size Python int. */
    public static long ofInt(BigInteger n) {
        long h = n.abs().mod(BigInteger.valueOf(MODULUS)).longValue();
        if (n.signum() < 0) h = -h;
        return h == -1 ? -2 : h;
    }

    /** Start value of the tuple hash accumulator. */
    public static long tupleStart() {
        return P5;
    }

    /** Feed one item hash ("lane") into the tuple hash accumulator. */
    public static long tupleStep(long acc, long laneHash) {
        acc += laneHash * P2;
        acc = Long.rotateLeft(acc, 31);
        return acc * P1;
    }

    /** Finish the tuple hash for a tuple of {@code len} items. */
    public static long tupleFinish(long acc, int len) {
        acc += len ^ (P5 ^ 3527539L);
        return acc == -1L ? 1546275796L : acc;
    }

    /** {@code hash((a, b))} for two Python ints. */
    public static long ofTuple(long a, long b) {
        return tupleFinish(tupleStep(tupleStep(P5, ofInt(a)), ofInt(b)), 2);
    }

    /** {@code hash((a, b, c))} for three Python ints. */
    public static long ofTuple(long a, long b, long c) {
        return tupleFinish(tupleStep(tupleStep(tupleStep(P5, ofInt(a)), ofInt(b)), ofInt(c)), 3);
    }

    /** {@code hash(tuple(items))} for a tuple of supported objects. */
    public static long ofTupleItems(List<?> items) {
        long acc = P5;
        for (Object o : items) acc = tupleStep(acc, of(o));
        return tupleFinish(acc, items.size());
    }

    /**
     * {@code hash(s)} of a Python str under {@code PYTHONHASHSEED=0}: SipHash-1-3 with the all-zero key
     * ({@code Python/pyhash.c}, {@code sys.hash_info.algorithm == 'siphash13'}, {@code cutoff == 0}) over the
     * string's internal PEP 393 buffer: 1 byte per char if every code point is below 256 (latin-1), else 2 bytes
     * (UTF-16LE, BMP only), else 4 bytes (UTF-32LE). The empty string hashes to 0 and -1 maps to -2.
     * Test values: {@code hash('a') = 4644417185603328019}, {@code hash('minecraft:plains') = 995151692255534335}.
     */
    public static long ofStr(String s) {
        int n = s.codePointCount(0, s.length());
        if (n == 0) return 0;
        int max = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            max = Math.max(max, cp);
            i += Character.charCount(cp);
        }
        int kind = max < 256 ? 1 : max < 65536 ? 2 : 4;
        byte[] buf = new byte[n * kind];
        int j = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            for (int b = 0; b < kind; b++) buf[j++] = (byte) (cp >>> (8 * b));
        }
        long h = siphash13(0L, 0L, buf);
        return h == -1 ? -2 : h;
    }

    /** CPython's {@code siphash13(k0, k1, src, len)} ({@code Python/pyhash.c}). */
    static long siphash13(long k0, long k1, byte[] src) {
        int len = src.length;
        long b = ((long) len) << 56;
        long[] v = {k0 ^ 0x736f6d6570736575L, k1 ^ 0x646f72616e646f6dL, k0 ^ 0x6c7967656e657261L, k1 ^ 0x7465646279746573L};
        int off = 0;
        while (len - off >= 8) {
            long mi = 0;
            for (int i = 7; i >= 0; i--) mi = (mi << 8) | (src[off + i] & 0xffL);
            off += 8;
            v[3] ^= mi;
            sipRound(v);
            v[0] ^= mi;
        }
        long t = 0;
        for (int i = len - off - 1; i >= 0; i--) t = (t << 8) | (src[off + i] & 0xffL);
        b |= t;
        v[3] ^= b;
        sipRound(v);
        v[0] ^= b;
        v[2] ^= 0xff;
        sipRound(v);
        sipRound(v);
        sipRound(v);
        return (v[0] ^ v[1]) ^ (v[2] ^ v[3]);
    }

    /** {@code SINGLE_ROUND}: {@code HALF_ROUND(v0,v1,v2,v3,13,16); HALF_ROUND(v2,v1,v0,v3,17,21)}. */
    private static void sipRound(long[] v) {
        long a = v[0], b = v[1], c = v[2], d = v[3];
        a += b;
        c += d;
        b = Long.rotateLeft(b, 13) ^ a;
        d = Long.rotateLeft(d, 16) ^ c;
        a = Long.rotateLeft(a, 32);
        c += b;
        a += d;
        b = Long.rotateLeft(b, 17) ^ c;
        d = Long.rotateLeft(d, 21) ^ a;
        c = Long.rotateLeft(c, 32);
        v[0] = a;
        v[1] = b;
        v[2] = c;
        v[3] = d;
    }

    /** {@code hash(o)} for any supported key object. */
    public static long of(Object o) {
        if (o instanceof PyHashable p) return p.pyHash();
        if (o instanceof String s) return ofStr(s);
        if (o instanceof Integer i) return ofInt(i);
        if (o instanceof Long l) return ofInt(l);
        if (o instanceof Short s) return ofInt(s);
        if (o instanceof Byte b) return ofInt(b);
        if (o instanceof Boolean b) return b ? 1 : 0;
        if (o instanceof BigInteger b) return ofInt(b);
        throw new IllegalArgumentException("no CPython hash emulation for " + (o == null ? "null" : o.getClass().getName()));
    }

    /** Python {@code ==} for supported keys: integral numbers compare by value across Java types. */
    public static boolean eq(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (isIntegral(a) && isIntegral(b)) {
            if (a instanceof BigInteger || b instanceof BigInteger) return toBig(a).equals(toBig(b));
            return longValue(a) == longValue(b);
        }
        return a.equals(b);
    }

    private static boolean isIntegral(Object o) {
        return o instanceof Integer || o instanceof Long || o instanceof Short || o instanceof Byte
                || o instanceof Boolean || o instanceof BigInteger;
    }

    private static long longValue(Object o) {
        if (o instanceof Boolean b) return b ? 1 : 0;
        return ((Number) o).longValue();
    }

    private static BigInteger toBig(Object o) {
        if (o instanceof BigInteger b) return b;
        return BigInteger.valueOf(longValue(o));
    }
}
