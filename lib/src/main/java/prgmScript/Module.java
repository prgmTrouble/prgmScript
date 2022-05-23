package prgmScript;

import java.util.HashMap;
import java.util.Map;

/** A record which represents the global scope of a library or script. */
@SuppressWarnings("ClassCanBeRecord")
public final class Module
{
    final CompilerScopeEntry compileTime;
    final RuntimeScopeEntry runTime;
    
    Module(CompilerScopeEntry compileTime,RuntimeScopeEntry runTime)
    {
        this.compileTime = compileTime;
        this.runTime = runTime;
    }
    
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