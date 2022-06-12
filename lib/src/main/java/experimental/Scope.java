package experimental;

import experimental.util.Stack;

import java.util.*;

final class Scope
{
    static final record StructEntry(ConstableTemplateType type,int offset) {}
    static final class Entry
    {
        final Map<String,Map<String,StructEntry>> structs = new HashMap<>();
        final Map<String,ConstableTemplateType> fields = new HashMap<>();
        
        // typeSets != templates
        // 'typeSets' contains typesets named outside function definitions,
        // while 'templates' contains typesets named inside function definitions.
        
        final Map<String,Set<TemplateType>> typeSets = new HashMap<>();
        final Map<String,Set<TemplateType>> templates = new HashMap<>();
    }
    
    final Stack<Entry> entries;
    final Stack<List<Integer>[]> cflow;
    
    Scope()
    {
        entries = new Stack<>(Entry[]::new);
        cflow = new Stack<List<Integer>[]>(List[][]::new);
        pushScope();
    }
    Scope(final Scope other)
    {
        entries = new Stack<>(other.entries);
        cflow = new Stack<>(other.cflow);
    }
    
    void pushScope() {entries.push(new Entry());}
    Entry popScope() {return entries.pop();}
    
    @SuppressWarnings("unchecked") void enterLoop() {cflow.push(new List[] {new ArrayList<Integer>(),new ArrayList<Integer>()});}
    List<Integer>[] exitLoop() {return cflow.pop();}
    void putBreak(final int id) {cflow.top()[0].add(id);}
    void putContinue(final int id) {cflow.top()[1].add(id);}
    
    void putStruct(final String structName,final Map<String,StructEntry> struct)
    {
        entries.top().structs.put(structName,struct);
    }
    Map<String,StructEntry> getStruct(final String structName)
    {
        for(int i = entries.pos();i-- != 0;)
        {
            final Map<String,StructEntry> struct = entries.data()[i].structs.get(structName);
            if(struct != null) return struct;
        }
        return null;
    }
    void putField(final String name,final ConstableTemplateType field)
    {
        entries.top().fields.put(name,field);
    }
    ConstableTemplateType getField(final String fieldName)
    {
        for(int i = entries.pos();i-- != 0;)
        {
            final ConstableTemplateType field = entries.data()[i].fields.get(fieldName);
            if(field != null) return field;
        }
        return null;
    }
    
    boolean putTypeSet(final String name,final Set<TemplateType> typeSet)
    {
        return entries.top().typeSets.put(name,typeSet) == null;
    }
    Set<TemplateType> getTypeSet(final String setName)
    {
        for(int i = entries.pos();i-- != 0;)
        {
            final Set<TemplateType> typeSet = entries.data()[i].typeSets.get(setName);
            if(typeSet != null) return typeSet;
        }
        return null;
    }
    
    boolean putTemplate(final String name,final Set<TemplateType> typeSet)
    {
        return entries.top().templates.put(name,typeSet) == null;
    }
    Set<TemplateType> getTemplate(final String templateName)
    {
        for(int i = entries.pos();i-- != 0;)
        {
            final Set<TemplateType> out = entries.data()[i].templates.get(templateName);
            if(out != null) return out;
        }
        return null;
    }
    boolean templateDefined(final String templateName)
    {
        for(int i = entries.pos();i-- != 0;)
            if(entries.data()[i].templates.containsKey(templateName))
                return true;
        return false;
    }
}