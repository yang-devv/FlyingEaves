package com.flyingeaves.gen.py;

/**
 * A Java value that stands for a Python object with a known, unsalted {@code hash()} (in practice: a tuple of ints).
 * Implementations must also implement {@code equals}/{@code hashCode} consistently with Python {@code ==}.
 * {@link PySet} uses {@link #pyHash()} for slot placement, so iteration order matches CPython.
 */
public interface PyHashable {
    /** CPython's {@code hash(self)} (64-bit, signed). */
    long pyHash();
}
