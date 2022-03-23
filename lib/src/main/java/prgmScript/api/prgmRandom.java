package prgmScript.api;

import prgmScript.Type;
import prgmScript.ast.Func;
import prgmScript.ast.Literal;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Random;

import static prgmScript.Primitives.*;
import static prgmScript.api.ModuleMaker.*;

final class prgmRandom
{
    private prgmRandom() {}
    
    private static final Type seedType = makePrimitiveType(true,INT),
                          isStrongType = makePrimitiveType(true,BOOL),
                          nextBoolType = makeFunctionType (true,BOOL.type),
                             nextIType = makeFunctionType (true,INT.type,INT.type,INT.type),
                             nextFType = makeFunctionType (true,FLOAT.type,FLOAT.type,FLOAT.type),
                           setSeedType = makeFunctionType (true,VOID.type,INT.type);
    static void register()
    {
        new ModuleMaker().
        declareStruct
        (
            "Random",
            Map.of
            (
                "seed"    ,seedType,
                "isStrong",isStrongType,
                "setSeed" ,setSeedType,
                
                "nextBool",nextBoolType,
                
                "nextI32" ,nextIType,
                "nextI64" ,nextIType,
                
                "nextF32" ,nextFType,
                "nextF64" ,nextFType
            )
        ).
        
        addFunction
        (
            true,"randomInstance",a ->
            {
                final Random rand;
                final boolean isStrong = (Boolean)a[0].value();
                if(isStrong)
                    try {rand = SecureRandom.getInstance("SHA1PRNG");}
                    catch(final NoSuchAlgorithmException e)
                    {
                        throw new RuntimeException
                        (
                            """
                            For whatever reason, the SHA1PRNG algorithm is not supported on
                            your system.
                            """
                        );
                    }
                else rand = new Random();
                final long seed = (Long)a[1].value();
                rand.setSeed(seed);
                return Map.of
                (
                    "seed"    ,new Literal(0,seedType) {@Override public Object value() {return seed;}},
                    "isStrong",new Literal(0,isStrongType) {@Override public Object value() {return isStrong;}},
                    "setSeed" ,new Func(0,setSeedType)
                    {
                        @Override
                        public Object call(final Literal...a)
                        {
                            rand.setSeed((Long)a[0].value());
                            return null;
                        }
                    },
                    
                    "nextBool",new Func(0,nextBoolType)
                    {
                        @Override public Object call(final Literal...a) {return rand.nextBoolean();}
                    },
                    
                    "nextI32" ,new Func(0,nextIType)
                    {
                        @Override
                        public Object call(final Literal...a)
                        {
                            return rand.nextInt(((Long)a[0].value()).intValue(),
                                                ((Long)a[1].value()).intValue());
                        }
                    },
                    "nextI64" ,new Func(0,nextIType)
                    {
                        @Override
                        public Object call(final Literal...a)
                        {
                            return rand.nextLong((Long)a[0].value(),
                                                 (Long)a[1].value());
                        }
                    },
                    
                    "nextF32" ,new Func(0,nextFType)
                    {
                        @Override
                        public Object call(final Literal...a)
                        {
                            return rand.nextFloat(((Double)a[0].value()).floatValue(),
                                                  ((Double)a[1].value()).floatValue());
                        }
                    },
                    "nextF64" ,new Func(0,nextFType)
                    {
                        @Override
                        public Object call(final Literal...a)
                        {
                            return rand.nextDouble((Double)a[0].value(),
                                                   (Double)a[1].value());
                        }
                    }
                );
            },
            makeStructType(false,"Random"),
            makePrimitiveType(false,BOOL),
            makePrimitiveType(false,INT)
        ).
        
        buildNoReturn("prgmRandom");
    }
}