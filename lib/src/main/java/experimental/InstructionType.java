package experimental;

enum InstructionType
{
    // Stack
    PUSH,POP,SP,DECL,
    /** Loads from the pointer at {@code SP-data}. */
    LOAD,
    /** Stores to the pointer at {@code SP-data}. */
    STORE,
    RET,RET_VOID,
    
    // Scope
    SC_ENTER,SC_EXIT,
    
    // Type Conversion
    CONVERT,
    BOOL_TO_NUM,
    
    // String
    STRLEN,SUBSTR,STRCAT,
    
    // List
    LISTIDX,
    FOR_ITR_START,
    FOR_ITR_NEXT,
    FOR_ITR_TEST,
    
    // Struct
    FIELD,
    
    // Function Call
    CALL,
    
    // Math
    ADD,SUB,MUL,DIV,MOD,NEG,
    LSH,RSH,LRSH,
    AND,OR,XOR,NOT,
    EQ,NEQ,GT,LT,GEQ,LEQ,
    
    // Type Comparsion
    TYPE_COMPARISON,
    
    // Throw
    THROW,THROW_VOID,
    
    // Import
    IMPORT,
    
    // Template Code Generation
    GENERATE_TEMPLATE
}