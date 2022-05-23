package prgmScript;

import prgmScript.exception.ScriptException;
import prgmScript.util.ErrReporter;

import java.util.ArrayList;
import java.util.List;

/** A factory which constructs {@linkplain Block}s. */
final class BlockBuilder
{
    /** The module that this block is located in. */
    private final String module;
    /** The list of instructions that compose this block. */
    private final List<Instruction> ilist = new ArrayList<>();
    /** A string representation of the above instructions for debugging. */
    private final List<String> dbgInstr = new ArrayList<>();
    /** The return type of the current block. */
    Type ret = null;
    /** Branch indices */
    int br0 = -1,br1 = -1;
    /** The debug name of this block. */
    final String name;
    /** A dummy return type for ternary conditional instructions. */
    Type dummyT = null;
    
    BlockBuilder(final String module,final String name) {this.module = module; this.name = name;}
    
    /** Adds an instruction. */
    void instruction(final Instruction instruction,final String name)
    {
        //TODO if statement needed? if any instructions return, they should have a type
        if(instruction.isRet) ret = instruction.type;
        ilist.add(instruction);
        dbgInstr.add(name);
    }
    /** Adds a branch. */
    void branch(final boolean which,final int destination)
    { //TODO ensure that last instruction yields trivially convertible to bool
        if(which) br0 = destination;
        else br1 = destination;
    }
    /** Adds a branch (equivalent to {@code branch(true,destination)}). */
    void branch(final int destination) {branch(true,destination);}
    /** Ensures that this block returns with the specified return type. */
    void ensureReturn(final ErrReporter reporter,final Type retType)
    {
        final int line = ilist.isEmpty()? 0:ilist.get(ilist.size()-1).line;
        if(ret == null)
        {
            if(retType.equals(Type.VOID))
            {
                // Add a dummy void return statement if there isn't one already.
                ilist.add(new Instruction(line,ret = Type.VOID) {@Override Object exec(final RuntimeScope s) {return Script.RET_VOID;}});
                return;
            }
            reporter.report(line,"Missing return statement");
        }
        else if(!ret.equals(retType)) reporter.report(line,"Expected return type "+retType+", got "+ret);
    }
    
    /**
     * Dumps all debugging information
     *
     * @param bbList A list of other {@linkplain BlockBuilder}s which map branch indices to blocks.
     * @param sb The {@linkplain StringBuilder} where all debug information will be sent.
     */
    void dump(final List<BlockBuilder> bbList,final StringBuilder sb)
    {
        sb.append(name).append(':');
        for(final String s : dbgInstr)
            sb.append("\n\t\t\t").append(s);
        sb.append("\n\t\t\t")
          .append(ret != null? ret : '-').append(",\t")
          .append(br0 != -1? bbList.get(br0).name : '-').append(",\t")
          .append(br1 != -1? bbList.get(br1).name : '-');
    }
    
    /**
     * @param blocks An array which maps branch indices to blocks.
     *
     * @return The compiled block.
     *
     * @throws ScriptException if the false branch is set without the true branch,
     *                         the last instruction for a conditional jump is not
     *                         convertible to a bool, or there are no returns or
     *                         branches exiting the block.
     */
    Block build(final Block[] blocks) throws ScriptException
    {
        final Instruction[] instr = ilist.toArray(Instruction[]::new);
        if(br1 >= 0)
        {
            if(br0 < 0)
                throw new ScriptException(instr[instr.length-1].line,module,
                                          "Conditional jump requires two destinations");
            // lastT is the correct base type of the jump condition because the terminator
            // should always be the last instruction.
            final Type lastT = instr.length == 0? dummyT : instr[instr.length-1].type;
            if(!Script.triviallyConvertible(Type.BOOL,lastT))
                throw new ScriptException(instr[instr.length-1].line,module,
                                          "Jump condition not convertible to bool type");
            final BaseType lastTT = lastT.base;
            //TODO ret should be null here?
            return instr.length == 0
                ? new Block(ret)
                  {
                      @Override
                      Object exec(final RuntimeScope s)
                      {
                          //*debug*/ System.out.println(name);
                          return blocks[Script.conditional(s.popAccumulator(),lastTT)? br0 : br1];
                      }
                  }
                : new Block(ret)
                  {
                      @Override
                      Object exec(final RuntimeScope s)
                      {
                          //*debug*/ System.out.println(name);
                          for(final Instruction i : instr)
                          {
                              final Object o = i.exec(s);
                              if(o != null) return o; //TODO if ret is null, this check isn't needed
                          }
                          return blocks[Script.conditional(s.popAccumulator(),lastTT)? br0 : br1];
                      }
                  };
        }
        if(br0 < 0 && ret == null)
            throw new ScriptException(instr[instr.length-1].line,module,
                                      "Dead end");
        return new Block(ret)
        {
            @Override
            Object exec(final RuntimeScope s)
            {
                //*debug*/ System.out.println(name);
                for(final Instruction i : instr)
                {
                    final Object o = i.exec(s);
                    if(o != null) return o;
                }
                return blocks[br0]; // TODO If ret is not null, br0 should not exist
            }
        };
    }
}