package experimental;

import java.util.ArrayList;
import java.util.List;

final class BlockBuilder
{
    final List<Instruction> instructions = new ArrayList<>();
    final String module,name;
    int b0 = -1,b1 = -1;
    
    BlockBuilder(final String module,final String name) {this.module = module; this.name = name;}
    
    void instruction(final int line,final InstructionType type,final Object data) {instructions.add(new Instruction(line,type,data));}
    
    void dump(final List<BlockBuilder> bbList,final StringBuilder sb)
    {
        sb.append(name).append(':');
        for(final Instruction s : instructions)
            sb.append("\n\t\t\t").append(s);
        sb.append("\n\t\t\t")
          .append(b0 != -1? bbList.get(b0).name : '-').append(",\t")
          .append(b1 != -1? bbList.get(b1).name : '-');
    }
}