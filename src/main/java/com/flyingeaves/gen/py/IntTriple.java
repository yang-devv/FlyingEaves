package com.flyingeaves.gen.py;

/**
 * The Python tuple {@code (a, b, c)} of three ints, e.g. a world position {@code (x, y, z)}.
 * Hashes like CPython ({@link PyHash#ofTuple(long, long, long)}); compares lexicographically like Python tuples.
 */
public record IntTriple(int a, int b, int c) implements PyHashable, Comparable<IntTriple> {
    public static IntTriple of(int a, int b, int c) {
        return new IntTriple(a, b, c);
    }

    @Override
    public long pyHash() {
        return PyHash.ofTuple(a, b, c);
    }

    @Override
    public int compareTo(IntTriple o) {
        int x = Integer.compare(a, o.a);
        if (x != 0) return x;
        x = Integer.compare(b, o.b);
        return x != 0 ? x : Integer.compare(c, o.c);
    }

    @Override
    public String toString() {
        return "(" + a + ", " + b + ", " + c + ")";
    }
}
