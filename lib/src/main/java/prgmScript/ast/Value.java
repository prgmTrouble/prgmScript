package prgmScript.ast;

import prgmScript.Trace;
import prgmScript.Type;

public abstract class Value extends Expr
{
    private boolean init = false;
    
    public Value(final int line,final Type type) {super(line,type);}
    
    public void setValue(final Trace trace,final Scope<Value> scope,final Object value)
    {
        if(!init || !type.isConst())
        {
            init = true;
            setValue(scope,value);
        }
        else
            trace.report(line,"Cannot assign to const value");
    }
    protected abstract void setValue(final Scope<Value> scope,final Object value);
}