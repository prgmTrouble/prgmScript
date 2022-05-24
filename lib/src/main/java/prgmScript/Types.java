package prgmScript;

import java.util.*;

/**
 * A utility class which contains functions for operating on types.
 *
 * @see Type
 * @see ConstableType
 */
public final class Types
{
    private Types() {}
    
    /*/
    The below functions and constants are responsible for managing types. Since types are used everywhere in the
    code, having only a single unique Type instance per runtime type may save a substantial amount of memory for
    other things. The drawback is that non-primitive types must be stored in a map of some kind to be retrieved
    later, which creates overhead for scripts which only use the primitive types. Additionally, these maps must
    be synchronized so that scripts running on different threads don't concurrently modify the maps.
    /*/
    
    /** A {@linkplain Type} object representing a primitive. */
    public static final Type VOID  = Type.VOID,
                             BOOL  = Type.BOOL,
                             INT   = Type.INT,
                             FLOAT = Type.FLOAT,
                             STR   = Type.STR;
    
    /** A synchronized map containing struct types. */
    private static final Map<String,Type> STRUCT = Collections.synchronizedMap(new HashMap<>());
    /** @return A {@linkplain Type} representing the struct with the specified name. */
    public static Type structType(final String structName)
    {
        Type t = STRUCT.get(structName);
        if(t == null) STRUCT.put(structName,t = new Type(BaseType.STRUCT,null,structName));
        return t;
    }
    
    /** A synchronized map containing list types. */
    private static final Map<Type,Type> LIST = Collections.synchronizedMap(new HashMap<>());
    /** @return A {@linkplain Type} representing the list with the specified sub-type. */
    public static Type listType(final Type subType)
    {
        Type t = LIST.get(subType);
        if(t == null) LIST.put(subType,t = new Type(BaseType.LIST,subType,null));
        return t;
    }
    
    /** A record which represents the identifying features of a function type. */
    private record FuncKey(Type subType,ConstableType...args)
    {
        @Override
        public boolean equals(final Object o)
        {
            return this == o ||
            (
                o instanceof final FuncKey fk &&
                subType.equals(fk.subType) &&
                Arrays.equals(args,fk.args)
            );
        }
        @Override public int hashCode() {return 31 * Objects.hash(subType) + Arrays.hashCode(args);}
    }
    /** A synchronized map containing function types. */
    private static final Map<FuncKey,Type> FUNC = Collections.synchronizedMap(new HashMap<>());
    /** @return A {@linkplain Type} representing the function with the specified return type and arguments. */
    public static Type funcType(final Type retType,final ConstableType...args)
    {
        final FuncKey fk = new FuncKey(retType,args);
        Type t = FUNC.get(fk);
        if(t == null) FUNC.put(fk,t = new Type(BaseType.FUNC,retType,null,args));
        return t;
    }
    
    /** A {@linkplain ConstableType} object representing a primitive. */
    public static final ConstableType MUTABLE_BOOL  = ConstableType.BOOL,
                                      MUTABLE_INT   = ConstableType.INT,
                                      MUTABLE_FLOAT = ConstableType.FLOAT,
                                      MUTABLE_STR   = ConstableType.STR,
                                  
                                      CONST_VOID    = ConstableType.VOID,
                                      CONST_BOOL    = ConstableType.CONST_BOOL,
                                      CONST_INT     = ConstableType.CONST_INT,
                                      CONST_FLOAT   = ConstableType.CONST_FLOAT,
                                      CONST_STR     = ConstableType.CONST_STR;
    
    /** A synchronized map containing all non-primitive non-const and const instances, respectively. */
    private static final Map<Type,ConstableType[]> STORAGE = Collections.synchronizedMap(new HashMap<>());
    
    /**
     * @return A {@linkplain ConstableType} representing the specified {@linkplain Type} and const-ness.
     *
     * @throws NullPointerException if {@code t} is {@code null}.
     */
    public static ConstableType constableType(final Type t,final boolean isConst)
    {
        if(t == null) throw new NullPointerException();
        return switch(t.base)
        {
            case VOID  -> CONST_VOID;
            case BOOL  -> isConst? CONST_BOOL  : MUTABLE_BOOL;
            case INT   -> isConst? CONST_INT   : MUTABLE_INT;
            case FLOAT -> isConst? CONST_FLOAT : MUTABLE_FLOAT;
            case STR   -> isConst? CONST_STR   : MUTABLE_STR;
            default    ->
            {
                ConstableType[] pair = STORAGE.get(t);
                if(pair == null) STORAGE.put(t,pair = new ConstableType[] {new ConstableType(t,false),new ConstableType(t,true)});
                yield pair[isConst? 1:0];
            }
        };
    }
}