package prgmScript;

/** A class representing a single unit of computation. */
abstract class Instruction
{
    /** The line of source code which generated this instruction. */
    final int line;
    /** The type of object which is put on the accumulator stack or returned. */
    final Type type;
    /** {@code true} iff the returned type is wrapped in a {@linkplain Value}. */
    final boolean isValueType,
    /** {@code true} iff the instruction returns its effects instead of pushing them to the accumulator stack. */
                  isRet;
    
    Instruction(final int line,final Type type,final boolean isValueType,final boolean isRet)
    {
        this.line = line;
        this.type = type;
        this.isValueType = isValueType;
        this.isRet = isRet;
    }
    Instruction(final int line,final Type type,final boolean isValueType) {this(line,type,isValueType,false);}
    Instruction(final int line,final Type type) {this(line,type,false,false);}
    
    /** @return <code>null</code>, or a return value. */
    abstract Object exec(final RuntimeScope scope);
}