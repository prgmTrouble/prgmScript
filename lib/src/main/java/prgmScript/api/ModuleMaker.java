package prgmScript.api;

import prgmScript.Module;
import prgmScript.Primitives;
import prgmScript.ScopeEntry;
import prgmScript.Type;
import prgmScript.ast.Func;
import prgmScript.ast.Literal;
import prgmScript.ast.Scope;
import prgmScript.ast.Value;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static prgmScript.ast.BaseType.*;

public final class ModuleMaker
{
    private final Map<String,Type> ctFields = new HashMap<>();
    private final Map<String,Value> rtFields = new HashMap<>();
    private final Map<String,Map<String,Type>> structs = new HashMap<>();
    
    public ModuleMaker() {}
    
    public ModuleMaker declareStruct(final String structName,final Map<String,Type> fields)
    {
        structs.put(structName,fields);
        return this;
    }
    
    public static Type makePrimitiveType(final boolean isConst,final Primitives type)
    {
        return isConst? new Type(isConst,type.type.simple(),null,null) : type.type;
    }
    public static Type makeStructType(final boolean isConst,final String structName)
    {
        return new Type(isConst,STRUCT,structName,null);
    }
    public static Type makeListType(final boolean isConst,final Type of) {return new Type(isConst,LIST,null,of);}
    public static Type makeFunctionType(final boolean isConst,final Type retType,final Type...args)
    {
        return new Type(isConst,FUNC,null,retType,args);
    }
    
    private static Value mkVal(final Object v,final Type t)
    {
        return t.isConst()
            ? new Value(0,t)
              {
                  @Override protected void setValue(final Scope<Value> s,final Object v) {}
                  @Override protected Literal eval(final Scope<Value> s) {return literal(s2 -> v,s);}
              }
            : new Value(0,t)
              {
                  Object x = v;
                  @Override protected void setValue(final Scope<Value> s,final Object x) {this.x = x;}
                  @Override protected Literal eval(final Scope<Value> s) {return literal(s2 -> x,s);}
              };
    }
    public Value makePrimitiveValue(final boolean isConst,final Object value,final Primitives type)
    {
        return mkVal(value,makePrimitiveType(isConst,type));
    }
    public Value makeStructValue(final boolean isConst,final String structName,
                                 final Map<String,Object> fields)
    {
        final Map<String,Type> struct = structs.get(Objects.requireNonNull(structName));
        if(struct == null) throw new IllegalArgumentException("No struct with name '"+structName+"' exists");
        final Map<String,Literal> value = new HashMap<>(Objects.requireNonNull(fields).size());
        for(final Map.Entry<String,Object> e : fields.entrySet())
        {
            final Object v = e.getValue();
            value.put
            (
                e.getKey(),
                new Literal(0,struct.get(e.getKey())) {@Override public Object value() {return v;}}
            );
        }
        return mkVal(value,makeStructType(isConst,structName));
    }
    public Value makeListValue(final boolean isConst,final Type of,final List<Literal> list)
    {
        final Type t = makeListType(isConst,of);
        for(final Literal l : Objects.requireNonNull(list))
            if(!l.type.equals(t) || l.type.isConst() != t.isConst())
                throw new IllegalArgumentException("List of type "+t+" cannot have element of type "+l.type);
        return mkVal(list,t);
    }
    public Value makeFunctionValue(final boolean isConst,final Function<Literal[],Object> f,
                                   final Type retType,final Type...args)
    {
        final Type t = makeFunctionType(isConst,retType,args);
        return isConst
            ? new Value(0,t)
              {
                  @Override protected void setValue(final Scope<Value> s,final Object v) {}
                  @Override
                  protected Literal eval(final Scope<Value> s)
                  {
                      return new Func(0,t) {@Override public Object call(final Literal...a) {return f.apply(a);}};
                  }
              }
            : new Value(0,t)
              {
                  Function<Literal[],Object> f2 = f;
                  @Override @SuppressWarnings("unchecked")
                  protected void setValue(final Scope<Value> s,final Object v) {f2 = (Function<Literal[],Object>)v;}
                  @Override
                  protected Literal eval(final Scope<Value> s)
                  {
                      return new Func(0,t) {@Override public Object call(final Literal...a) {return f2.apply(a);}};
                  }
              };
    }
    
    public ModuleMaker addValue(final String name,final Value v)
    {
        Objects.requireNonNull(v);
        ctFields.put(name,v.type);
        rtFields.put(name,v);
        return this;
    }
    public ModuleMaker addPrimitiveValue(final boolean isConst,final String name,
                                         final Object value,final Primitives type)
    {
        return addValue(name,makePrimitiveValue(isConst,value,type));
    }
    public ModuleMaker addStructValue(final boolean isConst,final String structName,
                                      final Map<String,Object> fields,final String name)
    {
        return addValue(name,makeStructValue(isConst,structName,fields));
    }
    public ModuleMaker addListValue(final boolean isConst,final Type of,
                                    final List<Literal> list,final String name)
    {
        return addValue(name,makeListValue(isConst,of,list));
    }
    public ModuleMaker addFunction(final boolean isConst,final String name,
                                   final Function<Literal[],Object> f,
                                   final Type retType,final Type...args)
    {
        return addValue(name,makeFunctionValue(isConst,f,retType,args));
    }
    
    void buildNoReturn(final String name)
    {
        new Module
        (
            new ScopeEntry<>(ctFields,structs),
            new ScopeEntry<>(rtFields,structs)
        ).register(name);
    }
    public boolean build(final String name)
    {
        return new Module
        (
            new ScopeEntry<>(ctFields,structs),
            new ScopeEntry<>(rtFields,structs)
        ).register(name);
    }
}