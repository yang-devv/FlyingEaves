package com.flyingeaves.gen.py;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * CPython 3.13 {@code random.Random}, bit for bit: the MT19937 core of {@code Modules/_randommodule.c}
 * and the derived methods of {@code Lib/random.py}.
 *
 * <h2>The global stream</h2>
 * A Python program that uses the module-level stream ({@code random.seed(seed)}, then {@code random.choice} ...)
 * is ported by creating exactly one instance for that stream and handing the same object to every piece of code
 * that calls the {@code random} module. Private streams ({@code random.Random(G.getrandbits(64))}) are separate instances made
 * with {@link #deriveStream()} (or {@link #ofUnsigned64}: a {@code getrandbits(64)} result is an <em>unsigned</em>
 * Python int, so never pass it to {@link #ofInt(long)}).
 *
 * <pre>{@code
 * PyRandom g = PyRandom.ofInt(seed);            // random.seed(seed)
 * PyRandom s = g.deriveStream();                // random.Random(random.getrandbits(64))
 * }</pre>
 *
 * <h2>Draw accounting</h2>
 * Every method consumes 32-bit MT words exactly like CPython. {@link #words()} counts them, which is handy
 * when bisecting a divergence ("after N words the streams differ").
 *
 * <h2>What is exact</h2>
 * Everything except {@link #gauss} and {@link #normalvariate}, which call the C runtime's {@code log}/{@code sin}/
 * {@code cos} in CPython; here they use {@link StrictMath}. The generator never calls them.
 *
 * <p>Not thread-safe (neither is CPython's).
 */
public final class PyRandom {
    private static final int N = 624, M = 397;
    private static final int MATRIX_A = 0x9908b0df;
    private static final int UPPER_MASK = 0x80000000, LOWER_MASK = 0x7fffffff;

    private final int[] mt = new int[N];
    private int index = N + 1;
    private long words;
    /** CPython's {@code gauss_next}; {@code Double.NaN} stands for {@code None}. */
    private double gaussNext = Double.NaN;

    private PyRandom() {
    }

    /** {@code random.Random(n)} for a Python int that fits a long (negative n seeds like {@code abs(n)}). */
    public static PyRandom ofInt(long n) {
        PyRandom r = new PyRandom();
        r.seed(n);
        return r;
    }

    /** {@code random.Random(n)} for an arbitrary-size Python int. */
    public static PyRandom ofInt(BigInteger n) {
        PyRandom r = new PyRandom();
        r.seed(n);
        return r;
    }

    /**
     * {@code random.Random(s)} for a str (seed version 2, as CPython 3.x): the key is the int
     * {@code int.from_bytes(s.encode() + sha512(s.encode()).digest(), 'big')}. (Addition for Flying Eaves: the
     * generator seeds every stream with strings such as {@code f"compose/{seed}/{look}/{rs}/0"}.)
     */
    public static PyRandom ofString(String s) {
        byte[] a = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] d;
        try {
            d = java.security.MessageDigest.getInstance("SHA-512").digest(a);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        byte[] all = new byte[a.length + d.length];
        System.arraycopy(a, 0, all, 0, a.length);
        System.arraycopy(d, 0, all, a.length, d.length);
        return ofInt(new BigInteger(1, all));
    }

    /**
     * {@code random.Random(x)} where {@code x} is the <em>unsigned</em> 64-bit value held in a long, e.g. a
     * {@code getrandbits(64)} result or {@code secrets.randbits(64)}. A value below 2^32 gives a 1-word key, as in
     * CPython.
     */
    public static PyRandom ofUnsigned64(long unsignedSeed) {
        PyRandom r = new PyRandom();
        r.seedUnsigned64(unsignedSeed);
        return r;
    }

    /**
     * {@code random.Random(self.getrandbits(64))}: draws 2 words from this stream and seeds a new, independent
     * stream with them. This is how every private stream of the generator is made ({@code BlockProcessor},
     * {@code Lore}, the segmenter stream, maze/crypt/construction streams).
     */
    public PyRandom deriveStream() {
        return ofUnsigned64(getrandbits(64));
    }

    private PyRandom(PyRandom o) {
        System.arraycopy(o.mt, 0, mt, 0, N);
        index = o.index;
        words = o.words;
        gaussNext = o.gaussNext;
    }

    /** Independent copy of the whole state (debugging aid, like {@code getstate}/{@code setstate}). */
    public PyRandom copy() {
        return new PyRandom(this);
    }

    /** Number of 32-bit MT outputs consumed since construction / the last seed / {@link #fromState}. */
    public long words() {
        return words;
    }

    /**
     * CPython's {@code Random.getstate()}: {@code (3, (mt[0], ..., mt[623], index), gauss_next)}. {@code mt} holds
     * the 624 words as unsigned 32-bit values in longs (as Python prints them); {@code index} is 0..624 (624 = the
     * next draw regenerates the table); {@code gaussNext} is null for Python's {@code None}.
     */
    public record State(long[] mt, int index, Double gaussNext) {
        public State {
            if (mt.length != N) throw new IllegalArgumentException("MT state needs " + N + " words, got " + mt.length);
            if (index < 0 || index > N) throw new IllegalArgumentException("MT index out of range: " + index);
            for (long w : mt) if (w < 0 || w > 0xFFFFFFFFL) throw new IllegalArgumentException("MT word out of range: " + w);
        }
    }

    /** {@code getstate()}. */
    public State getState() {
        long[] m = new long[N];
        for (int i = 0; i < N; i++) m[i] = mt[i] & 0xFFFFFFFFL;
        return new State(m, Math.min(index, N), Double.isNaN(gaussNext) ? null : gaussNext);
    }

    /** {@code r = Random(); r.setstate(state)}. The {@link #words()} counter starts at 0. */
    public static PyRandom fromState(State s) {
        PyRandom r = new PyRandom();
        for (int i = 0; i < N; i++) r.mt[i] = (int) s.mt()[i];
        r.index = s.index();
        r.words = 0;
        r.gaussNext = s.gaussNext() == null ? Double.NaN : s.gaussNext();
        return r;
    }

    // ------------------------------------------------------------------------------------------ seeding

    /**
     * {@code random.seed(n)} for an int: the key is the 32-bit words of {@code abs(n)}, least significant first
     * ({@code [0]} for 0). <b>Note:</b> a negative long is the negative Python int, so {@code seed(-5) == seed(5)};
     * use {@link #seedUnsigned64} for a 64-bit value with the top bit set.
     */
    public void seed(long n) {
        seed(BigInteger.valueOf(n));
    }

    /** {@code random.seed(n)} for an arbitrary int. */
    public void seed(BigInteger n) {
        BigInteger a = n.abs();
        int bits = a.bitLength();
        int keyLen = Math.max(1, (bits + 31) / 32);
        int[] key = new int[keyLen];
        for (int i = 0; i < keyLen; i++) key[i] = a.shiftRight(32 * i).intValue();
        initByArray(key);
        index = N;
        words = 0;
        gaussNext = Double.NaN;
    }

    /** {@code random.seed(x)} where x is the unsigned 64-bit value of {@code unsignedSeed}. */
    public void seedUnsigned64(long unsignedSeed) {
        int lo = (int) unsignedSeed, hi = (int) (unsignedSeed >>> 32);
        initByArray(hi == 0 ? new int[]{lo} : new int[]{lo, hi});
        index = N;
        words = 0;
        gaussNext = Double.NaN;
    }

    private void initGenrand(int s) {
        mt[0] = s;
        for (int i = 1; i < N; i++) mt[i] = 1812433253 * (mt[i - 1] ^ (mt[i - 1] >>> 30)) + i;
        index = N;
    }

    private void initByArray(int[] key) {
        initGenrand(19650218);
        int i = 1, j = 0;
        int n = key.length;
        for (int k = Math.max(N, n); k > 0; k--) {
            mt[i] = (mt[i] ^ ((mt[i - 1] ^ (mt[i - 1] >>> 30)) * 1664525)) + key[j] + j;
            i++;
            j++;
            if (i >= N) {
                mt[0] = mt[N - 1];
                i = 1;
            }
            if (j >= n) j = 0;
        }
        for (int k = N - 1; k > 0; k--) {
            mt[i] = (mt[i] ^ ((mt[i - 1] ^ (mt[i - 1] >>> 30)) * 1566083941)) - i;
            i++;
            if (i >= N) {
                mt[0] = mt[N - 1];
                i = 1;
            }
        }
        mt[0] = 0x80000000;
    }

    // ------------------------------------------------------------------------------------------ core

    /** {@code genrand_uint32}: the next tempered 32-bit word (use {@code & 0xFFFFFFFFL} for its unsigned value). */
    public int nextU32() {
        if (index >= N) {
            int kk;
            int y;
            for (kk = 0; kk < N - M; kk++) {
                y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                mt[kk] = mt[kk + M] ^ (y >>> 1) ^ ((y & 1) != 0 ? MATRIX_A : 0);
            }
            for (; kk < N - 1; kk++) {
                y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
                mt[kk] = mt[kk + (M - N)] ^ (y >>> 1) ^ ((y & 1) != 0 ? MATRIX_A : 0);
            }
            y = (mt[N - 1] & UPPER_MASK) | (mt[0] & LOWER_MASK);
            mt[N - 1] = mt[M - 1] ^ (y >>> 1) ^ ((y & 1) != 0 ? MATRIX_A : 0);
            index = 0;
        }
        int y = mt[index++];
        y ^= (y >>> 11);
        y ^= (y << 7) & 0x9d2c5680;
        y ^= (y << 15) & 0xefc60000;
        y ^= (y >>> 18);
        words++;
        return y;
    }

    /** {@code random()}: two words, {@code (a*2^26 + b) / 2^53}. */
    public double random() {
        long a = (nextU32() & 0xFFFFFFFFL) >>> 5;
        long b = (nextU32() & 0xFFFFFFFFL) >>> 6;
        return (a * 67108864.0 + b) * (1.0 / 9007199254740992.0);
    }

    /**
     * {@code getrandbits(k)} for {@code 0 <= k <= 64}. The result is the unsigned value; for {@code k == 64} it may
     * be negative as a Java long. {@code k == 0} returns 0 and draws nothing.
     */
    public long getrandbits(int k) {
        if (k < 0) throw new IllegalArgumentException("number of bits must be non-negative");
        if (k == 0) return 0;
        if (k <= 32) return (nextU32() & 0xFFFFFFFFL) >>> (32 - k);
        if (k > 64) throw new IllegalArgumentException("use getrandbitsBig for k > 64");
        long lo = nextU32() & 0xFFFFFFFFL;
        long hi = (nextU32() & 0xFFFFFFFFL) >>> (64 - k);
        return lo | (hi << 32);
    }

    /** {@code getrandbits(k)} for any {@code k >= 0}. */
    public BigInteger getrandbitsBig(int k) {
        if (k < 0) throw new IllegalArgumentException("number of bits must be non-negative");
        if (k == 0) return BigInteger.ZERO;
        if (k <= 32) return BigInteger.valueOf((nextU32() & 0xFFFFFFFFL) >>> (32 - k));
        int nw = (k - 1) / 32 + 1;
        BigInteger r = BigInteger.ZERO;
        int left = k;
        for (int i = 0; i < nw; i++, left -= 32) {
            long w = nextU32() & 0xFFFFFFFFL;
            if (left < 32) w >>>= (32 - left);
            r = r.or(BigInteger.valueOf(w).shiftLeft(32 * i));
        }
        return r;
    }

    // ------------------------------------------------------------------------------------------ integers

    /**
     * {@code _randbelow(n)} for {@code n >= 1}: {@code k = n.bit_length()}, draw {@code getrandbits(k)} until it is
     * below n. Consumes a geometric number of draws; {@code n == 1} still draws ({@code getrandbits(1)} until 0).
     */
    public long randbelow(long n) {
        if (n <= 0) throw new IllegalArgumentException("randbelow needs n > 0, got " + n);
        int k = 64 - Long.numberOfLeadingZeros(n);
        long r = getrandbits(k);
        while (r >= n) r = getrandbits(k);
        return r;
    }

    /** {@code _randbelow(n)} for an arbitrary-size n. */
    public BigInteger randbelow(BigInteger n) {
        if (n.signum() <= 0) throw new IllegalArgumentException("randbelow needs n > 0");
        int k = n.bitLength();
        BigInteger r = getrandbitsBig(k);
        while (r.compareTo(n) >= 0) r = getrandbitsBig(k);
        return r;
    }

    /** {@code randrange(stop)}. */
    public long randrange(long stop) {
        if (stop > 0) return randbelow(stop);
        throw new IllegalArgumentException("empty range for randrange()");
    }

    /** {@code randrange(start, stop)}. */
    public long randrange(long start, long stop) {
        long width = Math.subtractExact(stop, start);
        if (width > 0) return start + randbelow(width);
        throw new IllegalArgumentException("empty range in randrange(" + start + ", " + stop + ")");
    }

    /** {@code randrange(start, stop, step)}. */
    public long randrange(long start, long stop, long step) {
        long width = Math.subtractExact(stop, start);
        if (step == 1) return randrange(start, stop);
        long n;
        if (step > 0) n = Math.floorDiv(width + step - 1, step);
        else if (step < 0) n = Math.floorDiv(width + step + 1, step);
        else throw new IllegalArgumentException("zero step for randrange()");
        if (n <= 0) throw new IllegalArgumentException("empty range in randrange(" + start + ", " + stop + ", " + step + ")");
        return start + step * randbelow(n);
    }

    /** {@code randint(a, b)} = {@code randrange(a, b + 1)}; {@code randint(a, a)} still draws. */
    public long randint(long a, long b) {
        return randrange(a, b + 1);
    }

    /** {@code randint(a, b)} as an int (convenience). */
    public int randint(int a, int b) {
        return (int) randrange(a, (long) b + 1);
    }

    // ------------------------------------------------------------------------------------------ sequences

    /** {@code _randbelow(len(seq))}, i.e. the index {@code choice} would pick. Throws on an empty sequence. */
    public int choiceIndex(int len) {
        if (len == 0) throw new IndexOutOfBoundsException("Cannot choose from an empty sequence");
        return (int) randbelow(len);
    }

    /** {@code choice(seq)}. A one-element list still draws ({@code getrandbits(1)} until it is 0). */
    public <T> T choice(List<T> seq) {
        return seq.get(choiceIndex(seq.size()));
    }

    /** {@code choice(seq)} for an array. */
    @SafeVarargs
    public final <T> T choice(T... seq) {
        return seq[choiceIndex(seq.length)];
    }

    /** {@code choice(range(n))} / {@code choice} over an int array. */
    public int choice(int[] seq) {
        return seq[choiceIndex(seq.length)];
    }

    /** {@code choices(population, k=k)} without weights: {@code population[floor(random() * n)]} each (2 words). */
    public <T> List<T> choices(List<T> population, int k) {
        double n = population.size();
        List<T> out = new ArrayList<>(k);
        for (int i = 0; i < k; i++) out.add(population.get((int) Math.floor(random() * n)));
        return out;
    }

    /**
     * {@code choices(range(len(weights)), weights=weights, k=k)}, returning indices. The cumulative weights are a
     * plain left fold ({@code itertools.accumulate}); {@code total = cum[-1] + 0.0}; each pick is
     * {@code bisect_right(cum, random() * total, 0, n - 1)}.
     */
    public int[] choicesIndex(double[] weights, int k) {
        double[] cum = new double[weights.length];
        double acc = 0;
        for (int i = 0; i < weights.length; i++) {
            acc = i == 0 ? weights[0] : acc + weights[i];
            cum[i] = acc;
        }
        return choicesIndexCum(cum, k);
    }

    /** As {@link #choicesIndex(double[], int)} with {@code cum_weights} given directly. */
    public int[] choicesIndexCum(double[] cumWeights, int k) {
        int n = cumWeights.length;
        if (n == 0) throw new IndexOutOfBoundsException("population is empty");
        double total = cumWeights[n - 1] + 0.0;
        if (total <= 0.0) throw new IllegalArgumentException("Total of weights must be greater than zero");
        if (!Double.isFinite(total)) throw new IllegalArgumentException("Total of weights must be finite");
        int hi = n - 1;
        int[] out = new int[k];
        for (int i = 0; i < k; i++) out[i] = Py.bisectRight(cumWeights, random() * total, 0, hi);
        return out;
    }

    /**
     * {@code choices} with integer weights (Python ints): the cumulative sums are exact integers and the
     * comparison {@code float < int} in {@code bisect} is exact. Valid while the sums stay below 2^53.
     */
    public int[] choicesIndex(long[] weights, int k) {
        double[] cum = new double[weights.length];
        long acc = 0;
        for (int i = 0; i < weights.length; i++) {
            acc += weights[i];
            if (Math.abs(acc) > (1L << 53)) throw new ArithmeticException("integer weights too large for exact emulation");
            cum[i] = acc;
        }
        return choicesIndexCum(cum, k);
    }

    /** {@code choices(population, weights=weights, k=k)}. */
    public <T> List<T> choices(List<T> population, double[] weights, int k) {
        if (weights.length != population.size()) throw new IllegalArgumentException("The number of weights does not match the population");
        List<T> out = new ArrayList<>(k);
        for (int i : choicesIndex(weights, k)) out.add(population.get(i));
        return out;
    }

    /** {@code shuffle(x)}: {@code for i in reversed(range(1, len(x))): j = _randbelow(i+1); swap}. */
    public <T> void shuffle(List<T> x) {
        for (int i = x.size() - 1; i >= 1; i--) {
            int j = (int) randbelow(i + 1);
            T t = x.get(i);
            x.set(i, x.get(j));
            x.set(j, t);
        }
    }

    /** {@code shuffle} of an int array. */
    public void shuffle(int[] x) {
        for (int i = x.length - 1; i >= 1; i--) {
            int j = (int) randbelow(i + 1);
            int t = x[i];
            x[i] = x[j];
            x[j] = t;
        }
    }

    /**
     * Indices picked by {@code sample(population, k)} for a population of size {@code n}
     * ({@code sample(range(n), k)} returns exactly these). Two branches as in CPython: a pool branch when
     * {@code n <= setsize} and a rejection ("set") branch otherwise, where
     * {@code setsize = 21 + (k > 5 ? 4 ** ceil(log(3k, 4)) : 0)}.
     */
    public int[] sampleIndices(int n, int k) {
        if (k < 0 || k > n) throw new IllegalArgumentException("Sample larger than population or is negative");
        int[] result = new int[k];
        long setsize = 21;
        if (k > 5) setsize += pow4(ceilLog4(k * 3L));
        if (n <= setsize) {
            int[] pool = new int[n];
            for (int i = 0; i < n; i++) pool[i] = i;
            for (int i = 0; i < k; i++) {
                int j = (int) randbelow(n - i);
                result[i] = pool[j];
                pool[j] = pool[n - i - 1];
            }
        } else {
            java.util.HashSet<Integer> selected = new java.util.HashSet<>();
            for (int i = 0; i < k; i++) {
                int j = (int) randbelow(n);
                while (selected.contains(j)) j = (int) randbelow(n);
                selected.add(j);
                result[i] = j;
            }
        }
        return result;
    }

    /** {@code sample(population, k)} for a list population. */
    public <T> List<T> sample(List<T> population, int k) {
        int[] idx = sampleIndices(population.size(), k);
        List<T> out = new ArrayList<>(k);
        for (int i : idx) out.add(population.get(i));
        return out;
    }

    /**
     * {@code ceil(math.log(x, 4))} for an integer {@code x = 3k}. CPython computes {@code log(x)/log(4)} with the C
     * runtime; 3k is never a power of 4, and log4(3k) stays at least ~1e-12 away from an integer for any k a
     * generator can use, so this exact integer computation always agrees.
     */
    static int ceilLog4(long x) {
        int e = 0;
        long p = 1;
        while (p < x) {
            p *= 4;
            e++;
        }
        return e;
    }

    private static long pow4(int e) {
        return 1L << (2 * e);
    }

    // ------------------------------------------------------------------------------------------ real distributions

    /** {@code uniform(a, b)} = {@code a + (b - a) * random()}. */
    public double uniform(double a, double b) {
        return a + (b - a) * random();
    }

    /** {@code normalvariate(mu, sigma)} (Kinderman-Monahan). Uses StrictMath.log: not guaranteed bit-exact. */
    public double normalvariate(double mu, double sigma) {
        double z;
        while (true) {
            double u1 = random();
            double u2 = 1.0 - random();
            z = NV_MAGICCONST * (u1 - 0.5) / u2;
            double zz = z * z / 4.0;
            if (zz <= -StrictMath.log(u2)) break;
        }
        return mu + z * sigma;
    }

    /** {@code gauss(mu, sigma)} (Box-Muller with the cached second value). Not guaranteed bit-exact (libm). */
    public double gauss(double mu, double sigma) {
        double z = gaussNext;
        gaussNext = Double.NaN;
        if (Double.isNaN(z)) {
            double x2pi = random() * TWOPI;
            double g2rad = Math.sqrt(-2.0 * StrictMath.log(1.0 - random()));
            z = StrictMath.cos(x2pi) * g2rad;
            gaussNext = StrictMath.sin(x2pi) * g2rad;
        }
        return mu + z * sigma;
    }

    private static final double TWOPI = 2.0 * Math.PI;
    /** {@code 4 * exp(-0.5) / sqrt(2.0)} evaluated with the C runtime's exp (see {@link UcrtMath#exp}). */
    private static final double NV_MAGICCONST = 4 * UcrtMath.exp(-0.5) / Math.sqrt(2.0);
}
