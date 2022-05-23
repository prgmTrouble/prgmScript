package prgmScript.util;

import java.util.concurrent.atomic.AtomicLong;
import java.util.random.RandomGenerator;

/** An implementation of the xoroshiro128++ PRNG algorithm, see http://prng.di.unimi.it/xoroshiro128plusplus.c */
public final class XorShift implements RandomGenerator
{
    /**
     * Stafford variant 13
     *
     * http://zimbry.blogspot.com/2011/09/better-bit-mixing-improving-on.html
     */
    private static long stafford13(long l)
    {
        l =   (l ^ (l >>> (byte)30)) * 0xBF58476D1CE4E5B9L;
        l =   (l ^ (l >>> (byte)27)) * 0x94D049BB133111EBL;
        return l ^ (l >>> (byte)31);
    }
    private static final AtomicLong seed = new AtomicLong(stafford13(System.currentTimeMillis())^stafford13(System.nanoTime()));
    
    private long s0,s1;
    
    public XorShift(final long seed) {setSeed(seed);}
    public XorShift() {setSeed(seed.getAndAdd(0x9E3779B97F4A7C15L));}
    
    public synchronized void setSeed(long seed)
    {
        s0 = stafford13(seed ^= 0x6A09E667F3BCC909L); // First 64 bits of 1+sqrt(2), forced odd.
        s1 = stafford13(seed +  0x9E3779B97F4A7C15L); // First 64 bits of (1+sqrt(5))/2, forced odd.
    }
    
    @Override
    public long nextLong()
    {
        final long s = s0;
        long t = s1;
        final long r = Long.rotateLeft(s+t,17)+s;
        t ^= s;
        s0 = Long.rotateLeft(s,49)^t^(t<<21);
        s1 = Long.rotateLeft(s,28);
        return r;
    }
}