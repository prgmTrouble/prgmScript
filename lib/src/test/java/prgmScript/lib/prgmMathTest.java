package prgmScript.lib;

import org.junit.Test;
import prgmScript.Module;
import prgmScript.Script;
import prgmScript.exception.ScriptException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class prgmMathTest
{
    private static final File DIR = Path.of
    (
        System.getProperty("user.dir"),
        "src","test","java","prgmScript","lib","MathTest.prgm"
    ).toFile();
    @Test
    public void testMath() throws IOException,ScriptException
    {
        final Module m;
        try(final FileReader fr = new FileReader(DIR)) {assertNotNull(m = Script.run(fr,"MathTest",System.err));}
        final String[] nd_d = {"abs","acos","asin","atan","cbrt","ceil","cos","cosh","exp","expm1","floor","log","log2",
                               "log10","log1p","nextDown","nextUp","rint","signum","sin","sinh","sqrt","tan","tanh",
                               "toDegrees","toRadians","ulp"},
                      nd_dd = {"hypot","logb","max","min","nextAfter","pow"},
                      ni_ii = {"maxInt","minInt"};
        @SuppressWarnings("unchecked")
        final Function<Double,Double>[] d_d = new Function[]
        {
            (Function<Double,Double>)Math::abs,
            (Function<Double,Double>)StrictMath::acos,
            (Function<Double,Double>)StrictMath::asin,
            (Function<Double,Double>)StrictMath::atan,
            (Function<Double,Double>)StrictMath::cbrt,
            (Function<Double,Double>)StrictMath::ceil,
            (Function<Double,Double>)StrictMath::cos,
            (Function<Double,Double>)StrictMath::cosh,
            (Function<Double,Double>)StrictMath::exp,
            (Function<Double,Double>)StrictMath::expm1,
            (Function<Double,Double>)StrictMath::floor,
            (Function<Double,Double>)StrictMath::log,
            (Function<Double,Double>)d -> StrictMath.log(d)/StrictMath.log(2),
            (Function<Double,Double>)StrictMath::log10,
            (Function<Double,Double>)StrictMath::log1p,
            (Function<Double,Double>)Math::nextDown,
            (Function<Double,Double>)Math::nextUp,
            (Function<Double,Double>)StrictMath::rint,
            (Function<Double,Double>)StrictMath::signum,
            (Function<Double,Double>)StrictMath::sin,
            (Function<Double,Double>)StrictMath::sinh,
            (Function<Double,Double>)StrictMath::sqrt,
            (Function<Double,Double>)StrictMath::tan,
            (Function<Double,Double>)StrictMath::tanh,
            (Function<Double,Double>)StrictMath::toDegrees,
            (Function<Double,Double>)StrictMath::toRadians,
            (Function<Double,Double>)Math::ulp
        };
        @SuppressWarnings("unchecked")
        final BiFunction<Double,Double,Double>[] d_dd = new BiFunction[]
        {
            (BiFunction<Double,Double,Double>)StrictMath::hypot,
            (BiFunction<Double,Double,Double>)(a,b) -> StrictMath.log(a)/StrictMath.log(b),
            (BiFunction<Double,Double,Double>)Math::max,
            (BiFunction<Double,Double,Double>)Math::min,
            (BiFunction<Double,Double,Double>)Math::nextAfter,
            (BiFunction<Double,Double,Double>)StrictMath::pow
        };
        @SuppressWarnings("unchecked")
        final BiFunction<Integer,Integer,Integer>[] i_ii = new BiFunction[]
        {
            (BiFunction<Integer,Integer,Integer>)Math::max,
            (BiFunction<Integer,Integer,Integer>)Math::min
        };
        for(int i = 0;i < nd_d.length;++i)
            assertEquals(d_d[i].apply(.5),(double)m.getValue("_"+nd_d[i]).getValue(),1e-10);
        for(int i = 0;i < nd_dd.length;++i)
            assertEquals(d_dd[i].apply(.5,.33),(double)m.getValue("_"+nd_dd[i]).getValue(),1e-10);
        for(int i = 0;i < ni_ii.length;++i)
            assertEquals(i_ii[i].apply(1,2),(long)m.getValue("_"+ni_ii[i]).getValue(),1e-10);
        assertEquals(Math.abs(1L),(long)m.getValue("_absInt").getValue(),1e-10);
        assertEquals(Math.fma(.5,.33,.75),(double)m.getValue("_fma").getValue(),1e-10);
        assertEquals(StrictMath.scalb(.5,2),(double)m.getValue("_scalb").getValue(),1e-10);
    }
}