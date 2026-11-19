package com.flyingeaves.gen.py;

/**
 * Bit-exact Java versions of the two C runtime math functions whose results a Python program can depend on,
 * as they run on the reference setup: Windows UCRT ({@code ucrtbase.dll}, 10.0.26100 family) on an x86-64 CPU
 * with FMA3, where UCRT dispatches to its FMA3 code paths.
 * <ul>
 * <li>{@link #pow}: CPython's float {@code x ** y} (via {@link #floatPow}) and numpy's float64 scalar power call
 *     the C {@code pow}. UCRT's FMA3 {@code pow} is Arm's optimized-routines {@code pow} (log table of 128 entries,
 *     exp table of 256 entries) with fused multiply-adds in exactly three places, plus two UCRT shortcuts
 *     ({@code y == 1} returns x, {@code |x| == 1} returns ±1). It is <b>not</b> correctly rounded:
 *     {@code x ** 2 != x*x} for about 0.04% of inputs and {@code x ** 0.5 != sqrt(x)} for about 0.05%, so Java's
 *     {@code x*x}, {@link Math#pow} and {@link StrictMath#pow} all disagree with CPython on those inputs.</li>
 * <li>{@link #exp}: numpy's float64 {@code np.exp} on this CPU (AVX2, no AVX-512) calls the C {@code exp} per
 *     element. UCRT's FMA3 {@code exp} is an AMD-libm style algorithm (64-entry 2^(j/64) head/tail tables, degree-5
 *     polynomial evaluated with FMAs). {@link Math#exp} differs on about 0.5% of arguments.</li>
 * </ul>
 * The code follows the DLL's behaviour on the reference setup instruction by instruction (operand order of
 * additions/multiplications included). The tables are public data: {@code POW_INVC}, {@code POW_LOGC},
 * {@code POW_LOGCTAIL} and {@code POW_EXP_TAB} are entry for entry identical to Arm optimized-routines
 * {@code math/pow_log_data.c} (N = 128) and {@code math/exp_data.c} (N = 256), MIT OR Apache-2.0 WITH
 * LLVM-exception (checked 2026-11-14); {@code EXP_T3} is 2^(j/64) correctly rounded and {@code EXP_T1}/{@code EXP_T2}
 * its head/tail split, as in AMD libm. Verified by the self test
 * against hundreds of thousands of CPython / numpy results ({@code libm} vectors). On a CPU without FMA3, UCRT would
 * take other code paths and these functions would no longer describe CPython's results there.
 *
 * <p>Everything else in {@code math}: {@code sqrt} is IEEE ({@link Math#sqrt}); {@code log} and {@code atan2} are not
 * emulated (the generator uses {@code log} only inside {@code random.sample}'s set-size estimate, where it cannot
 * change the result, and {@code atan2} only as a sort key; see {@link #atan2}).
 */
public final class UcrtMath {
    private UcrtMath() {
    }

    // ------------------------------------------------------------------------------------------ CPython float **

    /**
     * CPython 3.13 {@code float_pow}: Python's {@code x ** y} for two floats (also {@code float ** int}, the int
     * converted to float first). Special cases are handled the way CPython does before it calls the C {@code pow}
     * on {@code |x|}; a negative base with an odd integer exponent negates the result. A negative base with a
     * non-integer exponent gives a complex number in Python and throws here; overflow throws (OverflowError);
     * {@code 0 ** negative} throws (ZeroDivisionError).
     */
    public static double floatPow(double iv, double iw) {
        if (iw == 0) return 1.0;
        if (Double.isNaN(iv)) return iv;
        if (Double.isNaN(iw)) return iv == 1.0 ? 1.0 : iw;
        if (Double.isInfinite(iw)) {
            double a = Math.abs(iv);
            if (a == 1.0) return 1.0;
            else if ((iw > 0.0) == (a > 1.0)) return Math.abs(iw);
            else return 0.0;
        }
        if (Double.isInfinite(iv)) {
            boolean odd = isOddInteger(iw);
            if (iw > 0.0) return odd ? iv : Math.abs(iv);
            else return odd ? Math.copySign(0.0, iv) : 0.0;
        }
        if (iv == 0.0) {
            boolean odd = isOddInteger(iw);
            if (iw < 0.0) throw new ArithmeticException("ZeroDivisionError: zero to a negative power");
            return odd ? iv : 0.0;
        }
        boolean negate = false;
        if (iv < 0.0) {
            if (iw != Math.floor(iw)) throw new ArithmeticException("negative number to a fractional power is complex in Python");
            iv = -iv;
            negate = isOddInteger(iw);
        }
        if (iv == 1.0) return negate ? -1.0 : 1.0;
        double ix = pow(iv, iw);
        if (Double.isInfinite(ix)) throw new ArithmeticException("OverflowError: (34, 'Result too large')");
        return negate ? -ix : ix;
    }

    /** CPython's {@code DOUBLE_IS_ODD_INTEGER}: {@code fmod(fabs(x), 2.0) == 1.0}. */
    private static boolean isOddInteger(double x) {
        return Math.abs(x) % 2.0 == 1.0;
    }

    // ------------------------------------------------------------------------------------------ pow (UCRT FMA3)

    private static long top12(double x) {
        return Double.doubleToRawLongBits(x) >>> 52;
    }

    private static boolean zeroinfnan(long i) {
        return Long.compareUnsigned(2 * i - 1, 2 * 0x7ff0000000000000L - 1) >= 0;
    }

    /** 0: not an integer, 1: odd integer, 2: even integer. */
    private static int checkint(long iy) {
        int e = (int) (iy >>> 52 & 0x7ff);
        if (e < 0x3ff) return 0;
        if (e > 0x3ff + 52) return 2;
        if ((iy & ((1L << (0x3ff + 52 - e)) - 1)) != 0) return 0;
        if ((iy & (1L << (0x3ff + 52 - e))) != 0) return 1;
        return 2;
    }

    private static boolean issignaling(double x) {
        long ix = Double.doubleToRawLongBits(x);
        return Long.compareUnsigned(2 * (ix ^ 0x0008000000000000L), 2 * 0x7ff8000000000000L) > 0;
    }

    private static final double SIGN_NEG_ONE = -1.0;

    /** The C runtime {@code pow(x, y)} of the reference setup (ucrtbase FMA3 path), for any inputs. */
    public static double pow(double x, double y) {
        int signBias = 0;
        long ix = Double.doubleToRawLongBits(x);
        long iy = Double.doubleToRawLongBits(y);
        long topx = top12(x);
        long topy = top12(y);
        // unsigned 32-bit compares in the C code: topx - 1 >= 0x7fe, (topy & 0x7ff) - 0x3be >= 0x80
        if (topx == 0 || topx >= 0x7ff || (topy & 0x7ff) < 0x3be || (topy & 0x7ff) >= 0x43e) {
            if (zeroinfnan(iy)) {
                if (2 * iy == 0) return issignaling(x) ? x + y : 1.0;
                if (ix == 0x3ff0000000000000L) return issignaling(y) ? x + y : 1.0;
                if (Long.compareUnsigned(2 * ix, 2 * 0x7ff0000000000000L) > 0 || Long.compareUnsigned(2 * iy, 2 * 0x7ff0000000000000L) > 0) return x + y;
                if (2 * ix == 2 * 0x3ff0000000000000L) return 1.0;
                if ((Long.compareUnsigned(2 * ix, 2 * 0x3ff0000000000000L) < 0) == ((iy >>> 63) == 0)) return 0.0;
                return y * y;
            }
            if (zeroinfnan(ix)) {
                double x2 = x * x;
                int sign = 0;
                if ((ix >>> 63) != 0 && checkint(iy) == 1) {
                    x2 = -x2;
                    sign = 1;
                }
                if (2 * ix == 0 && (iy >>> 63) != 0) return sign != 0 ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY; // __math_divzero
                return (iy >>> 63) != 0 ? 1 / x2 : x2;
            }
            if ((ix >>> 63) != 0) {
                int yint = checkint(iy);
                if (yint == 0) return Double.NaN; // __math_invalid
                if (yint == 1) signBias = SIGN_BIAS;
                ix &= 0x7fffffffffffffffL;
                topx &= 0x7ff;
            }
            if ((topy & 0x7ff) < 0x3be || (topy & 0x7ff) >= 0x43e) {
                if (ix == 0x3ff0000000000000L) return 1.0;
                if ((topy & 0x7ff) < 0x3be) return Long.compareUnsigned(ix, 0x3ff0000000000000L) > 0 ? 1.0 + y : 1.0 - y;
                boolean big = Long.compareUnsigned(ix, 0x3ff0000000000000L) > 0;
                return big == (topy < 0x800) ? Double.POSITIVE_INFINITY : 0.0; // __math_oflow(0) / __math_uflow(0)
            }
            if (topx == 0) {
                ix = Double.doubleToRawLongBits(x * 0x1p52);
                ix &= 0x7fffffffffffffffL;
                ix -= 52L << 52;
            }
        }
        // UCRT shortcuts in front of the core
        if (iy == 0x3ff0000000000000L) return x;
        if (ix == 0x3ff0000000000000L) return signBias == 0 ? 1.0 : SIGN_NEG_ONE;

        // log_inline(ix) -> hi + lo
        long tmp = ix - 0x3fe6955500000000L;
        int i = (int) ((tmp >>> (52 - 7)) & 0x7f);
        int k = (int) (tmp >> 52);
        long iz = ix - (tmp & (0xfffL << 52));
        double z = Double.longBitsToDouble(iz);
        double kd = k;
        double invc = Double.longBitsToDouble(POW_INVC[i]);
        double logc = Double.longBitsToDouble(POW_LOGC[i]);
        double logctail = Double.longBitsToDouble(POW_LOGCTAIL[i]);
        double r = Math.fma(z, invc, -1.0);
        double t1 = kd * LN2HI + logc;
        double t2 = t1 + r;
        double lo1 = kd * LN2LO + logctail;
        double lo2 = t1 - t2 + r;
        double ar = r * A0;
        double ar2 = r * ar;
        double ar3 = r * ar2;
        double hi = t2 + ar2;
        double lo3 = Math.fma(ar, r, -ar2);
        double lo4 = t2 - hi + ar2;
        double p = ar3 * ((A1 + r * A2) + ar2 * ((A3 + r * A4) + ar2 * (A5 + r * A6)));
        double lo = lo1 + lo2 + lo3 + lo4 + p;
        double ly = hi + lo;
        double ltail = hi - ly + lo;
        // y * (ly + ltail)
        double ehi = y * ly;
        double elo = y * ltail + Math.fma(y, ly, -ehi);
        return expInline(ehi, elo, signBias);
    }

    private static final int EXP_TABLE_BITS = 8;
    private static final int EXP_N = 1 << EXP_TABLE_BITS;
    private static final int SIGN_BIAS = 0x800 << EXP_TABLE_BITS;

    private static double expInline(double x, double xtail, int signBias) {
        long abstop = top12(x) & 0x7ff;
        // unsigned compares in the C code: abstop - top12(0x1p-54) >= top12(512) - top12(0x1p-54)
        if (abstop < 0x3c9 || abstop >= 0x408) {
            if (abstop < 0x3c9) {
                double one = 1.0 + x;
                return signBias != 0 ? -one : one;
            }
            if (abstop >= 0x409) {
                if ((Double.doubleToRawLongBits(x) >>> 63) != 0) return signBias != 0 ? -0.0 : 0.0; // __math_uflow
                return signBias != 0 ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;        // __math_oflow
            }
            abstop = 0;
        }
        double z = INVLN2N * x;
        double kd = z + SHIFT;
        long ki = Double.doubleToRawLongBits(kd) >>> 16;
        kd = (int) ki;
        double r = (x - kd * LN2HIN) + kd * LN2LON;
        r = r + xtail;
        int idx = 2 * (int) (ki & (EXP_N - 1));
        long top = (ki + signBias) << (52 - EXP_TABLE_BITS);
        double tail = Double.longBitsToDouble(POW_EXP_TAB[idx]);
        long sbits = POW_EXP_TAB[idx + 1] + top;
        double r2 = r * r;
        double tmp = (tail + r) + r2 * (C2 + r * C3) + r2 * r2 * (C4 + r * C5);
        if (abstop == 0) return specialcase(tmp, sbits, ki);
        double scale = Double.longBitsToDouble(sbits);
        return scale + scale * tmp;
    }

    private static double specialcase(double tmp, long sbits, long ki) {
        if ((ki & 0x80000000L) == 0) {
            sbits -= 1009L << 52;
            double scale = Double.longBitsToDouble(sbits);
            double y = scale + scale * tmp;
            return y * 0x1p1009;
        }
        sbits += 1022L << 52;
        double scale = Double.longBitsToDouble(sbits);
        double st = scale * tmp;
        double y = scale + st;
        if (Math.abs(y) < 1.0) {
            double one = y < 0.0 ? -1.0 : 1.0;
            double lo = scale - y + st;
            double hi = one + y;
            lo = one - hi + y + lo;
            y = (hi + lo) - one;
            if (y == 0.0) y = Double.longBitsToDouble(sbits & 0x8000000000000000L);
        }
        return y * 0x1p-1022;
    }

    // ------------------------------------------------------------------------------------------ exp (UCRT FMA3)

    /** The C runtime {@code exp(x)} of the reference setup (ucrtbase FMA3 path); numpy float64 {@code np.exp}. */
    public static double exp(double x) {
        long bits = Double.doubleToRawLongBits(x);
        long ax = bits & 0x7fffffffffffffffL;
        if (ax >= 0x7ff0000000000000L) {
            if (bits == 0x7ff0000000000000L) return x;
            if (bits == 0xfff0000000000000L) return 0.0;
            return Double.longBitsToDouble(bits | 0x0008000000000000L);
        }
        if (!(x <= EXP_MAXARG && x >= EXP_MINARG)) {
            if (x > EXP_MAXARG) return Double.POSITIVE_INFINITY;
            if (x >= EXP_UFLOW_ARG) return Double.MIN_VALUE;
            return 0.0;
        }
        if (ax <= 0x3e50000000000000L) return x + 1.0;
        double t = x * EXP_64BYLN2;
        double kd = t < 0 ? Math.ceil(t) : Math.floor(t); // roundsd mode 3: toward zero
        int n = (int) t;                                  // cvttsd2si
        double r1 = Math.fma(kd, EXP_LN2BY64_LEAD, x);
        double r = kd * EXP_LN2BY64_TAIL + r1;
        int j = n & 0x3f;
        int m = n >> 6;
        double q = Math.fma(r, EXP_C110, EXP_C120);
        q = Math.fma(r, q, EXP_C150);
        q = Math.fma(r, q, EXP_C130);
        q = Math.fma(r, q, EXP_C140);
        double rr = r * r;
        double res = Math.fma(q, rr, r);
        res = res * Double.longBitsToDouble(EXP_T3[j]);
        res = res + Double.longBitsToDouble(EXP_T2[j]);
        res = res + Double.longBitsToDouble(EXP_T1[j]);
        if (m > -1022 || (m == -1022 && res >= 1.0)) {
            return Double.longBitsToDouble(Double.doubleToRawLongBits(res) + ((long) m << 52));
        }
        long scaleBits = 1L << ((m + 1074) & 63);
        return res * Double.longBitsToDouble(scaleBits);
    }

    // ------------------------------------------------------------------------------------------ others

    /**
     * {@code math.atan2(y, x)}: <b>not</b> emulated, uses {@link StrictMath#atan2}, which differs from UCRT's by an
     * ulp on ~18% of random arguments. Meant only as a sort key of cells around an integer centre, i.e. on
     * integer offsets: for every
     * offset in a 193x193 square the stable sort order by this key equals CPython's (self test
     * {@code ATAN2SORT}), because distinct directions differ by far more than an ulp and collinear offsets get
     * identical keys in both. Do not use it where exact values matter.
     */
    public static double atan2(double y, double x) {
        return StrictMath.atan2(y, x);
    }

    /**
     * {@code np.hypot(a, b)} for integer arguments:
     * equals {@code sqrt(a*a + b*b)} for every integer pair with |a|,|b| <= 2048 on the reference setup
     * (checked exhaustively by the self test), so ties between equal integer distances stay ties.
     */
    public static double hypotInt(long a, long b) {
        return Math.sqrt((double) (a * a + b * b));
    }

    // ------------------------------------------------------------------------------------------ constants (from the DLL)

    private static double d(long bits) {
        return Double.longBitsToDouble(bits);
    }

    private static final double LN2HI = d(0x3fe62e42fefa3800L), LN2LO = d(0x3d2ef35793c76730L);
    private static final double A0 = d(0xbfe0000000000000L);
    private static final double A1 = -d(0x3fe5555555555560L), A2 = d(0x3fe0000000000006L);
    private static final double A3 = d(0x3fe999999959554eL), A4 = -d(0x3fe555555529a47aL);
    private static final double A5 = -d(0x3ff2495b9b4845e9L), A6 = d(0x3ff0002b8b263fc3L);
    private static final double INVLN2N = d(0x40771547652b82feL), SHIFT = d(0x4238000000008000L);
    private static final double LN2HIN = d(0x3f662e42fefc0000L), LN2LON = d(0x3d2c610ca86c3899L);
    private static final double C2 = d(0x3fdffffffffffdbdL), C3 = d(0x3fc555555555543cL);
    private static final double C4 = d(0x3fa55555cf16e1edL), C5 = d(0x3f81111167a4b553L);

    private static final double EXP_MAXARG = d(0x40862e42fefa39efL), EXP_MINARG = d(0xc0874046dfefd9d0L);
    private static final double EXP_UFLOW_ARG = d(0xc0874910d52d3051L);
    private static final double EXP_64BYLN2 = d(0x40571547652b82feL);
    private static final double EXP_LN2BY64_LEAD = d(0xbf862e42fefa0000L), EXP_LN2BY64_TAIL = d(0xbd1cf79abc9e3b39L);
    private static final double EXP_C110 = d(0x3f56c16c16c16c17L), EXP_C120 = d(0x3f81111111111111L);
    private static final double EXP_C150 = d(0x3fa5555555555555L), EXP_C130 = d(0x3fc5555555555555L);
    private static final double EXP_C140 = d(0x3fe0000000000000L);

    // ---- tables: pow = Arm optimized-routines pow_log_data.c / exp_data.c (identical, see class doc); exp: 2^(j/64) head/tail/total
    private static final long[] POW_INVC = {
            0x3ff6a00000000000L, 0x3ff6800000000000L, 0x3ff6600000000000L, 0x3ff6400000000000L,
            0x3ff6200000000000L, 0x3ff6000000000000L, 0x3ff5e00000000000L, 0x3ff5c00000000000L,
            0x3ff5a00000000000L, 0x3ff5800000000000L, 0x3ff5600000000000L, 0x3ff5600000000000L,
            0x3ff5400000000000L, 0x3ff5200000000000L, 0x3ff5000000000000L, 0x3ff4e00000000000L,
            0x3ff4c00000000000L, 0x3ff4a00000000000L, 0x3ff4a00000000000L, 0x3ff4800000000000L,
            0x3ff4600000000000L, 0x3ff4400000000000L, 0x3ff4200000000000L, 0x3ff4000000000000L,
            0x3ff4000000000000L, 0x3ff3e00000000000L, 0x3ff3c00000000000L, 0x3ff3a00000000000L,
            0x3ff3a00000000000L, 0x3ff3800000000000L, 0x3ff3600000000000L, 0x3ff3400000000000L,
            0x3ff3400000000000L, 0x3ff3200000000000L, 0x3ff3000000000000L, 0x3ff3000000000000L,
            0x3ff2e00000000000L, 0x3ff2c00000000000L, 0x3ff2c00000000000L, 0x3ff2a00000000000L,
            0x3ff2800000000000L, 0x3ff2600000000000L, 0x3ff2600000000000L, 0x3ff2400000000000L,
            0x3ff2400000000000L, 0x3ff2200000000000L, 0x3ff2000000000000L, 0x3ff2000000000000L,
            0x3ff1e00000000000L, 0x3ff1c00000000000L, 0x3ff1c00000000000L, 0x3ff1a00000000000L,
            0x3ff1a00000000000L, 0x3ff1800000000000L, 0x3ff1600000000000L, 0x3ff1600000000000L,
            0x3ff1400000000000L, 0x3ff1400000000000L, 0x3ff1200000000000L, 0x3ff1000000000000L,
            0x3ff1000000000000L, 0x3ff0e00000000000L, 0x3ff0e00000000000L, 0x3ff0c00000000000L,
            0x3ff0c00000000000L, 0x3ff0a00000000000L, 0x3ff0a00000000000L, 0x3ff0800000000000L,
            0x3ff0800000000000L, 0x3ff0600000000000L, 0x3ff0400000000000L, 0x3ff0400000000000L,
            0x3ff0200000000000L, 0x3ff0200000000000L, 0x3ff0000000000000L, 0x3ff0000000000000L,
            0x3fefc00000000000L, 0x3fef800000000000L, 0x3fef400000000000L, 0x3fef000000000000L,
            0x3feec00000000000L, 0x3fee800000000000L, 0x3fee400000000000L, 0x3fee200000000000L,
            0x3fede00000000000L, 0x3feda00000000000L, 0x3fed600000000000L, 0x3fed400000000000L,
            0x3fed000000000000L, 0x3fecc00000000000L, 0x3feca00000000000L, 0x3fec600000000000L,
            0x3fec400000000000L, 0x3fec000000000000L, 0x3febe00000000000L, 0x3feba00000000000L,
            0x3feb800000000000L, 0x3feb400000000000L, 0x3feb200000000000L, 0x3feae00000000000L,
            0x3feac00000000000L, 0x3feaa00000000000L, 0x3fea600000000000L, 0x3fea400000000000L,
            0x3fea000000000000L, 0x3fe9e00000000000L, 0x3fe9c00000000000L, 0x3fe9a00000000000L,
            0x3fe9600000000000L, 0x3fe9400000000000L, 0x3fe9200000000000L, 0x3fe9000000000000L,
            0x3fe8c00000000000L, 0x3fe8a00000000000L, 0x3fe8800000000000L, 0x3fe8600000000000L,
            0x3fe8400000000000L, 0x3fe8200000000000L, 0x3fe7e00000000000L, 0x3fe7c00000000000L,
            0x3fe7a00000000000L, 0x3fe7800000000000L, 0x3fe7600000000000L, 0x3fe7400000000000L,
            0x3fe7200000000000L, 0x3fe7000000000000L, 0x3fe6e00000000000L, 0x3fe6c00000000000L,
    };
    private static final long[] POW_LOGC = {
            0xbfd62c82f2b9c800L, 0xbfd5d1bdbf580800L, 0xbfd5767717455800L, 0xbfd51aad872df800L,
            0xbfd4be5f95777800L, 0xbfd4618bc21c6000L, 0xbfd404308686a800L, 0xbfd3a64c55694800L,
            0xbfd347dd9a988000L, 0xbfd2e8e2bae12000L, 0xbfd2895a13de8800L, 0xbfd2895a13de8800L,
            0xbfd22941fbcf7800L, 0xbfd1c898c1699800L, 0xbfd1675cababa800L, 0xbfd1058bf9ae4800L,
            0xbfd0a324e2739000L, 0xbfd0402594b4d000L, 0xbfd0402594b4d000L, 0xbfcfb9186d5e4000L,
            0xbfcef0adcbdc6000L, 0xbfce27076e2af000L, 0xbfcd5c216b4fc000L, 0xbfcc8ff7c79aa000L,
            0xbfcc8ff7c79aa000L, 0xbfcbc286742d9000L, 0xbfcaf3c94e80c000L, 0xbfca23bc1fe2b000L,
            0xbfca23bc1fe2b000L, 0xbfc9525a9cf45000L, 0xbfc87fa06520d000L, 0xbfc7ab890210e000L,
            0xbfc7ab890210e000L, 0xbfc6d60fe719d000L, 0xbfc5ff3070a79000L, 0xbfc5ff3070a79000L,
            0xbfc526e5e3a1b000L, 0xbfc44d2b6ccb8000L, 0xbfc44d2b6ccb8000L, 0xbfc371fc201e9000L,
            0xbfc29552f81ff000L, 0xbfc1b72ad52f6000L, 0xbfc1b72ad52f6000L, 0xbfc0d77e7cd09000L,
            0xbfc0d77e7cd09000L, 0xbfbfec9131dbe000L, 0xbfbe27076e2b0000L, 0xbfbe27076e2b0000L,
            0xbfbc5e548f5bc000L, 0xbfba926d3a4ae000L, 0xbfba926d3a4ae000L, 0xbfb8c345d631a000L,
            0xbfb8c345d631a000L, 0xbfb6f0d28ae56000L, 0xbfb51b073f062000L, 0xbfb51b073f062000L,
            0xbfb341d7961be000L, 0xbfb341d7961be000L, 0xbfb16536eea38000L, 0xbfaf0a30c0118000L,
            0xbfaf0a30c0118000L, 0xbfab42dd71198000L, 0xbfab42dd71198000L, 0xbfa77458f632c000L,
            0xbfa77458f632c000L, 0xbfa39e87b9fec000L, 0xbfa39e87b9fec000L, 0xbf9f829b0e780000L,
            0xbf9f829b0e780000L, 0xbf97b91b07d58000L, 0xbf8fc0a8b0fc0000L, 0xbf8fc0a8b0fc0000L,
            0xbf7fe02a6b100000L, 0xbf7fe02a6b100000L, 0x0000000000000000L, 0x0000000000000000L,
            0x3f80101575890000L, 0x3f90205658938000L, 0x3f98492528c90000L, 0x3fa0415d89e74000L,
            0x3fa466aed42e0000L, 0x3fa894aa149fc000L, 0x3faccb73cdddc000L, 0x3faeea31c006c000L,
            0x3fb1973bd1466000L, 0x3fb3bdf5a7d1e000L, 0x3fb5e95a4d97a000L, 0x3fb700d30aeac000L,
            0x3fb9335e5d594000L, 0x3fbb6ac88dad6000L, 0x3fbc885801bc4000L, 0x3fbec739830a2000L,
            0x3fbfe89139dbe000L, 0x3fc1178e8227e000L, 0x3fc1aa2b7e23f000L, 0x3fc2d1610c868000L,
            0x3fc365fcb0159000L, 0x3fc4913d8333b000L, 0x3fc527e5e4a1b000L, 0x3fc6574ebe8c1000L,
            0x3fc6f0128b757000L, 0x3fc7898d85445000L, 0x3fc8beafeb390000L, 0x3fc95a5adcf70000L,
            0x3fca93ed3c8ae000L, 0x3fcb31d8575bd000L, 0x3fcbd087383be000L, 0x3fcc6ffbc6f01000L,
            0x3fcdb13db0d49000L, 0x3fce530effe71000L, 0x3fcef5ade4dd0000L, 0x3fcf991c6cb3b000L,
            0x3fd07138604d5800L, 0x3fd0c42d67616000L, 0x3fd1178e8227e800L, 0x3fd16b5ccbacf800L,
            0x3fd1bf99635a6800L, 0x3fd214456d0eb800L, 0x3fd2bef07cdc9000L, 0x3fd314f1e1d36000L,
            0x3fd36b6776be1000L, 0x3fd3c25277333000L, 0x3fd419b423d5e800L, 0x3fd4718dc271c800L,
            0x3fd4c9e09e173000L, 0x3fd522ae0738a000L, 0x3fd57bf753c8d000L, 0x3fd5d5bddf596000L,
    };
    private static final long[] POW_LOGCTAIL = {
            0x3cfab42428375680L, 0xbd1ca508d8e0f720L, 0xbd2362a4d5b6506dL, 0xbce684e49eb067d5L,
            0xbd041b6993293ee0L, 0x3d13d82f484c84ccL, 0x3cdc42f3ed820b3aL, 0x3d20b1c686519460L,
            0x3d25594dd4c58092L, 0x3d267b1e99b72bd8L, 0x3d15ca14b6cfb03fL, 0x3d15ca14b6cfb03fL,
            0xbd165a242853da76L, 0xbd1fafbc68e75404L, 0x3d1f1fc63382a8f0L, 0xbd26a8c4fd055a66L,
            0xbd0c6bee7ef4030eL, 0xbcf036b89ef42d7fL, 0xbcf036b89ef42d7fL, 0x3d0d572aab993c87L,
            0x3d2b26b79c86af24L, 0xbd172f4f543fff10L, 0x3d21ba91bbca681bL, 0x3d27794f689f8434L,
            0x3d27794f689f8434L, 0x3d194eb0318bb78fL, 0x3cba4e633fcd9066L, 0xbd258c64dc46c1eaL,
            0xbd258c64dc46c1eaL, 0xbd2ad1d904c1d4e3L, 0x3d2bbdbf7fdbfa09L, 0x3d2bdb9072534a58L,
            0x3d2bdb9072534a58L, 0xbd10e46aa3b2e266L, 0xbd1e9e439f105039L, 0xbd1e9e439f105039L,
            0xbd20de8b90075b8fL, 0x3d170cc16135783cL, 0x3d170cc16135783cL, 0x3cf178864d27543aL,
            0xbd248d301771c408L, 0xbd2e80a41811a396L, 0xbd2e80a41811a396L, 0x3d0a699688e85bf4L,
            0x3d0a699688e85bf4L, 0xbd2575545ca333f2L, 0x3d2a342c2af0003cL, 0x3d2a342c2af0003cL,
            0xbd1d0c57585fbe06L, 0x3d253935e85baac8L, 0x3d253935e85baac8L, 0x3d137c294d2f5668L,
            0x3d137c294d2f5668L, 0xbd269737c93373daL, 0x3d1f025b61c65e57L, 0x3d1f025b61c65e57L,
            0x3d2c5edaccf913dfL, 0x3d2c5edaccf913dfL, 0x3d147c5e768fa309L, 0x3d2d599e83368e91L,
            0x3d2d599e83368e91L, 0x3d1c827ae5d6704cL, 0x3d1c827ae5d6704cL, 0xbd2cfc4634f2a1eeL,
            0xbd2cfc4634f2a1eeL, 0x3cf502b7f526feaaL, 0x3cf502b7f526feaaL, 0xbd2980267c7e09e4L,
            0xbd2980267c7e09e4L, 0xbd288d5493faa639L, 0xbcdf1e7cf6d3a69cL, 0xbcdf1e7cf6d3a69cL,
            0xbd19e23f0dda40e4L, 0xbd19e23f0dda40e4L, 0x0000000000000000L, 0x0000000000000000L,
            0xbd10c76b999d2be8L, 0xbd23dc5b06e2f7d2L, 0xbd2aa0ba325a0c34L, 0x3d0111c05cf1d753L,
            0xbd2c167375bdfd28L, 0xbd197995d05a267dL, 0xbd1a68f247d82807L, 0xbd0e113e4fc93b7bL,
            0xbd25325d560d9e9bL, 0x3d2cc85ea5db4ed7L, 0xbd2c69063c5d1d1eL, 0x3cec1e8da99ded32L,
            0x3d23115c3abd47daL, 0xbd1390802bf768e5L, 0x3d2646d1c65aacd3L, 0xbd2dc068afe645e0L,
            0xbd2534d64fa10afdL, 0x3d21ef78ce2d07f2L, 0x3d2ca78e44389934L, 0x3d039d6ccb81b4a1L,
            0x3cc62fa8234b7289L, 0x3d25837954fdb678L, 0x3d2633e8e5697dc7L, 0x3d19cf8b2c3c2e78L,
            0xbd25118de59c21e1L, 0xbd1c661070914305L, 0xbd073d54aae92cd1L, 0x3d07f22858a0ff6fL,
            0xbd28724350562169L, 0xbd0c358d4eace1aaL, 0xbd2d4bc4595412b6L, 0xbcf1ec72c5962bd2L,
            0xbd2aff2af715b035L, 0x3cc212276041f430L, 0xbcca211565bb8e11L, 0x3d1bcbecca0cdf30L,
            0x3cf89cdb16ed4e91L, 0x3d27188b163ceae9L, 0xbd2c210e63a5f01cL, 0x3d2b9acdf7a51681L,
            0x3d2ca6ed5147bdb7L, 0x3d0a87deba46baeaL, 0x3d2a9cfa4a5004f4L, 0xbd28e27ad3213cb8L,
            0x3d116ecdb0f177c8L, 0x3d183b54b606bd5cL, 0x3d08e436ec90e09dL, 0xbd2f27ce0967d675L,
            0xbd2e20891b0ad8a4L, 0x3d2ebe708164c759L, 0x3d1fadedee5d40efL, 0xbd0a0b2a08a465dcL,
    };
    private static final long[] POW_EXP_TAB = {
            0x0000000000000000L, 0x3ff0000000000000L, 0xbc84e82fc61851acL, 0x3feffb1afa5abcbfL,
            0x3c9b3b4f1a88bf6eL, 0x3feff63da9fb3335L, 0xbc82985dd8521d32L, 0x3feff168143b0281L,
            0xbc7160139cd8dc5dL, 0x3fefec9a3e778061L, 0x3c651e617061bfbdL, 0x3fefe7d42e11bbccL,
            0xbc905e7a108766d1L, 0x3fefe315e86e7f85L, 0x3c845fad437fa426L, 0x3fefde5f72f654b1L,
            0x3c8cd2523567f613L, 0x3fefd9b0d3158574L, 0xbc954529642b232fL, 0x3fefd50a0e3c1f89L,
            0xbc8bce8023f98efaL, 0x3fefd06b29ddf6deL, 0x3c8293708ef5c32eL, 0x3fefcbd42b72a836L,
            0x3c60f74e61e6c861L, 0x3fefc74518759bc8L, 0xbc95b9280905b2a4L, 0x3fefc2bdf66607e0L,
            0x3c90a3e45b33d399L, 0x3fefbe3ecac6f383L, 0x3c84f31f32c4b7e7L, 0x3fefb9c79b1f3919L,
            0x3c979aa65d837b6dL, 0x3fefb5586cf9890fL, 0x3c9407fb30d06420L, 0x3fefb0f145e46c85L,
            0x3c8eb51a92fdeffcL, 0x3fefac922b7247f7L, 0xbc9a5d04b3b9911bL, 0x3fefa83b23395decL,
            0x3c3ebe3d702f9cd1L, 0x3fefa3ec32d3d1a2L, 0xbc937a01f0739546L, 0x3fef9fa55fdfa9c5L,
            0xbc6a033489906e0bL, 0x3fef9b66affed31bL, 0x3c8b8268b04ef0a5L, 0x3fef973028d7233eL,
            0xbc9556522a2fbd0eL, 0x3fef9301d0125b51L, 0xbc9ac46e44a2ebccL, 0x3fef8edbab5e2ab6L,
            0xbc5080ef8c4eea55L, 0x3fef8abdc06c31ccL, 0xbc65704e90c9f860L, 0x3fef86a814f204abL,
            0xbc91c923b9d5f416L, 0x3fef829aaea92de0L, 0xbc897cea57e46280L, 0x3fef7e95934f312eL,
            0x3c80d3e3e95c55afL, 0x3fef7a98c8a58e51L, 0x3c56f01429e2b9d2L, 0x3fef76a45471c3c2L,
            0xbc801b15eaa59348L, 0x3fef72b83c7d517bL, 0x3c6e653b2459034bL, 0x3fef6ed48695bbc0L,
            0xbc8f1ff055de323dL, 0x3fef6af9388c8deaL, 0x3c92cc7ea345b7dcL, 0x3fef672658375d2fL,
            0x3c8b898c3f1353bfL, 0x3fef635beb6fcb75L, 0x3c957bfb2876ea9eL, 0x3fef5f99f8138a1cL,
            0xbc96d99c7611eb26L, 0x3fef5be084045cd4L, 0x3c8cdc1873af2155L, 0x3fef582f95281c6bL,
            0x3c9aecf73e3a2f60L, 0x3fef54873168b9aaL, 0xbc9493684653a131L, 0x3fef50e75eb44027L,
            0xbc8fe782cb86389dL, 0x3fef4d5022fcd91dL, 0xbc98e2899077520aL, 0x3fef49c18438ce4dL,
            0x3c8a6f4144a6c38dL, 0x3fef463b88628cd6L, 0x3c9120fcd4f59273L, 0x3fef42be3578a819L,
            0x3c807a05b0e4047dL, 0x3fef3f49917ddc96L, 0x3c89b788c188c9b8L, 0x3fef3bdda27912d1L,
            0x3c968efde3a8a894L, 0x3fef387a6e756238L, 0x3c877afbca90ef84L, 0x3fef351ffb82140aL,
            0x3c875e18f274487dL, 0x3fef31ce4fb2a63fL, 0x3c91512f082876eeL, 0x3fef2e85711ece75L,
            0x3c80472b981fe7f2L, 0x3fef2b4565e27cddL, 0x3c9a02f0c7d75ec6L, 0x3fef280e341ddf29L,
            0xbc96b87b3f71085eL, 0x3fef24dfe1f56381L, 0xbc803297e78260bfL, 0x3fef21ba7591bb70L,
            0x3c82f7e16d09ab31L, 0x3fef1e9df51fdee1L, 0xbc95b77e5ccd9fbfL, 0x3fef1b8a66d10f13L,
            0xbc3d219b1a6fbffaL, 0x3fef187fd0dad990L, 0xbc91e75c40b4251eL, 0x3fef157e39771b2fL,
            0x3c8b3782720c0ab4L, 0x3fef1285a6e4030bL, 0x3c98a911f1f7785aL, 0x3fef0f961f641589L,
            0x3c6e149289cecb8fL, 0x3fef0cafa93e2f56L, 0xbc61e7c998db7dbbL, 0x3fef09d24abd886bL,
            0x3c834d754db0abb6L, 0x3fef06fe0a31b715L, 0x3c85425c11faadf4L, 0x3fef0432edeeb2fdL,
            0x3c864201e2ac744cL, 0x3fef0170fc4cd831L, 0xbc979517a03e2847L, 0x3feefeb83ba8ea32L,
            0x3c8fdd395dd3f84aL, 0x3feefc08b26416ffL, 0xbc800e2a46da4beeL, 0x3feef96266e3fa2dL,
            0xbc86a3803b8e5b04L, 0x3feef6c55f929ff1L, 0xbc87430803972b34L, 0x3feef431a2de883bL,
            0xbc924aedcc4b5068L, 0x3feef1a7373aa9cbL, 0xbc954de30ae02d94L, 0x3feeef26231e754aL,
            0xbc9907f81b512d8eL, 0x3feeecae6d05d866L, 0xbc94f2487e1c03ecL, 0x3feeea401b7140efL,
            0xbc71d1e83e9436d2L, 0x3feee7db34e59ff7L, 0x3c914a5432fcb2f4L, 0x3feee57fbfec6cf4L,
            0xbc991919b3ce1b15L, 0x3feee32dc313a8e5L, 0x3c79c3bba5562a2fL, 0x3feee0e544ede173L,
            0x3c859f48a72a4c6dL, 0x3feedea64c123422L, 0xbc85a71612e21658L, 0x3feedc70df1c5175L,
            0xbc9312607a28698aL, 0x3feeda4504ac801cL, 0x3c86421f6f1d24d6L, 0x3feed822c367a024L,
            0xbc58a78f4817895bL, 0x3feed60a21f72e2aL, 0xbc9348a6815fce65L, 0x3feed3fb2709468aL,
            0xbc7c2c9b67499a1bL, 0x3feed1f5d950a897L, 0x3c835c43984d9871L, 0x3feecffa3f84b9d4L,
            0x3c4363ed60c2ac11L, 0x3feece086061892dL, 0xbc632afc8d9473a0L, 0x3feecc2042a7d232L,
            0x3c9666093b0664efL, 0x3feeca41ed1d0057L, 0xbc95fc5e44de020eL, 0x3feec86d668b3237L,
            0x3c6ecce1daa10379L, 0x3feec6a2b5c13cd0L, 0xbc7ea0148327c42fL, 0x3feec4e1e192aed2L,
            0x3c93ff8e3f0f1230L, 0x3feec32af0d7d3deL, 0xbc7a843ad1a88022L, 0x3feec17dea6db7d7L,
            0x3c7690cebb7aafb0L, 0x3feebfdad5362a27L, 0x3c892ca3bf144e63L, 0x3feebe41b817c114L,
            0x3c931dbdeb54e077L, 0x3feebcb299fddd0dL, 0xbc902c99b04aa8b0L, 0x3feebb2d81d8abffL,
            0xbc8f94340071a38eL, 0x3feeb9b2769d2ca7L, 0x3c73e34f67e67118L, 0x3feeb8417f4531eeL,
            0xbc87deccdc93a349L, 0x3feeb6daa2cf6642L, 0xbc75a3b1197ba0f0L, 0x3feeb57de83f4eefL,
            0xbc78dec6bd0f385fL, 0x3feeb42b569d4f82L, 0x3c81bd2888075068L, 0x3feeb2e2f4f6ad27L,
            0xbc861246ec7b5cf6L, 0x3feeb1a4ca5d920fL, 0xbc896be8ae89ef8fL, 0x3feeb070dde910d2L,
            0x3c93350518fdd78eL, 0x3feeaf4736b527daL, 0xbc88e6ac90348602L, 0x3feeae27dbe2c4cfL,
            0x3c7b98b72f8a9b05L, 0x3feead12d497c7fdL, 0xbc91af7f1365c3acL, 0x3feeac0827ff07ccL,
            0x3c9063e1e21c5409L, 0x3feeab07dd485429L, 0xbc943a3540d1898aL, 0x3feeaa11fba87a03L,
            0x3c34c7855019c6eaL, 0x3feea9268a5946b7L, 0xbc951f58ddaa8090L, 0x3feea84590998b93L,
            0x3c9432e62b64c035L, 0x3feea76f15ad2148L, 0xbc82e1648e50a17cL, 0x3feea6a320dceb71L,
            0xbc8ce44a6199769fL, 0x3feea5e1b976dc09L, 0x3c95f30eda98a575L, 0x3feea52ae6cdf6f4L,
            0xbc8c33c53bef4da8L, 0x3feea47eb03a5585L, 0x3c917ecda8a72159L, 0x3feea3dd1d1929fdL,
            0xbc845378892be9aeL, 0x3feea34634ccc320L, 0xbc9345f3cee1ae6eL, 0x3feea2b9febc8fb7L,
            0xbc93cedd78565858L, 0x3feea23882552225L, 0xbc85c33fdf910406L, 0x3feea1c1c70833f6L,
            0x3c5710aa807e1964L, 0x3feea155d44ca973L, 0x3c81079ab5789604L, 0x3feea0f4b19e9538L,
            0xbc93b3efbf5e2228L, 0x3feea09e667f3bcdL, 0x3c727df161cd7778L, 0x3feea052fa75173eL,
            0xbc6a12ad8734b982L, 0x3feea012750bdabfL, 0x3c93f9924a05b767L, 0x3fee9fdcddd47645L,
            0xbc6367efb86da9eeL, 0x3fee9fb23c651a2fL, 0xbc87557939a8b5efL, 0x3fee9f9298593ae5L,
            0xbc80dc3d54e08851L, 0x3fee9f7df9519484L, 0x3c51ed2f56fa9d1aL, 0x3fee9f7466f42e87L,
            0xbc781f647e5a3ecfL, 0x3fee9f75e8ec5f74L, 0xbc88e67a9006c909L, 0x3fee9f8286ead08aL,
            0xbc86ee4ac08b7db0L, 0x3fee9f9a48a58174L, 0x3c86597566977ac8L, 0x3fee9fbd35d7cbfdL,
            0xbc8619321e55e68aL, 0x3fee9feb564267c9L, 0x3c92c0b7028a5c3aL, 0x3feea024b1ab6e09L,
            0x3c909ccb5e09d4d3L, 0x3feea0694fde5d3fL, 0x3c8a30faf49cc78cL, 0x3feea0b938ac1cf6L,
            0xbc7b32dcb94da51dL, 0x3feea11473eb0187L, 0xbc92dad3519d7b5bL, 0x3feea17b0976cfdbL,
            0x3c94ecfd5467c06bL, 0x3feea1ed0130c132L, 0x3c87d51410fd15c2L, 0x3feea26a62ff86f0L,
            0x3c65ebe1abd66c55L, 0x3feea2f336cf4e62L, 0xbc760a3629969871L, 0x3feea3878491c491L,
            0xbc88a1c52fb3cf42L, 0x3feea427543e1a12L, 0x3c8b18c6e3fdef5dL, 0x3feea4d2add106d9L,
            0xbc9369b6f13b3734L, 0x3feea589994cce13L, 0x3c90ec1ddcb1390aL, 0x3feea64c1eb941f7L,
            0xbc805e843a19ff1eL, 0x3feea71a4623c7adL, 0xbc522cea4f3afa1eL, 0x3feea7f4179f5b21L,
            0xbc94d450d872576eL, 0x3feea8d99b4492edL, 0x3c7c88549b958471L, 0x3feea9cad931a436L,
            0x3c90ad675b0e8a00L, 0x3feeaac7d98a6699L, 0x3c931143962f7877L, 0x3feeabd0a478580fL,
            0x3c8db72fc1f0eab4L, 0x3feeace5422aa0dbL, 0x3c93e9e96f112479L, 0x3feeae05bad61778L,
            0xbc65b6609cc5e7ffL, 0x3feeaf3216b5448cL, 0xbc8dac42a4a38df0L, 0x3feeb06a5e0866d9L,
            0x3c7bf68359f35f44L, 0x3feeb1ae99157736L, 0x3c8b99dd98b1ed84L, 0x3feeb2fed0282c8aL,
            0xbc93091fa71e3d83L, 0x3feeb45b0b91ffc6L, 0xbc7885ad50cbb750L, 0x3feeb5c353aa2fe2L,
            0xbc5da9b88b6c1e29L, 0x3feeb737b0cdc5e5L, 0xbc82d5e85f3e0301L, 0x3feeb8b82b5f98e5L,
            0xbc6c23f97c90b959L, 0x3feeba44cbc8520fL, 0xbc51669428996971L, 0x3feebbdd9a7670b3L,
            0xbc92434322f4f9aaL, 0x3feebd829fde4e50L, 0x3c71f2b2c1c4c014L, 0x3feebf33e47a22a2L,
            0xbc85ca6cd7668e4bL, 0x3feec0f170ca07baL, 0xbc9294f304f166b6L, 0x3feec2bb4d53fe0dL,
            0x3c71affc2b91ce27L, 0x3feec49182a3f090L, 0xbc8a1e58414c07d3L, 0x3feec674194bb8d5L,
            0x3c6dd235e10a73bbL, 0x3feec86319e32323L, 0xbc79740b58a20091L, 0x3feeca5e8d07f29eL,
            0xbc87c50422622263L, 0x3feecc667b5de565L, 0x3c9165830a2b96c2L, 0x3feece7aed8eb8bbL,
            0x3c8b1c86e3e231d5L, 0x3feed09bec4a2d33L, 0xbc903d5cbe27874bL, 0x3feed2c980460ad8L,
            0xbc91bbd1d3bcbb15L, 0x3feed503b23e255dL, 0x3c5986178980fce0L, 0x3feed74a8af46052L,
            0x3c90cc319cee31d2L, 0x3feed99e1330b358L, 0xbc89472975b1f2a5L, 0x3feedbfe53c12e59L,
            0x3c8469846e735ab3L, 0x3feede6b5579fdbfL, 0x3c7d8157a34b7e7fL, 0x3feee0e521356ebaL,
            0xbc82dfcd978e9db4L, 0x3feee36bbfd3f37aL, 0x3c8c8a4e231ebb7dL, 0x3feee5ff3a3c2774L,
            0x3c8c1a7792cb3387L, 0x3feee89f995ad3adL, 0xbc888c8d11a142e5L, 0x3feeeb4ce622f2ffL,
            0xbc907b8f4ad1d9faL, 0x3feeee07298db666L, 0x3c889c2ea41433c7L, 0x3feef0ce6c9a8952L,
            0xbc55c3d956dcaebaL, 0x3feef3a2b84f15fbL, 0xbc7274aedac8ff80L, 0x3feef68415b749b1L,
            0xbc90a40e3da6f640L, 0x3feef9728de5593aL, 0x3c85c620ce76df06L, 0x3feefc6e29f1c52aL,
            0xbc68d6f438ad9334L, 0x3feeff76f2fb5e47L, 0xbc8fda52e1b51e41L, 0x3fef028cf22749e4L,
            0xbc91eee26b588a35L, 0x3fef05b030a1064aL, 0xbc32141a7b3e2cd8L, 0x3fef08e0b79a6f1fL,
            0x3c74ffd70a5fddcdL, 0x3fef0c1e904bc1d2L, 0xbc302899507554e5L, 0x3fef0f69c3f3a207L,
            0xbc91bdfbfa9298acL, 0x3fef12c25bd71e09L, 0xbc80dda2d4c0010cL, 0x3fef16286141b33dL,
            0x3c736eae30af0cb3L, 0x3fef199bdd85529cL, 0xbc8a007daadf8d68L, 0x3fef1d1cd9fa652cL,
            0x3c8ee3325c9ffd94L, 0x3fef20ab5fffd07aL, 0x3c836909391181d3L, 0x3fef244778fafb22L,
            0x3c84e08fd10959acL, 0x3fef27f12e57d14bL, 0xbc811cd7dbdf9547L, 0x3fef2ba88988c933L,
            0x3c63cdaf384e1a67L, 0x3fef2f6d9406e7b5L, 0xbc7ac28b7bef6621L, 0x3fef33405751c4dbL,
            0x3c676b2c6c921968L, 0x3fef3720dcef9069L, 0xbc7030587207b9e1L, 0x3fef3b0f2e6d1675L,
            0xbc808a1883ccb5d2L, 0x3fef3f0b555dc3faL, 0xbc8cc734592af7fcL, 0x3fef43155b5bab74L,
            0xbc8fad5d3ffffa6fL, 0x3fef472d4a07897cL, 0x3c87752a44f587e8L, 0x3fef4b532b08c968L,
            0xbc900dae3875a949L, 0x3fef4f87080d89f2L, 0x3c85b66fefeef52eL, 0x3fef53c8eacaa1d6L,
            0x3c74a385a63d07a7L, 0x3fef5818dcfba487L, 0x3c5159d9d908a96eL, 0x3fef5c76e862e6d3L,
            0xbc82919e2040220fL, 0x3fef60e316c98398L, 0x3c8c254d16117a68L, 0x3fef655d71ff6075L,
            0x3c8e5a50d5c192acL, 0x3fef69e603db3285L, 0xbc8d8c329fbd0e03L, 0x3fef6e7cd63a8315L,
            0x3c843a59ac016b4bL, 0x3fef7321f301b460L, 0xbc8ea6e6fbd5f2a6L, 0x3fef77d5641c0658L,
            0xbc82d52107b43e1fL, 0x3fef7c97337b9b5fL, 0xbc63e8e3eab2cbb4L, 0x3fef81676b197d17L,
            0xbc892ab93b470dc9L, 0x3fef864614f5a129L, 0xbc8b7966cd0d2cd9L, 0x3fef8b333b16ee12L,
            0x3c74b604603a88d3L, 0x3fef902ee78b3ff6L, 0xbc776caa4c2ff1cfL, 0x3fef953924676d76L,
            0x3c83c5ec519d7271L, 0x3fef9a51fbc74c83L, 0xbc81d5fc525d9940L, 0x3fef9f7977cdb740L,
            0xbc8ff7128fd391f0L, 0x3fefa4afa2a490daL, 0x3c855cd8aaea3d21L, 0x3fefa9f4867cca6eL,
            0xbc8dae98e223747dL, 0x3fefaf482d8e67f1L, 0x3c8269947c2bed4aL, 0x3fefb4aaa2188510L,
            0x3c8ec3bc41aa2008L, 0x3fefba1bee615a27L, 0xbc83b6137e9afe9eL, 0x3fefbf9c1cb6412aL,
            0x3c842b94c3a9eb32L, 0x3fefc52b376bba97L, 0xbc69fa74878ba7c7L, 0x3fefcac948dd7274L,
            0x3c8a64a931d185eeL, 0x3fefd0765b6e4540L, 0x3c901f3a75ee0efeL, 0x3fefd632798844f8L,
            0xbc8e37bae43be3edL, 0x3fefdbfdad9cbe14L, 0xbc516a9ce6ed84faL, 0x3fefe1d802243c89L,
            0x3c77893b4d91cd9dL, 0x3fefe7c1819e90d8L, 0xbc699c7db2effc76L, 0x3fefedba3692d514L,
            0x3c5305c14160cc89L, 0x3feff3c22b8f71f1L, 0x3c64b458677f9840L, 0x3feff9d96b2a23d9L,
    };
    private static final long[] EXP_T1 = {
            0x3ff0000000000000L, 0x3ff02c9a30000000L, 0x3ff059b0d0000000L, 0x3ff0874510000000L,
            0x3ff0b55860000000L, 0x3ff0e3ec30000000L, 0x3ff11301d0000000L, 0x3ff1429aa0000000L,
            0x3ff172b830000000L, 0x3ff1a35be0000000L, 0x3ff1d48730000000L, 0x3ff2063b80000000L,
            0x3ff2387a60000000L, 0x3ff26b4560000000L, 0x3ff29e9df0000000L, 0x3ff2d285a0000000L,
            0x3ff306fe00000000L, 0x3ff33c08b0000000L, 0x3ff371a730000000L, 0x3ff3a7db30000000L,
            0x3ff3dea640000000L, 0x3ff4160a20000000L, 0x3ff44e0860000000L, 0x3ff486a2b0000000L,
            0x3ff4bfdad0000000L, 0x3ff4f9b270000000L, 0x3ff5342b50000000L, 0x3ff56f4730000000L,
            0x3ff5ab07d0000000L, 0x3ff5e76f10000000L, 0x3ff6247eb0000000L, 0x3ff6623880000000L,
            0x3ff6a09e60000000L, 0x3ff6dfb230000000L, 0x3ff71f75e0000000L, 0x3ff75feb50000000L,
            0x3ff7a11470000000L, 0x3ff7e2f330000000L, 0x3ff8258990000000L, 0x3ff868d990000000L,
            0x3ff8ace540000000L, 0x3ff8f1ae90000000L, 0x3ff93737b0000000L, 0x3ff97d8290000000L,
            0x3ff9c49180000000L, 0x3ffa0c6670000000L, 0x3ffa5503b0000000L, 0x3ffa9e6b50000000L,
            0x3ffae89f90000000L, 0x3ffb33a2b0000000L, 0x3ffb7f76f0000000L, 0x3ffbcc1e90000000L,
            0x3ffc199bd0000000L, 0x3ffc67f120000000L, 0x3ffcb720d0000000L, 0x3ffd072d40000000L,
            0x3ffd5818d0000000L, 0x3ffda9e600000000L, 0x3ffdfc9730000000L, 0x3ffe502ee0000000L,
            0x3ffea4afa0000000L, 0x3ffefa1be0000000L, 0x3fff507650000000L, 0x3fffa7c180000000L,
    };
    private static final long[] EXP_T2 = {
            0x0000000000000000L, 0x3e6cef00c1dcdef9L, 0x3e48ac2ba1d73e2aL, 0x3e60eb37901186beL,
            0x3e69f3121ec53172L, 0x3e469e8d10103a17L, 0x3df25b50a4ebbf1aL, 0x3e6d525bbf668203L,
            0x3e68faa2f5b9bef9L, 0x3e66df96ea796d31L, 0x3e368b9aa7805b80L, 0x3e60c519ac771dd6L,
            0x3e6ceac470cd83f5L, 0x3e5789f37495e99cL, 0x3e547f7b84b09745L, 0x3e5b900c2d002475L,
            0x3e64636e2a5bd1abL, 0x3e4320b7fa64e430L, 0x3e5ceaa72a9c5154L, 0x3e53967fdba86f24L,
            0x3e682468446b6824L, 0x3e3f72e29f84325bL, 0x3e18624b40c4dbd0L, 0x3e5704f3404f068eL,
            0x3e54d8a89c750e5eL, 0x3e5a74b29ab4cf62L, 0x3e5a753e077c2a0fL, 0x3e5ad49f699bb2c0L,
            0x3e6a90a852b19260L, 0x3e56b48521ba6f93L, 0x3e0d2ac258f87d03L, 0x3e42a91124893ecfL,
            0x3e59fcef32422cbeL, 0x3e68ca345de441c5L, 0x3e61d8bee7ba46e1L, 0x3e59099f22fdba6aL,
            0x3e4f580c36bea881L, 0x3e5b3d398841740aL, 0x3e62999c25159f11L, 0x3e668925d901c83bL,
            0x3e415506dadd3e2aL, 0x3e622aee6c57304eL, 0x3e29b8bc9e8a0387L, 0x3e6fbc9c9f173d24L,
            0x3e451f8480e3e235L, 0x3e66bbcac96535b5L, 0x3e41f12ae45a1224L, 0x3e55e7f6fd0fac90L,
            0x3e62b5a75abd0e69L, 0x3e609e2bf5ed7fa1L, 0x3e47daf237553d84L, 0x3e12f074891ee83dL,
            0x3e6b0aa538444196L, 0x3e6cafa29694426fL, 0x3e69df20d22a0797L, 0x3e640f12f71a1e45L,
            0x3e69f7490e4bb40bL, 0x3e4ed9942b84600dL, 0x3e4bdcdaf5cb4656L, 0x3e5e2cffd89cf44cL,
            0x3e452486cc2c7b9dL, 0x3e6cc2b44eee3fa4L, 0x3e66dc8a80ce9f09L, 0x3e39e90d82e90a7eL,
    };
    private static final long[] EXP_T3 = {
            0x3ff0000000000000L, 0x3ff02c9a3e778061L, 0x3ff059b0d3158574L, 0x3ff0874518759bc8L,
            0x3ff0b5586cf9890fL, 0x3ff0e3ec32d3d1a2L, 0x3ff11301d0125b51L, 0x3ff1429aaea92de0L,
            0x3ff172b83c7d517bL, 0x3ff1a35beb6fcb75L, 0x3ff1d4873168b9aaL, 0x3ff2063b88628cd6L,
            0x3ff2387a6e756238L, 0x3ff26b4565e27cddL, 0x3ff29e9df51fdee1L, 0x3ff2d285a6e4030bL,
            0x3ff306fe0a31b715L, 0x3ff33c08b26416ffL, 0x3ff371a7373aa9cbL, 0x3ff3a7db34e59ff7L,
            0x3ff3dea64c123422L, 0x3ff4160a21f72e2aL, 0x3ff44e086061892dL, 0x3ff486a2b5c13cd0L,
            0x3ff4bfdad5362a27L, 0x3ff4f9b2769d2ca7L, 0x3ff5342b569d4f82L, 0x3ff56f4736b527daL,
            0x3ff5ab07dd485429L, 0x3ff5e76f15ad2148L, 0x3ff6247eb03a5585L, 0x3ff6623882552225L,
            0x3ff6a09e667f3bcdL, 0x3ff6dfb23c651a2fL, 0x3ff71f75e8ec5f74L, 0x3ff75feb564267c9L,
            0x3ff7a11473eb0187L, 0x3ff7e2f336cf4e62L, 0x3ff82589994cce13L, 0x3ff868d99b4492edL,
            0x3ff8ace5422aa0dbL, 0x3ff8f1ae99157736L, 0x3ff93737b0cdc5e5L, 0x3ff97d829fde4e50L,
            0x3ff9c49182a3f090L, 0x3ffa0c667b5de565L, 0x3ffa5503b23e255dL, 0x3ffa9e6b5579fdbfL,
            0x3ffae89f995ad3adL, 0x3ffb33a2b84f15fbL, 0x3ffb7f76f2fb5e47L, 0x3ffbcc1e904bc1d2L,
            0x3ffc199bdd85529cL, 0x3ffc67f12e57d14bL, 0x3ffcb720dcef9069L, 0x3ffd072d4a07897cL,
            0x3ffd5818dcfba487L, 0x3ffda9e603db3285L, 0x3ffdfc97337b9b5fL, 0x3ffe502ee78b3ff6L,
            0x3ffea4afa2a490daL, 0x3ffefa1bee615a27L, 0x3fff50765b6e4540L, 0x3fffa7c1819e90d8L,
    };
}
