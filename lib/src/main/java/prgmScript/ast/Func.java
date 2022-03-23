package prgmScript.ast;

import prgmScript.Type;
import prgmScript.util.ErrReporter;

public abstract class Func extends Literal
{
    public Func(final int line,final Type type) {super(line,type);}
    
    public abstract Object call(final Literal...args);
    @Override public Object value() {return this;}
    
    @Override
    public Literal evaluate(final Scope<Value> scope)
    {
        try {return super.evaluate(scope);}
        catch(final RuntimeException e)
        {
            System.err.println(ErrReporter.format(line,type.toString()));
            throw e;
        }
    }
}