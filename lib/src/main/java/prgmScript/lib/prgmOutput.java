package prgmScript.lib;

import prgmScript.ConstableType;
import prgmScript.ModuleMaker;
import prgmScript.Types;
import prgmScript.Value;

import java.util.Map;
import java.util.function.Function;

import static prgmScript.ModuleMaker.FuncInitializer;
import static prgmScript.ModuleMaker.createFuncInitializers;
import static java.lang.System.err;
import static java.lang.System.out;

/** Standard output streams. */
@SuppressWarnings("unused")
public final class prgmOutput
{
    private prgmOutput() {}
    
    static
    {
        @SuppressWarnings("unchecked")
        final Function<Value[],Object>[] f = new Function[]
        {
            (Function<Value[],Object>)a -> {out.print  ((String)a[0].getValue()); return null;},
            (Function<Value[],Object>)a -> {out.println((String)a[0].getValue()); return null;},
            (Function<Value[],Object>)a -> {err.print  ((String)a[0].getValue()); return null;},
            (Function<Value[],Object>)a -> {err.println((String)a[0].getValue()); return null;}
        };
        final FuncInitializer[] init = createFuncInitializers(f,Types.VOID,Types.CONST_STR);
        final ConstableType ft = Types.constableType(Types.funcType(Types.VOID,Types.CONST_STR),true);
        new ModuleMaker().
            declareStructType("PrintStream",Map.of("print",ft,"println",ft)).
            declareStructValue("sysout","PrintStream",Map.of("print",init[0],"println",init[1]),true).
            declareStructValue("syserr","PrintStream",Map.of("print",init[2],"println",init[3]),true).
            make().register("prgmOutput");
    }
}