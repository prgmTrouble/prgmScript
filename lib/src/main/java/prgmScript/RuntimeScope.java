package prgmScript;

import prgmScript.util.Stack;

import java.util.Map;

/** An extension of {@linkplain Scope} specifically for use during a script's runtime. */
final class RuntimeScope extends Scope<Value,RuntimeScopeEntry>
{
    final Stack<Object> accumulator = new Stack<>(Object[]::new);
    
    RuntimeScope() {super(RuntimeScopeEntry[]::new,RuntimeScopeEntry::new);}
    RuntimeScope(final RuntimeScope other) {super(other);}
    
    @Override
    void pushToScope(final RuntimeScopeEntry entry)
    {
        super.pushToScope(entry);
        final RuntimeScopeEntry e = entries.top();
        for(final Map.Entry<String,Map<String,ConstableType>> s : entry.structs.entrySet())
            e.structs.putIfAbsent(s.getKey(),s.getValue());
    }
    void setFieldValue(final String name,final Object value)
    {
        for(int i = entries.pos();i != 0;)
        {
            final Map<String,Value> e = entries.data()[--i].fields;
            if(e.containsKey(name))
            {
                e.get(name).value = value;
                return;
            }
        }
    }
    
    void pushAccumulator(final Object obj) {accumulator.push(obj);}
    Object popAccumulator() {return accumulator.pop();}
}