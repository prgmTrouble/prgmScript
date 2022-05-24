package prgmScript;

import prgmScript.util.ContainerUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A record which represents the global scope of a library or script. */
@SuppressWarnings("ClassCanBeRecord")
public final class Module
{
    final CompilerScopeEntry compileTime;
    final RuntimeScopeEntry runTime;
    
    Module(final CompilerScopeEntry compileTime,final RuntimeScopeEntry runTime)
    {
        this.compileTime = compileTime;
        this.runTime = runTime;
    }
    
    private static Value deepCopy(final Value v)
    {
        return new Value(v.type,switch(v.type.type.base)
        {
            case LIST ->
            {
                final List<Value> l1 = Script.listData(v.value),
                                  l2 = new ArrayList<>(l1.size());
                for(final Value i : l1) l2.add(deepCopy(i));
                yield Script.listStruct(v.type.type.subType,ContainerUtil.makeImmutable(l2));
            }
            case STRUCT ->
            {
                @SuppressWarnings("unchecked")
                final Map<String,Value> s1 = (Map<String,Value>)v.value,
                                        s2 = new HashMap<>(s1.size());
                for(final Map.Entry<String,Value> e : s1.entrySet())
                    s2.put(e.getKey(),deepCopy(e.getValue()));
                yield ContainerUtil.makeImmutable(s2);
            }
            case STR ->
            {
                @SuppressWarnings("unchecked")
                final Map<String,Value> m = (Map<String,Value>)v.value;
                yield Script.strStruct((String)m.get(" ").value);
            }
            default -> v.value;
        });
    }
    /** @return A copy of the value with the specified name. */
    public Value getValue(final String name) {return deepCopy(runTime.fields.get(name));}
    
    static final Map<String,Module> REGISTRY = new HashMap<>();
    static
    {
        // Load all libraries.
        for(final String cls : new String[] {"prgmMath","prgmOutput","prgmRandom"})
            try {Class.forName("prgmScript.lib."+cls);}
            catch(final ClassNotFoundException e) {e.printStackTrace();}
    }
    /**
     * If no module with the specified name exists in the registry, then this module will be made
     * available to all scripts via the {@code import} statement and this function will return
     * {@code true}.
     *
     * @param name Name given to this module.
     *
     * @return {@code false} iff a module with the specified name already exists.
     */
    public boolean register(final String name) {synchronized(REGISTRY) {return REGISTRY.putIfAbsent(name,this) == null;}}
}