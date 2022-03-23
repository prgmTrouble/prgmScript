package prgmScript.ast;

import prgmScript.ScopeEntry;
import prgmScript.Type;
import prgmScript.util.Stack;

import java.util.HashMap;
import java.util.Map;

import static prgmScript.Primitives.*;

public class Scope<V>
{
    private static final Map<String,Type> STRING_FIELDS = Map.of
    (
        "length"   ,new Type(true,BaseType.FUNC,null,INT.type),
        "substring",new Type(true,BaseType.FUNC,null,STR.type,INT.type,INT.type)
    );
    private static Map<String,Type> makeListFields(final Type list)
    {
        return Map.ofEntries
        (
            Map.entry("length"   ,new Type(true,BaseType.FUNC,null,INT.type)),
            Map.entry("popFront" ,new Type(true,BaseType.FUNC,null,list.complex())),
            Map.entry("popBack"  ,new Type(true,BaseType.FUNC,null,list.complex())),
            Map.entry("pushFront",new Type(true,BaseType.FUNC,null,list,list.complex())),
            Map.entry("pushBack" ,new Type(true,BaseType.FUNC,null,list,list.complex())),
            Map.entry("front"    ,new Type(true,BaseType.FUNC,null,list.complex())),
            Map.entry("back"     ,new Type(true,BaseType.FUNC,null,list.complex())),
            Map.entry("insert"   ,new Type(true,BaseType.FUNC,null,list,INT.type,list.complex())),
            Map.entry("remove"   ,new Type(true,BaseType.FUNC,null,list.complex(),INT.type)),
            Map.entry("copy"     ,new Type(true,BaseType.FUNC,null,list)),
            Map.entry("swap"     ,new Type(true,BaseType.FUNC,null,VOID.type,list))
        );
    }
    
    private final Stack<ScopeEntry<V>> entries;
    public Scope() {entries = new Stack<ScopeEntry<V>>(ScopeEntry[]::new); pushScope();}
    public Scope(final Scope<V> scope) {entries = new Stack<>(scope.entries); pushScope();}
    
    public void pushScope() {entries.push(new ScopeEntry<>(new HashMap<>(),new HashMap<>()));}
    public void pushToScope(final ScopeEntry<V> entry)
    {
        entries.top().fields().putAll(entry.fields());
        entries.top().structs().putAll(entry.structs());
    }
    public ScopeEntry<V> popScope() {return entries.pop();}
    
    public V getField(final String name)
    {
        for(int i = entries.pos();i != 0;)
        {
            final V v = entries.data()[--i].fields().get(name);
            if(v != null) return v;
        }
        return null;
    }
    public Map<String,Type> getStruct(final String name)
    {
        for(int i = entries.pos();i != 0;)
        {
            final Map<String,Type> s = entries.data()[--i].structs().get(name);
            if(s != null) return s;
        }
        return null;
    }
    public Map<String,Type> getMembers(final Type type)
    {
        return switch(type.simple())
        {
            case STRUCT -> getStruct(type.customName());
            case LIST   -> makeListFields(type);
            case STR    -> STRING_FIELDS;
            default     -> null;
        };
    }
    
    public void putField(final String name,final V value) {entries.top().fields().put(name,value);}
    public void putStruct(final String name,final Map<String,Type> struct) {entries.top().structs().put(name,struct);}
}