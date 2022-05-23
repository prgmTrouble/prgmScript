package prgmScript.lib;

import prgmScript.*;
import prgmScript.util.XorShift;

import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.random.RandomGenerator;

import static prgmScript.ModuleMaker.*;

/** Random number generators. */
@SuppressWarnings("unused")
public final class prgmRandom
{
    private prgmRandom() {}
    
    private static Map.Entry<String,ConstableType> entry(final String k,final Type rt,final ConstableType...args)
    {
        return Map.entry(k,Types.constableType(Types.funcType(rt,args),true));
    }
    static
    {
        final Map<String,ConstableType> struct = Map.ofEntries
        (
            Map.entry("seed",Types.CONST_INT),
            Map.entry("isStrong",Types.CONST_BOOL),
            entry("setSeed",Types.VOID,Types.CONST_INT),
    
            entry("nextBool",Types.BOOL),
            
            entry("nextI32",Types.INT),
            entry("nextI64",Types.INT),
            entry("nextBoundedInt",Types.INT,Types.CONST_INT,Types.CONST_INT),
            
            entry("nextF32",Types.FLOAT),
            entry("nextF64",Types.FLOAT),
            entry("nextBoundedFloat",Types.FLOAT,Types.CONST_FLOAT,Types.CONST_FLOAT),
            
            entry("nextGaussian",Types.FLOAT),
            entry("nextExponential",Types.FLOAT)
        );
        final Function<Function<Value[],Object>,Value> nextB = funcCreator(true,Types.BOOL),
                                                       nextI = funcCreator(true,Types.INT),
                                                       nextF = funcCreator(true,Types.FLOAT),
        
                                                       nextBI = funcCreator(true,Types.INT,Types.CONST_INT,Types.CONST_INT),
                                                       nextBF = funcCreator(true,Types.FLOAT,Types.CONST_FLOAT,Types.CONST_FLOAT);
        final Function<Value[],Object> f = a ->
        {
            final long seed = (long)a[0].getValue();
            final boolean isStrong = (boolean)a[1].getValue();
            final RandomGenerator rand =
                seed == 0L
                    ? (isStrong? new XorShift(    ) : new Random(    ))
                    : (isStrong? new XorShift(seed) : new Random(seed));
            return Map.ofEntries
            (
                Map.entry("seed",createInt(seed,true)),
                Map.entry("isStrong",createBool(isStrong,true)),
                
                Map.entry("nextBool",nextB.apply(b -> rand.nextBoolean())),
                
                Map.entry("nextI32",nextI.apply(b -> (long)rand.nextInt())),
                Map.entry("nextI64",nextI.apply(b -> rand.nextLong())),
                Map.entry("nextBoundedInt",nextBI.apply(b -> rand.nextLong((long)b[0].getValue(),(long)b[1].getValue()))),
                
                Map.entry("nextF32",nextF.apply(b -> (double)rand.nextFloat())),
                Map.entry("nextF64",nextF.apply(b -> rand.nextDouble())),
                Map.entry("nextBoundedFloat",nextBF.apply(b -> rand.nextDouble((double)b[0].getValue(),(double)b[1].getValue()))),
                
                Map.entry("nextGaussian",nextF.apply(b -> rand.nextGaussian())),
                Map.entry("nextExponential",nextF.apply(b -> rand.nextExponential()))
            );
        };
        new ModuleMaker().
            declareStructType("Random",struct).
            declareFunc("newRandom",true,f,Types.structType("Random"),Types.CONST_INT,Types.CONST_BOOL).
            make().register("prgmRandom");
    }
}