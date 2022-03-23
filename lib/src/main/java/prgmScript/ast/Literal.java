package prgmScript.ast;

import prgmScript.Type;

public abstract class Literal extends Expr
{
    public Literal(final int line,final Type type) {super(line,type);}
    
    @Override public Literal eval(final Scope<Value> scope) {return this;}
    public abstract Object value();
    public boolean conditional() {return type.simple().conditional(value());}
    
    @Override
    public boolean equals(final Object obj)
    {
        return obj == this || (obj instanceof Literal l? l.value().equals(value()) : value().equals(obj));
    }
}