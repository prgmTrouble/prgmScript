package prgmScript.ast;

import prgmScript.Type;
import prgmScript.util.ErrReporter;

public abstract class Expr
{
    @FunctionalInterface protected interface GetValue {Object value(final Scope<Value> scope);}
    
    public final int line;
    public final Type type;
    
    public Expr(final int line,final Type type)
    {
        this.line = line;
        this.type = type;
    }
    
    protected Literal literal(final GetValue getter,final Scope<Value> scope)
    {
        return new Literal(line,type)
        {
            @Override public Object value() {return getter.value(scope);}
        };
    }
    protected abstract Literal eval(final Scope<Value> scope);
    public Literal evaluate(final Scope<Value> scope)
    {
        try {return eval(scope);}
        catch(final prgmException e)
        {
            System.err.println(e.origin? e : ("["+line+"]"));
            throw new prgmException();
        }
        catch(final Exception e)
        {
            System.err.println(ErrReporter.format(line,e.toString()));
            throw new prgmException();
        }
    }
}