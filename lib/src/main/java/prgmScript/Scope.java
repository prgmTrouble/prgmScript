package prgmScript;

import prgmScript.util.Stack;

import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * A collection of symbols grouped by their position in the code. Symbols in higher scopes can be shadowed by
 * symbols in lower scopes.
 *
 * @see CompilerScope
 * @see RuntimeScope
 * @see Entry
 */
abstract class Scope<V,E extends Entry<V>>
{
    final Stack<E> entries;
    final Supplier<E> entryCreator;
    
    Scope(final IntFunction<E[]> entryArrayGen,final Supplier<E> entryCreator)
    {
        entries = new Stack<>(entryArrayGen);
        this.entryCreator = entryCreator;
        pushScope();
    }
    Scope(final Scope<V,E> other) {entries = new Stack<>(other.entries); entryCreator = other.entryCreator;}
    
    void pushScope() {entries.push(entryCreator.get());}
    /**
     * Pushes the specified module to the current scope. Any fields or structs from the input which
     * conflict with the current scope will be ignored.
     */
    void pushToScope(final E entry)
    {
        final E e = entries.top();
        for(final Map.Entry<String,V> f : entry.fields.entrySet())
            e.fields.putIfAbsent(f.getKey(),f.getValue());
    }
    /** @return The scope entry removed from the top of the stack. */
    E popScope() {return entries.pop();}
    
    /** @return The field with the specified name, or {@code null} if no such field exists. */
    V getField(final String name)
    {
        for(int i = entries.pos();i != 0;)
        {
            final V v = entries.data()[--i].fields.get(name);
            if(v != null) return v;
        }
        return null;
    }
    /** Inserts a field with the specified name and value. */
    void putField(final String name,final V value) {entries.top().fields.put(name,value);}
}