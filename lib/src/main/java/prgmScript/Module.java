package prgmScript;

import prgmScript.ast.Value;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public record Module(ScopeEntry<Type> compileTime,ScopeEntry<Value> runTime)
{
    static final Map<String,Module> REGISTRY = Collections.synchronizedMap(new HashMap<>());
    /**
     * If no module with the specified name exists in the registry, then this module will be made
     * available to all scripts via the {@code import} statement and this function will return
     * {@code true}.
     *
     * @param name Name given to this module.
     *
     * @return {@code false} iff a module with the specified name already exists.
     */
    public boolean register(final String name) {return REGISTRY.putIfAbsent(name,this) == null;}
}