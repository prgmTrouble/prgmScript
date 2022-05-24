package prgmScript;

/** An object representing a portion of un-interrupted code. */
abstract class Block
{
    final Type ret;
    Block(final Type returnType) {ret = returnType;}
    
    /** @return The next block to execute, or a return value. */
    abstract Object exec(final RuntimeScope scope);
}