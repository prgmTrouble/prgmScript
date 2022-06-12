package experimental;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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
    
    /** A synchronized map containing all non-primitive non-const and const instances, respectively. */
    private static final Map<Type,ConstableType[]> STORAGE = Collections.synchronizedMap(new HashMap<>());
    
    /**
     * @return A {@linkplain ConstableType} representing the specified {@linkplain Type} and const-ness.
     *
     * @throws NullPointerException if {@code t} is {@code null}.
     */
    public static ConstableType of(final Type t,final boolean isConst)
    {
        if(t == null) throw new NullPointerException();
        return switch(t.base)
        {
            case VOID  -> VOID;
            case BOOL  -> isConst? CONST_BOOL  : BOOL;
            case INT   -> isConst? CONST_INT   : INT;
            case FLOAT -> isConst? CONST_FLOAT : FLOAT;
            case STR   -> isConst? CONST_STR   : STR;
            default    ->
            {
                ConstableType[] pair = STORAGE.get(t);
                if(pair == null) STORAGE.put(t,pair = new ConstableType[] {new ConstableType(t,false),new ConstableType(t,true)});
                yield pair[isConst? 1:0];
            }
        };
    }
}