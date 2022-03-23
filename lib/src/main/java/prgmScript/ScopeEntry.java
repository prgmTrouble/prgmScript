package prgmScript;

import java.util.Map;

public record ScopeEntry<V>(Map<String,V> fields,Map<String,Map<String,Type>> structs) {}