package prgmScript.api;

import prgmScript.Primitives;
import prgmScript.Script;

final class prgmOutput
{
    static void register()
    {
        new ModuleMaker().
        addFunction
        (
            true,"printOut",a -> {System.out.print(Script.toString(a[0])); return null;},
            Primitives.VOID.type,Primitives.STR.type
        ).
        addFunction
        (
            true,"printErr",a -> {System.err.print(Script.toString(a[0])); return null;},
            Primitives.VOID.type,Primitives.STR.type
        ).
        
        buildNoReturn("prgmOutput");
    }
}