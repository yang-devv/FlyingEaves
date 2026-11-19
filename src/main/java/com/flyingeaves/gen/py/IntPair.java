package com.flyingeaves.gen.py;

/**
 * The Python tuple {@code (a, b)} of two ints, e.g. a local cell {@code (lz, lx)} or a world column {@code (x, z)}.
 * Hashes like CPython ({@link PyHash#ofTuple(long, long)}), so a {@link PySet} of these iterates in CPython order.
 * Python compares tuples lexicographically; {@link #compareTo} does the same.
 */
public record IntPair(int a, int b) implements PyHashable, Comparable<IntPair> {
    public static IntPair of(int a, int b) {
        return new IntPair(a, b);
    }

    @Override
    public long pyHash() {
        return PyHash.ofTuple(a, b);
    }

    @Override
    public int compareTo(IntPair o) {
        int c = Integer.compare(a, o.a);
        return c != 0 ? c : Integer.compare(b, o.b);
    }

    @Override
    public String toString() {
        return "(" + a + ", " + b + ")";
    }
}
