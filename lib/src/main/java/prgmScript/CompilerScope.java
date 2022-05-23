package prgmScript;

import prgmScript.util.Stack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** An extension of {@linkplain Scope} specifically for use during compile time. */
final class CompilerScope extends Scope<ConstableType,CompilerScopeEntry>
{
    /** A stack containing the block indices of break and continue statements, respectively. */
    final Stack<List<Integer>[]> cflow = new Stack<List<Integer>[]>(List[][]::new);
    
    CompilerScope() {super(CompilerScopeEntry[]::new,CompilerScopeEntry::new);}
    CompilerScope(final CompilerScope other) {super(other);}
    
    /** Enters a loop. */
    @SuppressWarnings("unchecked") void enterLoop() {cflow.push(new List[] {new ArrayList<>(),new ArrayList<>()});}
    /**
     * Exits a loop.
     *
     * @return A list of block indices for break and continue statements, respectively.
     */
    List<Integer>[] exitLoop() {return cflow.pop();}
    
    /**
     * Pushes the specified module to the current scope. Any fields or structs from the input which
     * conflict with the current scope will be ignored.
     */
    void pushToScope(final String module,final CompilerScopeEntry entry)
    {
        pushToScope(entry);
        entries.top().modules.add(module);
    }
    /** @return A map of types representing the specified struct's fields. */
    Map<String,ConstableType> getStruct(final String name)
    {
        for(int i = entries.pos();i != 0;)
        {
            final Map<String,ConstableType> v = entries.data()[--i].structs.get(name);
            if(v != null) return v;
        }
        return null;
    }
    /** Puts a struct onto the current scope. */
    void putStruct(final String name,final Map<String,ConstableType> struct) {entries.top().structs.put(name,struct);}
}