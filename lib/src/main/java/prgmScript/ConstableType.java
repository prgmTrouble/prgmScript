package prgmScript;

import java.util.Objects;

/** A class which contains a {@linkplain Type} and whether it is const. */
@SuppressWarnings("ClassCanBeRecord")
public final class ConstableType
{
    public final Type type;
    public final boolean isConst;
    
    ConstableType(final Type type,final boolean isConst) {this.type = type; this.isConst = isConst;}
    
    @Override public String toString() {return (isConst? "const ":"")+type;}
    @Override public int hashCode() {return Objects.hash(type,isConst);}
    @Override
    public boolean equals(final Object o)
    {
        final Type ot;
        if(o instanceof final Type t) ot = t;
        else if(o instanceof final ConstableType t) ot = t.type;
        else return false;
        return type.equals(ot);
    }
    
    /** A ConstableType object representing a primitive. */
    static final ConstableType BOOL        = new ConstableType(Type.BOOL ,false),
                               INT         = new ConstableType(Type.INT  ,false),
                               FLOAT       = new ConstableType(Type.FLOAT,false),
                               STR         = new ConstableType(Type.STR  ,false),
                           
                               VOID        = new ConstableType(Type.VOID , true),
                               CONST_BOOL  = new ConstableType(Type.BOOL , true),
                               CONST_INT   = new ConstableType(Type.INT  , true),
                               CONST_FLOAT = new ConstableType(Type.FLOAT, true),
                               CONST_STR   = new ConstableType(Type.STR  , true);
}