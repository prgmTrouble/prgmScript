package experimental;

/**
 * A simple type token for categorizing more complex types.
 */
public enum BaseType
{
    /** A void type. This should only appear in function return types. */
    VOID,
    
    /** A boolean type. */
    BOOL,
    
    /** A 64-bit integral type. */
    INT,
    
    /** A 64-bit floating-point type. */
    FLOAT,
    
    /** A string of characters. */
    STR,
    
    /** A mutable list of characters. */
    LIST,
    
    /** A data structure composed of named values. */
    STRUCT,
    
    /** A function type. */
    FUNC,
    
    /** A templated type. */
    TEMPLATE;
    
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
}