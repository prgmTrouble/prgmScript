package experimental;

import java.util.*;

/** A record which represents a runtime datatype. */
@SuppressWarnings("ClassCanBeRecord")
public final class Type
{
    static final record TemplateEntry(String name,TemplateType...args) {}
    public final BaseType base;
    public final TemplateType subType;
    public final String structName;
    final TemplateEntry[] template;
    final ConstableTemplateType[] args;
    
    Type(final BaseType base,final TemplateType subType,final String structName,
         final TemplateEntry[] template,final ConstableTemplateType...args)
    {
        this.base = base;
        this.subType = subType;
        this.structName = structName;
        this.template = template;
        this.args = args;
    }
    
    @Override
    public String toString()
    {
        return switch(base)
        {
            case LIST -> subType + "[]";
            case STRUCT -> structName;
            case FUNC ->
            {
                final StringJoiner sj = new StringJoiner(",","func<"+subType+">(",")");
                for(final ConstableTemplateType ct : args) sj.add(ct.toString());
                yield sj.toString();
            }
            default -> base.name().toLowerCase();
        };
    }
    @Override public int hashCode() {return 31 * Objects.hash(base,subType,structName) + Arrays.hashCode(args);}
    @Override
    public boolean equals(final Object o)
    {
        return this == o ||
        (
            o instanceof final Type ct &&
            base == ct.base &&
            Objects.equals(structName,ct.structName) &&
            Objects.equals(subType,ct.subType) &&
            Objects.deepEquals(args,ct.args)
        );
    }
    
    /** A Type object representing a primitive. */
    static final Type VOID  = new Type(BaseType.VOID ,null,null,null),
                      BOOL  = new Type(BaseType.BOOL ,null,null,null),
                      INT   = new Type(BaseType.INT  ,null,null,null),
                      FLOAT = new Type(BaseType.FLOAT,null,null,null),
                      STR   = new Type(BaseType.STR  ,null,null,null);
    
    /** A synchronized map containing struct types. */
    private static final Map<String,Type> STRUCT = Collections.synchronizedMap(new HashMap<>());
    /** @return A {@linkplain Type} representing the struct with the specified name. */
    public static Type ofStruct(final String structName)
    {
        Type t = STRUCT.get(structName);
        if(t == null) STRUCT.put(structName,t = new Type(BaseType.STRUCT,null,structName,null));
        return t;
    }
    
    /** A synchronized map containing list types. */
    private static final Map<TemplateType,Type> LIST = Collections.synchronizedMap(new HashMap<>());
    /** @return A {@linkplain Type} representing the list with the specified sub-type. */
    public static Type ofList(final TemplateType subType)
    {
        Type t = LIST.get(subType);
        if(t == null) LIST.put(subType,t = new Type(BaseType.LIST,subType,null,null));
        return t;
    }
    
    /** A record which represents the identifying features of a function type. */
    private record FuncKey(TemplateType subType,ConstableTemplateType...args)
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
    public static Type ofFunc(final TemplateType retType,final TemplateEntry[] template,final ConstableTemplateType...args)
    {
        final FuncKey fk = new FuncKey(retType,args);
        Type t = FUNC.get(fk);
        if(t == null) FUNC.put(fk,t = new Type(BaseType.FUNC,retType,null,template,args));
        return t;
    }
}


















