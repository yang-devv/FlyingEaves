package com.flyingeaves.gen.py;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Python language-level semantics that differ from Java's: rounding, integer division, the 3.12+ compensated
 * {@code sum()}, {@code bisect}, first-extreme {@code min}/{@code max}, and the exact text forms Python emits
 * ({@code repr(str)}, {@code repr(float)}, {@code json.dumps}).
 *
 * <p>Float arithmetic itself needs no helper: Java {@code double}/{@code float} operations are IEEE binary64/32 with
 * round-to-nearest-even, exactly like CPython and numpy, as long as the port evaluates in the same order and never
 * uses {@link Math#fma}. For {@code x ** y} on floats use {@link UcrtMath#floatPow} (the C runtime {@code pow} is
 * <em>not</em> {@code x*x}), for {@code math.sqrt} use {@link Math#sqrt}.
 */
public final class Py {
    private Py() {
    }

    // ------------------------------------------------------------------------------------------ rounding

    /** {@code round(x)} for a float: round half to even, as an int. NaN/inf throw (ValueError/OverflowError). */
    public static long round(double x) {
        if (Double.isNaN(x)) throw new ArithmeticException("ValueError: cannot convert float NaN to integer");
        if (Double.isInfinite(x)) throw new ArithmeticException("OverflowError: cannot convert float infinity to integer");
        double r = Math.rint(x);
        if (Math.abs(r) >= 0x1p63) throw new ArithmeticException("round result does not fit a long: " + x);
        return (long) r;
    }

    /** {@code round(x)} as an int (convenience; throws if out of int range). */
    public static int roundInt(double x) {
        return Math.toIntExact(round(x));
    }

    /** {@code int(x)} for a float: truncation toward zero. NaN/inf throw. */
    public static long intOf(double x) {
        if (Double.isNaN(x)) throw new ArithmeticException("ValueError: cannot convert float NaN to integer");
        if (Double.isInfinite(x)) throw new ArithmeticException("OverflowError: cannot convert float infinity to integer");
        if (Math.abs(x) >= 0x1p63) throw new ArithmeticException("int() result does not fit a long: " + x);
        return (long) x;
    }

    /** {@code math.floor(x)} as an int. */
    public static long floor(double x) {
        return intOf(Math.floor(x));
    }

    /** {@code math.ceil(x)} as an int. */
    public static long ceil(double x) {
        return intOf(Math.ceil(x));
    }

    // ------------------------------------------------------------------------------------------ division

    /** {@code a // b} for ints (floor division). */
    public static long floorDiv(long a, long b) {
        if (b == 0) throw new ArithmeticException("ZeroDivisionError: integer division or modulo by zero");
        return Math.floorDiv(a, b);
    }

    /** {@code a % b} for ints (result has the sign of b). */
    public static long floorMod(long a, long b) {
        if (b == 0) throw new ArithmeticException("ZeroDivisionError: integer division or modulo by zero");
        return Math.floorMod(a, b);
    }

    /**
     * {@code a / b} for two Python ints: the correctly rounded quotient of the exact rationals. Equal to
     * {@code (double) a / (double) b} when both fit in 53 bits; larger operands use exact big arithmetic.
     */
    public static double trueDiv(long a, long b) {
        if (b == 0) throw new ArithmeticException("ZeroDivisionError: division by zero");
        if (Math.abs(a) <= (1L << 53) && Math.abs(b) <= (1L << 53) && a != Long.MIN_VALUE && b != Long.MIN_VALUE) {
            return (double) a / (double) b;
        }
        return trueDiv(BigInteger.valueOf(a), BigInteger.valueOf(b));
    }

    /** {@code a / b} for arbitrary Python ints, correctly rounded (half-even). */
    public static double trueDiv(BigInteger a, BigInteger b) {
        if (b.signum() == 0) throw new ArithmeticException("ZeroDivisionError: division by zero");
        // Exact rational -> double with one rounding: scale to get >= 55 significant bits, then round by hand.
        boolean neg = a.signum() * b.signum() < 0;
        a = a.abs();
        b = b.abs();
        if (a.signum() == 0) return neg ? -0.0 : 0.0;
        int shift = 55 - (a.bitLength() - b.bitLength());
        BigInteger num = shift >= 0 ? a.shiftLeft(shift) : a;
        BigInteger den = shift >= 0 ? b : b.shiftLeft(-shift);
        BigInteger[] qr = num.divideAndRemainder(den);
        BigInteger q = qr[0];
        boolean sticky = qr[1].signum() != 0;
        // q has 55 or 56 bits; value = q * 2^-shift
        int extra = q.bitLength() - 53;
        int exp2 = -shift + extra; // value ~ (q >> extra) * 2^exp2
        // subnormal handling: the smallest exponent of a 53-bit integer mantissa is -1074
        if (exp2 < -1074) {
            extra += (-1074 - exp2);
            exp2 = -1074;
        }
        BigInteger mant;
        if (extra > 0) {
            mant = q.shiftRight(extra);
            BigInteger rem = q.subtract(mant.shiftLeft(extra));
            BigInteger half = BigInteger.ONE.shiftLeft(extra - 1);
            int c = rem.compareTo(half);
            if (c > 0 || (c == 0 && (sticky || mant.testBit(0)))) mant = mant.add(BigInteger.ONE);
        } else {
            mant = q.shiftLeft(-extra);
        }
        double v = Math.scalb(mant.doubleValue(), exp2); // mant <= 2^53, exact; scalb exact unless overflow
        return neg ? -v : v;
    }

    /** {@code x // y} for Python floats ({@code float_floor_div}). */
    public static double floorDiv(double vx, double wx) {
        return floatDivmod(vx, wx)[0];
    }

    /** {@code x % y} for Python floats ({@code float_rem}): result has the sign of y. */
    public static double floorMod(double vx, double wx) {
        if (wx == 0.0) throw new ArithmeticException("ZeroDivisionError: float modulo");
        double mod = vx % wx; // C fmod, exact
        if (mod != 0.0) {
            if ((wx < 0) != (mod < 0)) mod += wx;
        } else {
            mod = Math.copySign(0.0, wx);
        }
        return mod;
    }

    /** {@code divmod(x, y)} for Python floats ({@code _float_div_mod}). */
    public static double[] floatDivmod(double vx, double wx) {
        if (wx == 0.0) throw new ArithmeticException("ZeroDivisionError: float divmod()");
        double mod = vx % wx;
        double div = (vx - mod) / wx;
        if (mod != 0.0) {
            if ((wx < 0) != (mod < 0)) {
                mod += wx;
                div -= 1.0;
            }
        } else {
            mod = Math.copySign(0.0, wx);
        }
        double floordiv;
        if (div != 0.0) {
            floordiv = Math.floor(div);
            if (div - floordiv > 0.5) floordiv += 1.0;
        } else {
            floordiv = Math.copySign(0.0, vx / wx);
        }
        return new double[]{floordiv, mod};
    }

    // ------------------------------------------------------------------------------------------ sums

    /**
     * Builtin {@code sum(xs)} where every item is a Python {@code float} (CPython 3.12+): the first item is
     * {@code 0 + x0}, then Neumaier compensated summation; the compensation is added at the end if non-zero and
     * finite. This is <b>not</b> a plain left fold ({@code sum([0.1]*10) == 1.0}).
     */
    public static double sum(double[] xs) {
        return sum(xs, 0, xs.length);
    }

    /** {@link #sum(double[])} over {@code xs[from, to)}. */
    public static double sum(double[] xs, int from, int to) {
        if (from >= to) return 0; // int 0
        double f = 0.0 + xs[from]; // int 0 + float
        double c = 0.0;
        for (int i = from + 1; i < to; i++) {
            double x = xs[i];
            double t = f + x;
            if (Math.abs(f) >= Math.abs(x)) c += (f - t) + x;
            else c += (x - t) + f;
            f = t;
        }
        if (c != 0.0 && Double.isFinite(c)) f += c;
        return f;
    }

    /** Builtin {@code sum(gen)} over Python floats produced by a function of each element. */
    public static <T> double sum(Iterable<T> items, ToDoubleFunction<? super T> f) {
        double acc = 0.0, c = 0.0;
        boolean first = true;
        for (T it : items) {
            double x = f.applyAsDouble(it);
            if (first) {
                acc = 0.0 + x;
                first = false;
                continue;
            }
            double t = acc + x;
            if (Math.abs(acc) >= Math.abs(x)) c += (acc - t) + x;
            else c += (x - t) + acc;
            acc = t;
        }
        if (first) return 0;
        if (c != 0.0 && Double.isFinite(c)) acc += c;
        return acc;
    }

    /** Builtin {@code sum()} of Python ints (exact). */
    public static long sum(long[] xs) {
        long s = 0;
        for (long x : xs) s = Math.addExact(s, x);
        return s;
    }

    /**
     * Builtin {@code sum()} over a mix of Python ints ({@link Integer}, {@link Long}, {@link BigInteger}) and
     * floats ({@link Double}), start 0, exactly as CPython 3.13 on 64-bit Windows: an exact int phase, then the
     * compensated float phase in which ints that fit a 32-bit C {@code long} are added without compensation, and an
     * int outside that range flushes the compensation and drops to a plain left fold for the rest.
     * Returns a {@link Long}/{@link BigInteger} if every item was an int, else a {@link Double}.
     */
    public static Number sumMixed(List<? extends Number> items) {
        int i = 0, n = items.size();
        // phase 1: C fast path for ints (64-bit Py_ssize_t accumulator; an item outside the 32-bit C long range
        // of Windows, or an accumulator overflow, leaves the fast path for the generic loop)
        long iacc = 0;
        while (i < n && !(items.get(i) instanceof Double)) {
            BigInteger b = toBig(items.get(i));
            if (b.bitLength() >= 32) return genericSum(BigInteger.valueOf(iacc), items, i);
            long v = b.longValue();
            long s = iacc + v;
            if (((iacc ^ s) & (v ^ s)) < 0) return genericSum(BigInteger.valueOf(iacc), items, i);
            iacc = s;
            i++;
        }
        if (i == n) return iacc;
        // first float: int + float -> float, then the compensated float loop
        double f = (double) iacc + (Double) items.get(i++);
        double c = 0.0;
        while (i < n) {
            Number it = items.get(i++);
            if (it instanceof Double d) {
                double x = d;
                double t = f + x;
                if (Math.abs(f) >= Math.abs(x)) c += (f - t) + x;
                else c += (x - t) + f;
                f = t;
                continue;
            }
            BigInteger b = toBig(it);
            if (b.bitLength() < 32) { // fits a C long (32-bit on Windows): added without compensation
                f += b.longValue();
                continue;
            }
            if (c != 0.0 && Double.isFinite(c)) f += c;
            return genericSum(f, items, i - 1);
        }
        if (c != 0.0 && Double.isFinite(c)) f += c;
        return f;
    }

    /** CPython's generic {@code sum} loop: {@code result = result + item} (exact ints, float+int with correct rounding). */
    private static Number genericSum(Number result, List<? extends Number> items, int from) {
        for (int i = from; i < items.size(); i++) {
            Number it = items.get(i);
            if (result instanceof Double r) {
                result = r + (it instanceof Double d ? d : trueDiv(toBig(it), BigInteger.ONE));
            } else if (it instanceof Double d) {
                result = trueDiv((BigInteger) result, BigInteger.ONE) + d;
            } else {
                result = ((BigInteger) result).add(toBig(it));
            }
        }
        if (result instanceof BigInteger b && b.bitLength() < 64) return b.longValue();
        return result;
    }

    private static BigInteger toBig(Number x) {
        if (x instanceof BigInteger b) return b;
        if (x instanceof Double) throw new IllegalArgumentException("not an int");
        return BigInteger.valueOf(x.longValue());
    }

    /** A plain left fold {@code acc = 0; for x: acc += x} (a Python {@code +=} loop, or {@code sum()} of numpy float64 scalars). */
    public static double sumPlain(double[] xs) {
        double acc = 0.0;
        for (int i = 0; i < xs.length; i++) acc = i == 0 ? 0.0 + xs[0] : acc + xs[i];
        return acc;
    }

    // ------------------------------------------------------------------------------------------ bisect / min / max

    /** {@code bisect.bisect_right(a, x, lo, hi)}. */
    public static int bisectRight(double[] a, double x, int lo, int hi) {
        if (lo < 0) throw new IllegalArgumentException("lo must be non-negative");
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (x < a[mid]) hi = mid;
            else lo = mid + 1;
        }
        return lo;
    }

    /** {@code bisect.bisect_left(a, x, lo, hi)}. */
    public static int bisectLeft(double[] a, double x, int lo, int hi) {
        if (lo < 0) throw new IllegalArgumentException("lo must be non-negative");
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (a[mid] < x) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    /** {@code max(items, key=f)} with float keys: the <b>first</b> item with the largest key (strict {@code >}). */
    public static <T> T maxBy(Iterable<T> items, ToDoubleFunction<? super T> key) {
        T best = null;
        double bk = 0;
        boolean any = false;
        for (T it : items) {
            double k = key.applyAsDouble(it);
            if (!any || k > bk) {
                best = it;
                bk = k;
                any = true;
            }
        }
        if (!any) throw new IllegalArgumentException("max() arg is an empty sequence");
        return best;
    }

    /** {@code min(items, key=f)} with float keys: the <b>first</b> item with the smallest key (strict {@code <}). */
    public static <T> T minBy(Iterable<T> items, ToDoubleFunction<? super T> key) {
        T best = null;
        double bk = 0;
        boolean any = false;
        for (T it : items) {
            double k = key.applyAsDouble(it);
            if (!any || k < bk) {
                best = it;
                bk = k;
                any = true;
            }
        }
        if (!any) throw new IllegalArgumentException("min() arg is an empty sequence");
        return best;
    }

    /** {@code max(items, key=...)} with an arbitrary key order (e.g. tuple keys): first maximal element. */
    public static <T> T maxBy(Iterable<T> items, Comparator<? super T> keyOrder) {
        T best = null;
        boolean any = false;
        for (T it : items) {
            if (!any || keyOrder.compare(it, best) > 0) {
                best = it;
                any = true;
            }
        }
        if (!any) throw new IllegalArgumentException("max() arg is an empty sequence");
        return best;
    }

    /** {@code min(items, key=...)} with an arbitrary key order: first minimal element. */
    public static <T> T minBy(Iterable<T> items, Comparator<? super T> keyOrder) {
        T best = null;
        boolean any = false;
        for (T it : items) {
            if (!any || keyOrder.compare(it, best) < 0) {
                best = it;
                any = true;
            }
        }
        if (!any) throw new IllegalArgumentException("min() arg is an empty sequence");
        return best;
    }

    // ------------------------------------------------------------------------------------------ text forms

    /** {@code repr(x)} / {@code str(x)} / f-string {@code {x}} of a Python float (shortest round-trip digits). */
    public static String repr(double x) {
        if (Double.isNaN(x)) return "nan";
        if (Double.isInfinite(x)) return x > 0 ? "inf" : "-inf";
        if (x == 0.0) return (Double.doubleToRawLongBits(x) < 0) ? "-0.0" : "0.0";
        String sign = x < 0 ? "-" : "";
        double ax = Math.abs(x);
        String[] dd = shortestDigits(ax);
        String digits = dd[0];
        int decpt = Integer.parseInt(dd[1]);
        StringBuilder sb = new StringBuilder(sign);
        if (decpt <= -4 || decpt > 16) {
            int exp = decpt - 1;
            sb.append(digits.charAt(0));
            if (digits.length() > 1) sb.append('.').append(digits, 1, digits.length());
            sb.append('e').append(exp < 0 ? '-' : '+');
            int ae = Math.abs(exp);
            if (ae < 10) sb.append('0');
            sb.append(ae);
        } else if (decpt <= 0) {
            sb.append("0.");
            for (int i = 0; i < -decpt; i++) sb.append('0');
            sb.append(digits);
        } else if (decpt >= digits.length()) {
            sb.append(digits);
            for (int i = digits.length(); i < decpt; i++) sb.append('0');
            sb.append(".0");
        } else {
            sb.append(digits, 0, decpt).append('.').append(digits, decpt, digits.length());
        }
        return sb.toString();
    }

    /**
     * Shortest decimal digit string that round-trips to {@code x > 0} (David Gay's mode 0, as CPython's repr):
     * among the shortest candidates the one nearest to x, ties to an even last digit.
     * Returns {digits, decpt} with x ~= 0.digits * 10^decpt.
     */
    static String[] shortestDigits(double x) {
        BigDecimal exact = new BigDecimal(x);
        for (int p = 1; p <= 17; p++) {
            MathContext mcF = new MathContext(p, RoundingMode.FLOOR), mcC = new MathContext(p, RoundingMode.CEILING);
            BigDecimal lo = exact.round(mcF), hi = exact.round(mcC);
            boolean okLo = lo.doubleValue() == x && lo.signum() > 0, okHi = hi.doubleValue() == x;
            BigDecimal pick = null;
            if (okLo && okHi) {
                int c = exact.subtract(lo).compareTo(hi.subtract(exact));
                if (c < 0) pick = lo;
                else if (c > 0) pick = hi;
                else pick = lastDigitEven(lo) ? lo : hi;
            } else if (okLo) {
                pick = lo;
            } else if (okHi) {
                pick = hi;
            }
            if (pick != null) {
                BigDecimal s = pick.stripTrailingZeros();
                String unscaled = s.unscaledValue().toString();
                int decpt = unscaled.length() - s.scale();
                return new String[]{unscaled, Integer.toString(decpt)};
            }
        }
        throw new IllegalStateException("no round-trip representation for " + x);
    }

    private static boolean lastDigitEven(BigDecimal d) {
        return d.unscaledValue().testBit(0) == false;
    }

    /** {@code repr(s)} of a Python str (quote choice and escapes as CPython's {@code unicode_repr}). */
    public static String repr(String s) {
        boolean hasSq = s.indexOf('\'') >= 0, hasDq = s.indexOf('"') >= 0;
        char q = (hasSq && !hasDq) ? '"' : '\'';
        StringBuilder sb = new StringBuilder(s.length() + 2).append(q);
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == q || cp == '\\') sb.append('\\').append((char) cp);
            else if (cp == '\t') sb.append("\\t");
            else if (cp == '\n') sb.append("\\n");
            else if (cp == '\r') sb.append("\\r");
            else if (cp < 0x20 || cp == 0x7f) sb.append(String.format("\\x%02x", cp));
            else if (cp < 0x7f) sb.append((char) cp);
            else if (isPrintable(cp)) sb.appendCodePoint(cp);
            else if (cp <= 0xff) sb.append(String.format("\\x%02x", cp));
            else if (cp <= 0xffff) sb.append(String.format("\\u%04x", cp));
            else sb.append(String.format("\\U%08x", cp));
        }
        return sb.append(q).toString();
    }

    /** Python's {@code str.isprintable} for one code point (Unicode data of the running JDK). */
    static boolean isPrintable(int cp) {
        if (cp == ' ') return true;
        int t = Character.getType(cp);
        return !(t == Character.CONTROL || t == Character.FORMAT || t == Character.SURROGATE || t == Character.PRIVATE_USE
                || t == Character.UNASSIGNED || t == Character.LINE_SEPARATOR || t == Character.PARAGRAPH_SEPARATOR
                || t == Character.SPACE_SEPARATOR);
    }

    /** {@code json.dumps(s)} of a str with the default {@code ensure_ascii=True}. */
    public static String jsonString(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (ch < 0x20 || ch > 0x7e) sb.append(String.format("\\u%04x", (int) ch));
                    else sb.append(ch);
                }
            }
        }
        return sb.append('"').toString();
    }
}
