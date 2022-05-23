package prgmScript;

import java.util.Arrays;
import java.util.Objects;
import java.util.StringJoiner;

/** A record which represents a runtime datatype. */
@SuppressWarnings("ClassCanBeRecord")
public final class Type
{
    public final BaseType base;
    public final Type subType;
    public final String structName;
    public final ConstableType[] args;
    
    Type(final BaseType base,final Type subType,final String structName,final ConstableType...args)
    {
        this.base = base;
        this.subType = subType;
        this.structName = structName;
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
                for(final ConstableType ct : args) sj.add(ct.toString());
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
    static final Type VOID  = new Type(BaseType.VOID ,null,null),
                      BOOL  = new Type(BaseType.BOOL ,null,null),
                      INT   = new Type(BaseType.INT  ,null,null),
                      FLOAT = new Type(BaseType.FLOAT,null,null),
                      STR   = new Type(BaseType.STR  ,null,null);
}