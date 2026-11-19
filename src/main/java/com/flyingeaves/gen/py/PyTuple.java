package com.flyingeaves.gen.py;

import java.util.List;

/**
 * A general Python tuple of hashable items (ints, nested tuples). Prefer {@link IntPair}/{@link IntTriple} for the
 * common int cases. Equality is element-wise Python equality ({@link PyHash#eq}); do not mix this class with
 * IntPair/IntTriple inside one set (Java would not consider them equal although Python would).
 */
public record PyTuple(List<Object> items) implements PyHashable {
    public PyTuple {
        items = List.copyOf(items);
    }

    public static PyTuple of(Object... items) {
        return new PyTuple(List.of(items));
    }

    @Override
    public long pyHash() {
        return PyHash.ofTupleItems(items);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PyTuple t) || t.items.size() != items.size()) return false;
        for (int i = 0; i < items.size(); i++) if (!PyHash.eq(items.get(i), t.items.get(i))) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(pyHash());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(items.get(i));
        }
        if (items.size() == 1) sb.append(',');
        return sb.append(')').toString();
    }
}
