package prgmScript.api;

import static prgmScript.Primitives.FLOAT;
import static prgmScript.Primitives.INT;

final class prgmMath
{
    private static final double LG2 = Math.log(2);
    @FunctionalInterface private interface F {double f(double a);}
    private static void addFunc(final ModuleMaker mm,final F f,final String n)
    {
        mm.
        addFunction
        (
            true,n,a -> f.f((Double)a[0].value()),
            FLOAT.type,FLOAT.type
        );
    }
    @FunctionalInterface private interface G {double g(double a,double b);}
    private static void addFunc(final ModuleMaker mm,final G g,final String n)
    {
        mm.
        addFunction
        (
            true,n,a -> g.g((Double)a[0].value(),(Double)a[1].value()),
            FLOAT.type,FLOAT.type,FLOAT.type
        );
    }
    private static double log2(double x) {return Math.log(x)/LG2;}
    private static double logb(double x,double b) {return Math.log(x)/Math.log(b);}
    
    static void register()
    {
        final ModuleMaker mm = new ModuleMaker();
        
        addFunc(mm,    Math::      abs,"abs"      );
        addFunc(mm,    Math::     acos,"acos"     );
        addFunc(mm,    Math::     asin,"asin"     );
        addFunc(mm,    Math::     atan,"atan"     );
        addFunc(mm,    Math::     cbrt,"cbrt"     );
        addFunc(mm,    Math::     ceil,"ceil"     );
        addFunc(mm,    Math::      cos,"cos"      );
        addFunc(mm,    Math::     cosh,"cosh"     );
        addFunc(mm,    Math::      exp,"exp"      );
        addFunc(mm,    Math::    expm1,"expm1"    );
        addFunc(mm,    Math::    floor,"floor"    );
        addFunc(mm,    Math::    hypot,"hypot"    );
        addFunc(mm,    Math::      log,"log"      );
        addFunc(mm,prgmMath::     log2,"log2"     );
        addFunc(mm,    Math::    log10,"log10"    );
        addFunc(mm,    Math::    log1p,"log1p"    );
        addFunc(mm,prgmMath::     logb,"logb"     );
        addFunc(mm,    Math::      max,"max"      );
        addFunc(mm,    Math::      min,"min"      );
        addFunc(mm,    Math::nextAfter,"nextAfter");
        addFunc(mm,    Math:: nextDown,"nextDown" );
        addFunc(mm,    Math::   nextUp,"nextUp"   );
        addFunc(mm,    Math::      pow,"pow"      );
        addFunc(mm,    Math::     rint,"rint"     );
        addFunc(mm,    Math::   signum,"signum"   );
        addFunc(mm,    Math::      sin,"sin"      );
        addFunc(mm,    Math::     sinh,"sinh"     );
        addFunc(mm,    Math::     sqrt,"sqrt"     );
        addFunc(mm,    Math::      tan,"tan"      );
        addFunc(mm,    Math::     tanh,"tanh"     );
        addFunc(mm,    Math::toDegrees,"toDegrees");
        addFunc(mm,    Math::toRadians,"toRadians");
        addFunc(mm,    Math::      ulp,"ulp"      );
        
        mm.
        addFunction
        (
            true,"fma",a -> Math.fma((Double)a[0].value(),(Double)a[1].value(),(Double)a[2].value()),
            FLOAT.type,FLOAT.type,FLOAT.type,FLOAT.type
        ).
        addFunction
        (
            true,"scalb",a -> Math.scalb((Double)a[0].value(),((Long)a[1].value()).intValue()),
            FLOAT.type,FLOAT.type,INT.type
        ).
        addFunction
        (
            true,"minInt",a -> Math.min((Long)a[0].value(),(Long)a[1].value()),
            INT.type,INT.type,INT.type
        ).
        addFunction
        (
            true,"maxInt",a -> Math.max((Long)a[0].value(),(Long)a[1].value()),
            INT.type,INT.type,INT.type
        ).
        
        addPrimitiveValue(true,"M_e" ,Math. E,FLOAT).
        addPrimitiveValue(true,"M_pi",Math.PI,FLOAT).
    
        buildNoReturn("prgmMath");
    }
}