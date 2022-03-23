package prgmScript.ast;

import java.util.List;
import java.util.Map;

public enum BaseType
{
    VOID,
    BOOL,
    INT,
    FLOAT,
    STR,
    LIST,
    STRUCT,
    FUNC;
    
    @Override
    public String toString()
    {
        return switch(this)
        {
            case VOID   -> "void";
            case BOOL   -> "boolean";
            case INT    -> "integer";
            case FLOAT  -> "float";
            case STR    -> "string";
            case LIST   -> "list";
            case STRUCT -> "struct";
            case FUNC   -> "function";
        };
    }
    
    @SuppressWarnings("unchecked")
    public boolean conditional(final Object value)
    {
        return value != null && switch(this)
        {
            case BOOL   -> (Boolean)value;
            case INT    -> (Long)value != 0L;
            case FLOAT  -> (Double)value != 0D;
            case STR    -> ((String)value).isEmpty();
            case LIST   -> ((List<Literal>)value).isEmpty();
            case STRUCT ->
            {
                for(final Literal e : ((Map<String,Literal>)value).values())
                    if(!e.type.simple().conditional(e.value()))
                        yield false;
                yield true;
            }
            case FUNC   -> true;
            default     -> throw new AssertionError();
        };
    }
}