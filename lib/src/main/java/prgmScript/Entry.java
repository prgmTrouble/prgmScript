package prgmScript;

import java.util.HashMap;
import java.util.Map;

/**
 * A class representing a scope.
 *
 * @see Scope
 * @see CompilerScopeEntry
 * @see RuntimeScopeEntry
 */
abstract class Entry<T>
{
    final Map<String,T> fields;
    final Map<String,Map<String,ConstableType>> structs;
    
    Entry(final Map<String,T> fields,final Map<String,Map<String,ConstableType>> structs) {this.fields = fields; this.structs = structs;}
    Entry() {this(new HashMap<>(),new HashMap<>());}
}