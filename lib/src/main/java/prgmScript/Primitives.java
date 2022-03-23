package prgmScript;

import prgmScript.ast.BaseType;

public enum Primitives
{
    VOID (BaseType.VOID),
    BOOL (BaseType.BOOL),
    INT  (BaseType.INT),
    FLOAT(BaseType.FLOAT),
    STR  (BaseType.STR);
    
    public final Type type;
    
    Primitives(final BaseType t) {type = new Type(false,t,null,null);}
}