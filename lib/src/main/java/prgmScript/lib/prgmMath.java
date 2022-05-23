package prgmScript.lib;

import prgmScript.ModuleMaker;
import prgmScript.Types;
import prgmScript.Value;

import java.util.function.Function;

import static prgmScript.ModuleMaker.*;

/** Common math functions. */
@SuppressWarnings("unused")
public final class prgmMath
{
    private prgmMath() {}
    
    private static final double LG2 = Math.log(2);
    
    static
    {
        @SuppressWarnings("unchecked")
        final Function<Value[],Object>[] d_d  = new Function[]
        {
            (Function<Value[],Object>)a -> Math.abs((double)a[0].getValue()),
            (Function<Value[],Object>)a -> StrictMath.acos((double)a[0].getValue()),
            (Function<Value[],Object>)a -> StrictMath.asin((double)a[0].getValue()),
            (Function<Value[],Object>)a -> StrictMath.atan((double)a[0].getValue()),
            (Function<Value[],Object>)a -> StrictMath.cbrt((double)a[0].getValue()),
            (Function<Value[],Object>)a -> StrictMath.ceil((double)a[0].getValue()),
            (Function<Value[],Object>)a -> StrictMath.cos((double)a[0].getValue()),
            (Function<Value[],Object>)a -> StrictMath.cosh((double)a[0].getValue()),
            (Function<Value[],Object>)a -> StrictMath.exp((double)a[0].getValue()),
            (Function<Value[],Object>)a -> StrictMath.expm1((double)a[0].getValue()),
            (Function<Value[],Object>)a -> StrictMath.floor((double)a[0].getValue()),
            (Function<Value[],Object>)a -> StrictMath.log((double)a[0].getValue()),
            (Function<Value[],Object>)a -> StrictMath.log((double)a[0].getValue())/LG2,
            (Function<Value[],Object>)a -> StrictMath.log10((double)a[0].getValue()),
            (Function<Value[],Object>)a -> StrictMath.log1p((double)a[0].getValue()),
            (Function<Value[],Object>)a -> Math.nextDown((double)a[0].getValue()),
            (Function<Value[],Object>)a -> Math.nextUp((double)a[0].getValue()),
            (Function<Value[],Object>)a -> StrictMath.rint((double)a[0].getValue()),
            (Function<Value[],Object>)a -> StrictMath.signum((double)a[0].getValue()),
            (Function<Value[],Object>)a -> StrictMath.sin((double)a[0].getValue()),
            (Function<Value[],Object>)a -> StrictMath.sinh((double)a[0].getValue()),
            (Function<Value[],Object>)a -> StrictMath.sqrt((double)a[0].getValue()),
            (Function<Value[],Object>)a -> StrictMath.tan((double)a[0].getValue()),
            (Function<Value[],Object>)a -> StrictMath.tanh((double)a[0].getValue()),
            (Function<Value[],Object>)a -> StrictMath.toDegrees((double)a[0].getValue()),
            (Function<Value[],Object>)a -> StrictMath.toRadians((double)a[0].getValue()),
            (Function<Value[],Object>)a -> Math.ulp((double)a[0].getValue())
        },
                                          d_dd = new Function[]
        {
            (Function<Value[],Object>)a -> StrictMath.hypot((double)a[0].getValue(),(double)a[1].getValue()),
            (Function<Value[],Object>)a -> StrictMath.log((double)a[0].getValue())/StrictMath.log((double)a[1].getValue()),
            (Function<Value[],Object>)a -> Math.max((double)a[0].getValue(),(double)a[1].getValue()),
            (Function<Value[],Object>)a -> Math.min((double)a[0].getValue(),(double)a[1].getValue()),
            (Function<Value[],Object>)a -> Math.nextAfter((double)a[0].getValue(),(double)a[1].getValue()),
            (Function<Value[],Object>)a -> StrictMath.pow((double)a[0].getValue(),(double)a[1].getValue())
        },
                                          i_ii = new Function[]
        {
            (Function<Value[],Object>)a -> Math.max((long)a[0].getValue(),(long)a[1].getValue()),
            (Function<Value[],Object>)a -> Math.min((long)a[0].getValue(),(long)a[1].getValue())
        };
        final Function<Value[],Object> absInt = a -> Math.abs((long)a[0].getValue()),
                                       fma    = a -> Math.fma((double)a[0].getValue(),(double)a[1].getValue(),(double)a[2].getValue()),
                                       scalb  = a -> Math.scalb((double)a[0].getValue(),(int)(long)a[1].getValue());
        final FuncInitializer[] d  = createFuncInitializers(d_d ,Types.FLOAT,Types.CONST_FLOAT),
                                dd = createFuncInitializers(d_dd,Types.FLOAT,Types.CONST_FLOAT,Types.CONST_FLOAT),
                                ii = createFuncInitializers(i_ii,Types.INT  ,Types.CONST_INT,  Types.CONST_INT);
        final FuncInitializer absIntInit = createFuncInitializer(absInt,Types.INT  ,Types.CONST_INT),
                              fmaInit    = createFuncInitializer(fma   ,Types.FLOAT,Types.CONST_FLOAT,Types.CONST_FLOAT,Types.CONST_FLOAT),
                              scalbInit  = createFuncInitializer(scalb ,Types.FLOAT,Types.CONST_FLOAT,Types.CONST_INT);
        final String[] nd_d  = new String[] {"abs","acos","asin","atan","cbrt","ceil","cos","cosh","exp","expm1","floor","log","log2",
                                             "log10","log1p","nextDown","nextUp","rint","signum","sin","sinh","sqrt","tan","tanh",
                                             "toDegrees","toRadians","ulp"},
                       nd_dd = new String[] {"hypot","logb","max","min","nextAfter","pow"},
                       ni_ii = new String[] {"maxInt","minInt"};
    
        final ModuleMaker mm = new ModuleMaker();
        for(int i = 0;i < d.length;++i)
            mm.declareValue(nd_d[i],createFunc(d[i],true));
        for(int i = 0;i < dd.length;++i)
            mm.declareValue(nd_dd[i],createFunc(dd[i],true));
        for(int i = 0;i < ii.length;++i)
            mm.declareValue(ni_ii[i],createFunc(ii[i],true));
        mm.declareValue("absInt",createFunc(absIntInit,true))
          .declareValue("fma",createFunc(fmaInit,true))
          .declareValue("scalb",createFunc(scalbInit,true))
        
          .declareValue("M_e",createFloat(StrictMath.E,true))
          .declareValue("M_pi",createFloat(StrictMath.PI,true))
        
          .make().register("prgmMath");
    }
}