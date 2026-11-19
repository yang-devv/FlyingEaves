package com.flyingeaves.gen.py;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

/**
 * A Python {@code set} (or {@code frozenset}) that iterates in exactly the order CPython 3.13 would
 * ({@code Objects/setobject.c}): same hash ({@link PyHash}), same open-addressing table (8-slot start, 9 linear
 * probes then perturbation by 5, resize when {@code fill*5 >= mask*3}, last-dummy reuse on insertion), and the same
 * construction paths, which do <b>not</b> all give the same order for the same elements:
 * <ul>
 * <li>{@code set(list)}, set comprehensions, {@code s.add(x)}, {@code s.update(list_or_generator)}:
 *     sequential adds ({@link #of(Iterable)}, {@link #add}, {@link #update(Iterable)}).</li>
 * <li>{@code set(d)} / {@code s.update(d)} for a dict: one pre-resize, then adds in dict order
 *     ({@link #fromDictKeys}, {@link #updateFromDictKeys}).</li>
 * <li>{@code set(s)}, {@code s.copy()}, {@code frozenset(s)}, {@code s | t}, {@code s |= t}, {@code s.update(t)}
 *     for a set {@code t}: {@code set_merge} (pre-resize, slot copy or clean insertion) ({@link #copy()},
 *     {@link #or}, {@link #update(PySet)}).</li>
 * <li>{@code &}, {@code -}, {@code ^} and their in-place forms follow CPython's size-dependent strategies.</li>
 * </ul>
 * Use one PySet for every Python set in ported code, not only at the order-sensitive sites, so that iteration,
 * {@code pop()}, {@code list(s)}, {@code min(s, key)} ties etc. are right by construction. Key types: see
 * {@link PyHash} ({@link IntPair}, {@link IntTriple}, boxed ints).
 *
 * <p>Iterating while the set changes size throws {@link ConcurrentModificationException}, like CPython's
 * RuntimeError. Validated against the real interpreter by the self test ({@code pyset} vectors).
 */
public final class PySet<K> implements Iterable<K> {
    private static final Object DUMMY = new Object() {
        @Override
        public String toString() {
            return "<dummy>";
        }
    };
    private static final int MINSIZE = 8, LINEAR_PROBES = 9, PERTURB_SHIFT = 5;

    private Object[] keys = new Object[MINSIZE];
    private long[] hashes = new long[MINSIZE];
    private int mask = MINSIZE - 1;
    private int fill, used, finger;
    private boolean frozen;
    private long frozenHash = -1;

    /** {@code set()}. */
    public PySet() {
    }

    // ------------------------------------------------------------------------------------------ exact table layout
    // (Restore a set dumped from CPython with its exact table, so that future iteration, pop() and insertion order
    // continue exactly as in CPython.)

    /** One active table slot: index in the table, the key, and the hash CPython stored with it. */
    public record Slot(int slot, Object key, long hash) {
    }

    /**
     * The complete CPython table state: {@code tableSize = mask + 1}, {@code fill} (active + dummy slots),
     * {@code used} (active slots), the {@code pop()} finger, the active slots in slot order (= iteration order), and
     * the indices of dummy (deleted) slots. Every other slot is empty.
     */
    public record Layout(int tableSize, int fill, int used, int finger, List<Slot> slots, int[] dummies) {
    }

    /** This set's exact table (see {@link Layout}). */
    public Layout layout() {
        List<Slot> slots = new ArrayList<>(used);
        int nd = 0;
        for (int i = 0; i <= mask; i++) {
            Object k = keys[i];
            if (k == DUMMY) nd++;
            else if (k != null) slots.add(new Slot(i, k, hashes[i]));
        }
        int[] dummies = new int[nd];
        nd = 0;
        for (int i = 0; i <= mask; i++) if (keys[i] == DUMMY) dummies[nd++] = i;
        return new Layout(mask + 1, fill, used, finger, slots, dummies);
    }

    /**
     * Rebuilds a set from a dumped CPython table. Checks that the layout is self-consistent (power-of-two size of at
     * least 8, counts, every key reachable by its probe sequence, and the stored hash equal to {@link PyHash#of}
     * for every key whose type PyHash supports); throws {@link IllegalArgumentException} otherwise.
     */
    public static <K> PySet<K> fromLayout(Layout l) {
        int size = l.tableSize();
        if (size < MINSIZE || Integer.bitCount(size) != 1) throw new IllegalArgumentException("table size " + size);
        if (l.used() != l.slots().size() || l.fill() != l.used() + l.dummies().length)
            throw new IllegalArgumentException("inconsistent counts: used " + l.used() + " fill " + l.fill() + " slots " + l.slots().size() + " dummies " + l.dummies().length);
        PySet<K> s = new PySet<>();
        s.keys = new Object[size];
        s.hashes = new long[size];
        s.mask = size - 1;
        for (int d : l.dummies()) {
            s.keys[d] = DUMMY;
            s.hashes[d] = -1;
        }
        for (Slot e : l.slots()) {
            if (e.key() == null) throw new IllegalArgumentException("null key in slot " + e.slot());
            if (s.keys[e.slot()] != null) throw new IllegalArgumentException("slot used twice: " + e.slot());
            s.keys[e.slot()] = e.key();
            s.hashes[e.slot()] = e.hash();
        }
        s.fill = l.fill();
        s.used = l.used();
        s.finger = l.finger();
        for (Slot e : l.slots()) {
            long h;
            try {
                h = PyHash.of(e.key());
            } catch (IllegalArgumentException unsupported) {
                h = e.hash();
            }
            if (h != e.hash()) throw new IllegalArgumentException("stored hash " + e.hash() + " != hash(" + e.key() + ") = " + h);
            if (s.lookup(e.key(), e.hash()) != e.slot()) throw new IllegalArgumentException("key " + e.key() + " not reachable at slot " + e.slot());
        }
        return s;
    }

    /** Marks this set as a Python {@code frozenset} (mutators then throw); {@code cachedHash} is CPython's cached hash field (-1 if never hashed). */
    public PySet<K> freeze(long cachedHash) {
        frozen = true;
        frozenHash = cachedHash;
        return this;
    }

    /** True for a {@code frozenset}. */
    public boolean isFrozen() {
        return frozen;
    }

    /** The frozenset's cached hash field as dumped (-1 = not computed yet); meaningless for a set. */
    public long frozenHash() {
        return frozenHash;
    }

    private void checkMutable() {
        if (frozen) throw new UnsupportedOperationException("'frozenset' object is immutable");
    }

    /** {@code set(iterable)} for a non-set, non-dict iterable: adds in iteration order. */
    public static <K> PySet<K> of(Iterable<? extends K> items) {
        PySet<K> s = new PySet<>();
        s.update(items);
        return s;
    }

    /** {@code {a, b, c}} / {@code set([a, b, c])}. */
    @SafeVarargs
    public static <K> PySet<K> of(K... items) {
        PySet<K> s = new PySet<>();
        for (K k : items) s.add(k);
        return s;
    }

    /**
     * {@code set(d)} for a dict {@code d}: pre-sized, then the keys in dict order. Pass an insertion-ordered
     * map ({@link java.util.LinkedHashMap}) that mirrors the Python dict.
     */
    public static <K> PySet<K> fromDictKeys(Map<? extends K, ?> dict) {
        PySet<K> s = new PySet<>();
        s.updateFromDictKeys(dict);
        return s;
    }

    // ------------------------------------------------------------------------------------------ table core

    private static int probeIndex(long h, int mask) {
        return (int) (h & mask);
    }

    private void insertClean(Object[] ks, long[] hs, int msk, Object key, long h) {
        long perturb = h;
        int i = probeIndex(h, msk);
        while (true) {
            if (ks[i] == null) {
                ks[i] = key;
                hs[i] = h;
                return;
            }
            if (i + LINEAR_PROBES <= msk) {
                for (int j = 1; j <= LINEAR_PROBES; j++) {
                    if (ks[i + j] == null) {
                        ks[i + j] = key;
                        hs[i + j] = h;
                        return;
                    }
                }
            }
            perturb >>>= PERTURB_SHIFT;
            i = (int) ((i * 5L + 1 + perturb) & msk);
        }
    }

    private void resize(long minused) {
        long newsize = MINSIZE;
        while (newsize <= minused) newsize <<= 1;
        if (newsize > (1 << 30)) throw new IllegalStateException("set too large");
        Object[] oldK = keys;
        long[] oldH = hashes;
        keys = new Object[(int) newsize];
        hashes = new long[(int) newsize];
        mask = (int) newsize - 1;
        fill = used;
        for (int i = 0; i < oldK.length; i++) {
            Object k = oldK[i];
            if (k != null && k != DUMMY) insertClean(keys, hashes, mask, k, oldH[i]);
        }
    }

    /** {@code set_add_entry}. Returns true if the key was new. */
    private boolean addEntry(Object key, long h) {
        int msk = mask;
        int i = probeIndex(h, msk);
        long perturb = h;
        int freeslot = -1;
        while (true) {
            int probes = (i + LINEAR_PROBES <= msk) ? LINEAR_PROBES : 0;
            int j = i;
            while (true) {
                Object e = keys[j];
                if (e == null) {
                    if (freeslot >= 0) {
                        used++;
                        keys[freeslot] = key;
                        hashes[freeslot] = h;
                        return true;
                    }
                    keys[j] = key;
                    hashes[j] = h;
                    fill++;
                    used++;
                    if ((long) fill * 5 >= (long) msk * 3) resize(used > 50000 ? used * 2L : used * 4L);
                    return true;
                }
                if (e == DUMMY) {
                    freeslot = j; // the LAST dummy on the probe path wins (CPython 3.13)
                } else if (hashes[j] == h && PyHash.eq(e, key)) {
                    return false;
                }
                if (probes == 0) break;
                probes--;
                j++;
            }
            perturb >>>= PERTURB_SHIFT;
            i = (int) ((i * 5L + 1 + perturb) & msk);
        }
    }

    /** {@code set_lookkey}: slot index of the key, or -1. */
    private int lookup(Object key, long h) {
        int msk = mask;
        int i = probeIndex(h, msk);
        long perturb = h;
        while (true) {
            int probes = (i + LINEAR_PROBES <= msk) ? LINEAR_PROBES : 0;
            int j = i;
            while (true) {
                Object e = keys[j];
                if (e == null) return -1;
                if (e != DUMMY && hashes[j] == h && PyHash.eq(e, key)) return j;
                if (probes == 0) break;
                probes--;
                j++;
            }
            perturb >>>= PERTURB_SHIFT;
            i = (int) ((i * 5L + 1 + perturb) & msk);
        }
    }

    private void makeDummy(int slot) {
        keys[slot] = DUMMY;
        used--;
    }

    // ------------------------------------------------------------------------------------------ basic API

    /** {@code len(s)}. */
    public int size() {
        return used;
    }

    public boolean isEmpty() {
        return used == 0;
    }

    /** {@code key in s}. */
    public boolean contains(Object key) {
        return lookup(key, PyHash.of(key)) >= 0;
    }

    /** {@code s.add(key)}. Returns true if the key was not present. */
    public boolean add(K key) {
        if (key == null) throw new NullPointerException("None is not supported as a set key");
        checkMutable();
        return addEntry(key, PyHash.of(key));
    }

    /** {@code s.discard(key)}. Returns true if the key was present. */
    public boolean discard(Object key) {
        checkMutable();
        int j = lookup(key, PyHash.of(key));
        if (j < 0) return false;
        makeDummy(j);
        return true;
    }

    /** {@code s.remove(key)}: like discard but throws (KeyError) if absent. */
    public void remove(Object key) {
        if (!discard(key)) throw new NoSuchElementException("KeyError: " + key);
    }

    /** {@code s.pop()}: removes the first active slot at or after the internal finger. */
    @SuppressWarnings("unchecked")
    public K pop() {
        checkMutable();
        if (used == 0) throw new NoSuchElementException("pop from an empty set");
        int i = finger & mask;
        while (keys[i] == null || keys[i] == DUMMY) {
            i++;
            if (i > mask) i = 0;
        }
        K key = (K) keys[i];
        makeDummy(i);
        finger = i + 1;
        return key;
    }

    /** {@code s.clear()}: a fresh 8-slot table; the pop finger is kept (as in CPython). */
    public void clear() {
        checkMutable();
        keys = new Object[MINSIZE];
        hashes = new long[MINSIZE];
        mask = MINSIZE - 1;
        fill = used = 0;
    }

    /** {@code list(s)}: the elements in iteration order. */
    @SuppressWarnings("unchecked")
    public List<K> toList() {
        List<K> out = new ArrayList<>(used);
        for (Object k : keys) if (k != null && k != DUMMY) out.add((K) k);
        return out;
    }

    /** Iterates active slots in slot order (CPython iteration order). Fails fast on size changes. */
    @Override
    public Iterator<K> iterator() {
        return new Iterator<>() {
            final Object[] ks = keys;
            final int expectedUsed = used;
            int pos = 0, seen = 0;

            @Override
            public boolean hasNext() {
                check();
                return seen < expectedUsed;
            }

            @SuppressWarnings("unchecked")
            @Override
            public K next() {
                check();
                while (pos < ks.length) {
                    Object k = ks[pos++];
                    if (k != null && k != DUMMY) {
                        seen++;
                        return (K) k;
                    }
                }
                throw new NoSuchElementException();
            }

            private void check() {
                if (used != expectedUsed || keys != ks) throw new ConcurrentModificationException("Set changed size during iteration");
            }
        };
    }

    @Override
    public void forEach(Consumer<? super K> action) {
        for (K k : this) action.accept(k);
    }

    // ------------------------------------------------------------------------------------------ bulk construction

    /** {@code s.update(iterable)} for a non-set, non-dict iterable: sequential adds. A PySet argument merges. */
    @SuppressWarnings("unchecked")
    public void update(Iterable<? extends K> items) {
        checkMutable();
        if (items instanceof PySet<?> ps) {
            merge((PySet<K>) ps);
            return;
        }
        for (K k : items) add(k);
    }

    /** {@code s.update(t)} / {@code s |= t} for a set {@code t} ({@code set_merge}). */
    public void update(PySet<? extends K> other) {
        checkMutable();
        merge(other);
    }

    /** {@code s.update(d)} for a dict: pre-resize if needed, then add the keys in dict order. */
    public void updateFromDictKeys(Map<? extends K, ?> dict) {
        checkMutable();
        int n = dict.size();
        if ((long) (fill + n) * 5 >= (long) mask * 3) resize((long) (used + n) * 2);
        for (K k : dict.keySet()) add(k);
    }

    private void merge(PySet<? extends K> other) {
        if (other == this || other.used == 0) return;
        if ((long) (fill + other.used) * 5 >= (long) mask * 3) resize((long) (used + other.used) * 2);
        if (fill == 0 && mask == other.mask && other.fill == other.used) {
            for (int i = 0; i <= other.mask; i++) {
                Object k = other.keys[i];
                if (k != null) {
                    keys[i] = k;
                    hashes[i] = other.hashes[i];
                }
            }
            fill = other.fill;
            used = other.used;
            return;
        }
        if (fill == 0) {
            fill = other.used;
            used = other.used;
            for (int i = 0; i <= other.mask; i++) {
                Object k = other.keys[i];
                if (k != null && k != DUMMY) insertClean(keys, hashes, mask, k, other.hashes[i]);
            }
            return;
        }
        Object[] oks = other.keys;
        long[] ohs = other.hashes;
        for (int i = 0; i < oks.length; i++) {
            Object k = oks[i];
            if (k != null && k != DUMMY) addEntry(k, ohs[i]);
        }
    }

    /** {@code set(s)} / {@code s.copy()} / {@code frozenset(s)} ({@code set_merge} into an empty set). */
    public PySet<K> copy() {
        PySet<K> r = new PySet<>();
        r.merge(this);
        return r;
    }

    // ------------------------------------------------------------------------------------------ set algebra

    /** {@code s | t}: copy of s, then merge t. */
    public PySet<K> or(PySet<? extends K> other) {
        PySet<K> r = copy();
        if (other != this) r.merge(other);
        return r;
    }

    /** {@code s |= t}. */
    public PySet<K> ior(PySet<? extends K> other) {
        checkMutable();
        merge(other);
        return this;
    }

    /** {@code s & t} ({@code set_intersection}): iterates the smaller set (the right operand on equal sizes). */
    @SuppressWarnings("unchecked")
    public PySet<K> and(PySet<?> other) {
        if (other == this) return copy();
        PySet<K> r = new PySet<>();
        PySet<?> big = this, small = other;
        if (other.used > used) {
            big = other;
            small = this;
        }
        Object[] ks = small.keys;
        long[] hs = small.hashes;
        for (int i = 0; i < ks.length; i++) {
            Object k = ks[i];
            if (k != null && k != DUMMY && big.lookup(k, hs[i]) >= 0) r.addEntry(k, hs[i]);
        }
        return r;
    }

    /** {@code s.intersection(iterable)} for a non-set iterable. */
    public PySet<K> intersection(Iterable<?> other) {
        if (other instanceof PySet<?> ps) return and(ps);
        PySet<K> r = new PySet<>();
        for (Object k : other) {
            long h = PyHash.of(k);
            if (lookup(k, h) >= 0) r.addEntry(k, h);
        }
        return r;
    }

    /** {@code s &= t}: computes {@code s & t} and takes over its table (the pop finger is kept). */
    public PySet<K> iand(PySet<?> other) {
        checkMutable();
        PySet<K> r = and(other);
        keys = r.keys;
        hashes = r.hashes;
        mask = r.mask;
        fill = r.fill;
        used = r.used;
        return this;
    }

    /** {@code s - t} ({@code set_difference}). */
    public PySet<K> minus(PySet<?> other) {
        if ((used >> 2) > other.used) {
            PySet<K> r = copy();
            r.differenceUpdate(other);
            return r;
        }
        PySet<K> r = new PySet<>();
        for (int i = 0; i < keys.length; i++) {
            Object k = keys[i];
            if (k != null && k != DUMMY && other.lookup(k, hashes[i]) < 0) r.addEntry(k, hashes[i]);
        }
        return r;
    }

    /** {@code s.difference(iterable)} for a non-set iterable: copy, then discard each item. */
    public PySet<K> difference(Iterable<?> other) {
        if (other instanceof PySet<?> ps) return minus(ps);
        PySet<K> r = copy();
        r.differenceUpdate(other);
        return r;
    }

    /** {@code s -= t} / {@code s.difference_update(t)} for a set t. */
    public PySet<K> differenceUpdate(PySet<?> other) {
        checkMutable();
        if (other == this) {
            clear();
            return this;
        }
        PySet<?> o = other;
        if ((other.used >> 3) > used) o = and(other);
        Object[] ks = o.keys;
        long[] hs = o.hashes;
        for (int i = 0; i < ks.length; i++) {
            Object k = ks[i];
            if (k != null && k != DUMMY) {
                int j = lookup(k, hs[i]);
                if (j >= 0) makeDummy(j);
            }
        }
        shrinkAfterDiscards();
        return this;
    }

    /** {@code s.difference_update(iterable)} for a non-set iterable. */
    public PySet<K> differenceUpdate(Iterable<?> other) {
        if (other instanceof PySet<?> ps) return differenceUpdate(ps);
        for (Object k : other) discard(k);
        shrinkAfterDiscards();
        return this;
    }

    private void shrinkAfterDiscards() {
        if ((fill - used) <= mask / 4) return;
        resize(used > 50000 ? used * 2L : used * 4L);
    }

    /** {@code s ^ t}: a copy of t, then symmetric-difference-update with s. */
    @SuppressWarnings("unchecked")
    public PySet<K> xor(PySet<? extends K> other) {
        PySet<K> r = ((PySet<K>) other).copy();
        r.symmetricDifferenceUpdate(this);
        return r;
    }

    /** {@code s ^= t} / {@code s.symmetric_difference_update(t)} for a set t. */
    public PySet<K> symmetricDifferenceUpdate(PySet<? extends K> other) {
        checkMutable();
        if (other == this) {
            clear();
            return this;
        }
        Object[] ks = other.keys.clone();
        long[] hs = other.hashes.clone();
        for (int i = 0; i < ks.length; i++) {
            Object k = ks[i];
            if (k != null && k != DUMMY) {
                int j = lookup(k, hs[i]);
                if (j >= 0) makeDummy(j);
                else addEntry(k, hs[i]);
            }
        }
        return this;
    }

    /** Set equality (order-independent, like Python {@code ==}). */
    public boolean sameElements(PySet<?> other) {
        if (other.used != used) return false;
        for (int i = 0; i < keys.length; i++) {
            Object k = keys[i];
            if (k != null && k != DUMMY && other.lookup(k, hashes[i]) < 0) return false;
        }
        return true;
    }

    /** Debug view: table size (mask + 1). */
    public int tableSize() {
        return mask + 1;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (K k : this) {
            if (!first) sb.append(", ");
            sb.append(k);
            first = false;
        }
        return sb.append(used == 0 ? "set()" : "}").toString().replace("{set()", "set()");
    }
}
