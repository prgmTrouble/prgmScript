package experimental;

final class Instruction
{
    final int line;
    final InstructionType type;
    Object data;
    
    Instruction(final int line,final InstructionType type,final Object data)
    {
        this.line = line;
        this.type = type;
        this.data = data;
    }
    
    @Override public String toString() {return type.name()+(data != null? " "+data:"");}
}