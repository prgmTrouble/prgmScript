package prgmScript;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** An extension of {@linkplain Entry} for use during a script's compile time. */
final class CompilerScopeEntry extends Entry<ConstableType>
{
    final Set<String> modules;
    
    CompilerScopeEntry(final Map<String,ConstableType> fields,
                       final Map<String,Map<String,ConstableType>> structs,
                       final Set<String> modules)
    {
        super(fields,structs);
        this.modules = modules;
    }
    CompilerScopeEntry() {super(); modules = new HashSet<>();}
}