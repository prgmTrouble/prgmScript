package prgmScript;

import java.util.Map;

/** An extension of {@linkplain Entry} for use during a script's runtime. */
final class RuntimeScopeEntry extends Entry<Value>
{
    RuntimeScopeEntry(final Map<String,Value> fields,final Map<String,Map<String,ConstableType>> structs) {super(fields,structs);}
    RuntimeScopeEntry() {super();}
}